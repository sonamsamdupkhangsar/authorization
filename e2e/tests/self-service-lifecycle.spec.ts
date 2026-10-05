import { expect, Page, test } from "@playwright/test";
import {
  mailboxConfigFromEnv,
  waitForActivationLink,
  waitForNewestMessage,
} from "../support/activation-email";

const issuerUrl = process.env.E2E_ISSUER_URL;
const adminUrl = process.env.E2E_ADMIN_URL;
const signupInbox = process.env.E2E_SIGNUP_INBOX;
const configuredSignupEmail = process.env.E2E_SIGNUP_EMAIL;
const signupPassword = process.env.E2E_SIGNUP_PASSWORD ?? "OpenIssuer-Test-42";

test.skip(
  process.env.E2E_SELF_SERVICE_LIFECYCLE !== "true"
    || !issuerUrl
    || !adminUrl
    || (!signupInbox && !configuredSignupEmail)
    || !process.env.E2E_MAILBOX_PASSWORD,
  "Set E2E_SELF_SERVICE_LIFECYCLE=true, E2E_ISSUER_URL, E2E_ADMIN_URL, signup email, and E2E_MAILBOX_PASSWORD.",
);

function plusAddress(email: string, suffix: string): string {
  const separator = email.lastIndexOf("@");
  if (separator <= 0) {
    throw new Error("E2E_SIGNUP_INBOX must be a valid email address.");
  }
  return `${email.slice(0, separator)}+${suffix}${email.slice(separator)}`;
}

async function signInToAdmin(
  page: Page,
  issuer: URL,
  admin: URL,
  username: string,
  password: string,
): Promise<void> {
  await page.goto(new URL("/admin/dashboard", admin).toString());
  await expect(page).toHaveURL(new RegExp(`^${issuer.origin.replaceAll(".", "\\.")}/`));
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#submit").click();
  await expect(page).toHaveURL(new RegExp(`^${admin.origin.replaceAll(".", "\\.")}/admin/dashboard`));
  await expect(page.getByRole("heading", { name: "Manage tenant authorization." })).toBeVisible();
}

test("signup, self-service actions, and final profile deletion", async ({ page }) => {
  const issuer = new URL(issuerUrl!);
  const admin = new URL(adminUrl!);
  const uniqueSuffix = Date.now().toString().slice(-10);
  const username = process.env.E2E_SIGNUP_USERNAME ?? `e2e-lifecycle-${uniqueSuffix}`;
  const email = signupInbox
    ? plusAddress(signupInbox, uniqueSuffix)
    : configuredSignupEmail!;
  const mailbox = mailboxConfigFromEnv(signupInbox ?? email);
  const startedAt = new Date();
  test.setTimeout((mailbox?.timeoutMs ?? 120_000) * 3 + 90_000);

  await test.step("Create and activate a unique user", async () => {
    await page.goto(new URL("/signup", issuer).toString());
    await page.locator("#firstName").fill("E2E");
    await page.locator("#lastName").fill("Lifecycle User");
    await page.locator("#organization").fill(`E2E Lifecycle ${uniqueSuffix}`);
    await page.locator("#email").fill(email);
    await page.locator("#authenticationId").fill(username);
    await page.locator("#password").fill(signupPassword);
    await page.locator("#submitButton").click();
    await expect(page.getByText(/your signup was successful/i)).toBeVisible();
    await expect(page.getByText(/check your email/i)).toBeVisible();

    const message = await waitForActivationLink(email, startedAt, mailbox!);
    expect(message.activationUrl).toBeTruthy();
    const response = await page.goto(message.activationUrl!);
    expect(response?.ok(), `Activation request failed for ${email}`).toBe(true);
    await expect(page).toHaveURL(/\/accounts\/active\/password-secret\//);
  });

  await test.step("Exercise username and password email self-service", async () => {
    const usernameRequestAt = new Date();
    await page.goto(new URL("/username", issuer).toString());
    await page.locator("#emailAddress").fill(email);
    await page.locator("#emailUsername").click();
    await expect(page.getByText(/username has been sent/i)).toBeVisible();
    await waitForNewestMessage(email, usernameRequestAt, mailbox!);

    const passwordRequestAt = new Date();
    await page.goto(new URL("/password", issuer).toString());
    await page.locator("#email").fill(email);
    await page.locator("#changePassword").click();
    await expect(page.getByText(/check your email for changing your password/i)).toBeVisible();
    await waitForNewestMessage(email, passwordRequestAt, mailbox!);
  });

  await test.step("Sign in and verify the user's profile", async () => {
    await signInToAdmin(page, issuer, admin, username, signupPassword);
    await page.getByRole("link", { name: "Your Profile", exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/user\/profile/);
    await expect(page.locator("#authenticationId")).toHaveValue(username);
  });

  await test.step("Delete the signed-in user's profile last", async () => {
    page.on("dialog", async dialog => dialog.accept());
    const deletion = page.waitForResponse(response =>
      response.request().method() === "DELETE"
      && response.url().includes("/admin/users/delete"),
    );
    const loggedOut = page.waitForURL(url =>
      url.origin !== admin.origin || !url.pathname.startsWith("/admin/"),
    );
    await page.goto(new URL("/admin/users/delete", admin).toString());
    await page.getByRole("button", { name: "Delete my account" }).click();
    expect((await deletion).ok(), "Delete-my-account request failed").toBe(true);
    await loggedOut;
  });
});
