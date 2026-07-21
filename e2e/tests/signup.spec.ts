import { expect, Page, test } from "@playwright/test";
import {
  ActivationMessage,
  MailboxConfig,
  mailboxConfigFromEnv,
  waitForActivationLink,
  waitForNewestMessage,
} from "../support/activation-email";

const signupInbox = process.env.E2E_SIGNUP_INBOX;
const configuredSignupEmail = process.env.E2E_SIGNUP_EMAIL;
const signupPassword = process.env.E2E_SIGNUP_PASSWORD ?? "OpenIssuer-Test-42";

test.skip(
  !signupInbox && !configuredSignupEmail,
  "Set E2E_SIGNUP_INBOX to a plus-addressable inbox or E2E_SIGNUP_EMAIL to an exact address.",
);

function plusAddress(email: string, suffix: string): string {
  const separator = email.lastIndexOf("@");
  if (separator <= 0) {
    throw new Error("E2E_SIGNUP_INBOX must be a valid email address.");
  }

  return `${email.slice(0, separator)}+${suffix}${email.slice(separator)}`;
}

async function activateThroughVisibleWebmail(
  page: Page,
  mailbox: MailboxConfig,
  message: ActivationMessage,
): Promise<Page> {
  const webmailUrl = process.env.E2E_WEBMAIL_URL ?? "https://box.openissuer.com/mail/";
  await page.goto(webmailUrl);
  await page.locator('input[name="_user"]').fill(mailbox.username);
  await page.locator('input[name="_pass"]').fill(mailbox.password);
  await page.locator('button[type="submit"], input[type="submit"]').click();
  await expect(page.locator("#messagelist, #mailboxlist").first()).toBeVisible();

  const messageUrl = new URL(webmailUrl);
  messageUrl.searchParams.set("_task", "mail");
  messageUrl.searchParams.set("_action", "show");
  messageUrl.searchParams.set("_mbox", "INBOX");
  messageUrl.searchParams.set("_uid", String(message.uid));
  await page.goto(messageUrl.toString());

  const activationLink = page
    .frameLocator("#messagecontframe")
    .locator(`a[href*="/accounts/active/password-secret/"]`)
    .first();
  await expect(activationLink).toBeVisible();

  const pagesBeforeClick = page.context().pages().length;
  await activationLink.click();
  await page.waitForTimeout(1_000);
  const pages = page.context().pages();
  const activationPage = pages.length > pagesBeforeClick ? pages.at(-1)! : page;

  // Some webmail security settings rewrite external links. Use the URL extracted from
  // the same message if the click did not reach the activation endpoint.
  if (!activationPage.url().includes("/accounts/active/password-secret/") && message.activationUrl) {
    await activationPage.goto(message.activationUrl);
  }
  return activationPage;
}

test("signup submits a new user and records the interaction", async ({ page }, testInfo) => {
  const uniqueSuffix = Date.now().toString().slice(-10);
  const username = process.env.E2E_SIGNUP_USERNAME ?? `e2e-${uniqueSuffix}`;
  const signupEmail = signupInbox
    ? plusAddress(signupInbox, uniqueSuffix)
    : configuredSignupEmail!;
  const signupStartedAt = new Date();
  const mailbox = mailboxConfigFromEnv(signupInbox ?? signupEmail);
  test.setTimeout(mailbox ? mailbox.timeoutMs + 60_000 : 30_000);

  await test.step("Open the signup page", async () => {
    await page.goto("/signup");
    await expect(page.locator("#submitButton")).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("signup-form.png"),
      fullPage: true,
    });
  });

  await test.step("Enter the new user", async () => {
    await page.locator("#firstName").fill("E2E");
    await page.locator("#lastName").fill("Signup User");
    await page.locator("#organization").fill(`E2E Organization ${uniqueSuffix}`);
    await page.locator("#email").fill(signupEmail);
    await page.locator("#authenticationId").fill(username);
    await page.locator("#password").fill(signupPassword);
  });

  await test.step("Submit signup", async () => {
    await page.locator("#submitButton").click();
    await expect(page.getByText(/your signup was successful/i)).toBeVisible();
    await expect(page.getByText(/check your email/i)).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("signup-submitted.png"),
      fullPage: true,
    });
  });

  await testInfo.attach("created-user", {
    body: Buffer.from(JSON.stringify({ inbox: signupInbox ?? signupEmail, email: signupEmail, username }, null, 2)),
    contentType: "application/json",
  });

  if (mailbox) {
    await test.step("Activate the account from its email", async () => {
      if (process.env.E2E_SHOW_WEBMAIL === "true") {
        const message = await waitForNewestMessage(signupEmail, signupStartedAt, mailbox);
        const activationPage = await activateThroughVisibleWebmail(page, mailbox, message);
        await expect(activationPage).toHaveURL(new RegExp("/accounts/active/password-secret/"));
      } else {
        const message = await waitForActivationLink(signupEmail, signupStartedAt, mailbox);
        if (!message.activationUrl) {
          throw new Error(`Activation URL was not found for ${signupEmail}.`);
        }
        const response = await page.goto(message.activationUrl);
        expect(response?.ok(), `Activation request failed at ${message.activationUrl}`).toBe(true);
        await expect(page).toHaveURL(new RegExp("/accounts/active/password-secret/"));
      }
    });
  }
});
