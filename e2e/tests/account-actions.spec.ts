import { expect, test } from "@playwright/test";

const accountEmail = process.env.E2E_ACCOUNT_EMAIL;

test.describe("email actions for an activated test account", () => {
  test.skip(!accountEmail, "Set E2E_ACCOUNT_EMAIL to an activated test account.");

  test("forgot username sends an email", async ({ page }) => {
    await page.goto("/username");
    await page.locator("#emailAddress").fill(accountEmail!);
    await page.locator("#emailUsername").click();
    await expect(page.getByText(/username has been sent/i)).toBeVisible();
  });

  test("forgot password sends a reset secret", async ({ page }) => {
    await page.goto("/password");
    await page.locator("#email").fill(accountEmail!);
    await page.locator("#changePassword").click();
    await expect(page.getByText(/check your email for changing your password/i)).toBeVisible();
  });

});

test.describe("activation email for an inactive test account", () => {
  const inactiveAccountEmail = process.env.E2E_INACTIVE_ACCOUNT_EMAIL;

  test.skip(!inactiveAccountEmail, "Set E2E_INACTIVE_ACCOUNT_EMAIL to an inactive test account.");

  test("activation link can be requested", async ({ page }) => {
    await page.goto("/accounts/active");
    await page.locator("#emailAddress").fill(inactiveAccountEmail!);
    await page.locator("#emailUsername").click();
    await expect(page.getByText(/email sent successfully/i)).toBeVisible();
  });
});

test.describe("actions requiring a manually copied email secret", () => {
  const resetSecret = process.env.E2E_PASSWORD_RESET_SECRET;
  const newPassword = process.env.E2E_NEW_PASSWORD ?? "OpenIssuer-Changed-43";

  test.skip(
    !accountEmail || !resetSecret,
    "Set E2E_ACCOUNT_EMAIL and E2E_PASSWORD_RESET_SECRET after receiving the reset email.",
  );

  test("password reset completes with the emailed secret", async ({ page }) => {
    await page.goto("/password/secret");
    await page.locator("#email").fill(accountEmail!);
    await page.locator("#secret").fill(resetSecret!);
    await page.locator("#password").fill(newPassword);
    await page.getByRole("button", { name: "Change password" }).click();
    await expect(page.getByText(/password has been updated successfully/i)).toBeVisible();
  });
});

test.describe("requesting unlock for a locked test account", () => {
  const lockedAccountEmail = process.env.E2E_LOCKED_ACCOUNT_EMAIL;

  test.skip(!lockedAccountEmail, "Set E2E_LOCKED_ACCOUNT_EMAIL to an account that is currently locked.");

  test("account unlock email can be requested", async ({ page }) => {
    await page.goto("/accounts/lock");
    await page.locator("#email").fill(lockedAccountEmail!);
    await page.locator("#unlockMyAccount").click();
    await expect(page.getByText(/check the associated email to unlock account/i)).toBeVisible();
  });
});

test.describe("unlocking a locked test account", () => {
  const lockedAccountEmail = process.env.E2E_LOCKED_ACCOUNT_EMAIL;
  const unlockSecret = process.env.E2E_ACCOUNT_UNLOCK_SECRET;

  test.skip(
    !lockedAccountEmail || !unlockSecret,
    "Set E2E_LOCKED_ACCOUNT_EMAIL and E2E_ACCOUNT_UNLOCK_SECRET after receiving the unlock email.",
  );

  test("account unlock completes with the emailed secret", async ({ page }) => {
    await page.goto("/accounts/lock/secret");
    await page.locator("#email").fill(lockedAccountEmail!);
    await page.locator("#secret").fill(unlockSecret!);
    await page.locator("#unlockMyAccount").click();
    await expect(page.getByText(/account associated with the email has been unlocked/i)).toBeVisible();
  });
});
