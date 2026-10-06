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
const emailCooldownMs = Number(process.env.E2E_EMAIL_COOLDOWN_MS ?? "11000");
const stepDelayMs = Number(process.env.E2E_STEP_DELAY_MS ?? "3000");

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

async function pauseWithCountdown(page: Page, reason: string, durationMs: number): Promise<void> {
  await page.evaluate(({ reason: message, seconds }) => {
    const existing = document.getElementById("e2e-countdown");
    existing?.remove();
    const banner = document.createElement("div");
    banner.id = "e2e-countdown";
    banner.style.cssText = [
      "position:fixed", "top:16px", "left:50%", "transform:translateX(-50%)",
      "z-index:2147483647", "padding:14px 20px", "border-radius:8px",
      "background:#172554", "color:#fff", "font:16px sans-serif",
      "box-shadow:0 4px 16px #0006", "text-align:center",
    ].join(";");
    banner.innerHTML = `<strong>Playwright is pausing</strong><br>${message}<br><span id="e2e-countdown-seconds">${seconds}</span> seconds remaining`;
    document.body.appendChild(banner);
  }, { reason, seconds: Math.ceil(durationMs / 1000) });

  const end = Date.now() + durationMs;
  while (Date.now() < end) {
    const seconds = Math.max(0, Math.ceil((end - Date.now()) / 1000));
    await page.evaluate(value => {
      const element = document.getElementById("e2e-countdown-seconds");
      if (element) element.textContent = String(value);
    }, seconds);
    await new Promise(resolve => setTimeout(resolve, Math.min(1_000, end - Date.now())));
  }
  await page.evaluate(() => document.getElementById("e2e-countdown")?.remove());
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
    await pauseWithCountdown(page, "Showing the signup result before checking the activation email.", stepDelayMs);

    const message = await waitForActivationLink(email, startedAt, mailbox!);
    expect(message.activationUrl).toBeTruthy();
    const response = await page.goto(message.activationUrl!);
    expect(response?.ok(), `Activation request failed for ${email}`).toBe(true);
    await expect(page).toHaveURL(/\/accounts\/active\/password-secret\//);
    await pauseWithCountdown(page, "The account is activated; pausing before the next self-service action.", stepDelayMs);
  });

  await test.step("Exercise username and password email self-service", async () => {
    await pauseWithCountdown(page, "Waiting for the email anti-spam cooldown before requesting the username email.", emailCooldownMs);
    const usernameRequestAt = new Date();
    await page.goto(new URL("/username", issuer).toString());
    await page.locator("#emailAddress").fill(email);
    await page.locator("#emailUsername").click();
    await expect(page.getByText(/username has been sent/i)).toBeVisible();
    await pauseWithCountdown(page, "Showing the username-email result before requesting another email.", stepDelayMs);
    await waitForNewestMessage(email, usernameRequestAt, mailbox!);

    await pauseWithCountdown(page, "Waiting for the email anti-spam cooldown before requesting the password-reset email.", emailCooldownMs);
    const passwordRequestAt = new Date();
    await page.goto(new URL("/password", issuer).toString());
    await page.locator("#email").fill(email);
    await page.locator("#changePassword").click();
    await expect(page.getByText(/check your email for changing your password/i)).toBeVisible();
    await pauseWithCountdown(page, "Showing the password-reset request result before signing in.", stepDelayMs);
    await waitForNewestMessage(email, passwordRequestAt, mailbox!);
  });

  await test.step("Sign in and verify the user's profile", async () => {
    await signInToAdmin(page, issuer, admin, username, signupPassword);
    await page.getByRole("link", { name: "Your Profile", exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/user\/profile/);
    await expect(page.locator("#authenticationId")).toHaveValue(username);
    await pauseWithCountdown(page, "Showing the signed-in user's profile before deletion.", stepDelayMs);
  });

  await test.step("Exercise the admin clients and passkeys navigation", async () => {
    await page.getByRole("link", { name: "Clients", exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/clients(?:\?.*)?$/);
    await pauseWithCountdown(page, "Showing the admin clients list.", stepDelayMs);

    const firstClient = page.locator("ol.list-group li a").first();
    if (await firstClient.count()) {
      await firstClient.click();
      await expect(page).toHaveURL(/\/admin\/clients\/[0-9a-f-]+(?:\?.*)?$/);
      await expect(page.getByRole("heading", { name: "Client Update Page" })).toBeVisible();
      await pauseWithCountdown(page, "Showing the first existing client.", stepDelayMs);
    }

    await page.getByRole("link", { name: "Your Profile", exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/user\/profile/);
    await page.getByRole("link", { name: "Passkeys", exact: true }).click();
    await expect(page).toHaveURL(url =>
      url.origin === issuer.origin
      && url.pathname === "/issuer/mfa/passkeys"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString(),
    );
    await expect(page.getByRole("heading", { name: /Passkeys$/ })).toBeVisible();
    await pauseWithCountdown(page, "Showing the issuer passkeys page.", stepDelayMs);

    await page.getByRole("link", { name: "Profile", exact: true }).click();
    await expect(page).toHaveURL(url =>
      url.origin === issuer.origin
      && url.pathname === "/account/profile"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString(),
    );
    await pauseWithCountdown(page, "Showing the issuer account profile page.", stepDelayMs);

    await page.getByRole("link", { name: "Passkeys", exact: true }).click();
    await expect(page).toHaveURL(url =>
      url.origin === issuer.origin
      && url.pathname === "/mfa/passkeys"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString(),
    );
    await pauseWithCountdown(page, "Showing passkeys from the issuer account profile.", stepDelayMs);

    await page.getByRole("link", { name: "Back to admin" }).click();
    await expect(page).toHaveURL(/\/admin\/user\/profile/);
    await pauseWithCountdown(page, "Returned to the admin profile before deletion.", stepDelayMs);
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
    await pauseWithCountdown(page, "The profile was deleted and the session logged out.", stepDelayMs);
  });
});
