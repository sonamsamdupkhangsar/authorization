import { expect, test } from "@playwright/test";

const signupEmail = process.env.E2E_SIGNUP_EMAIL;
const signupPassword = process.env.E2E_SIGNUP_PASSWORD ?? "OpenIssuer-Test-42";

test.skip(!signupEmail, "Set E2E_SIGNUP_EMAIL to an inbox you can access.");

test("signup submits a new user and records the interaction", async ({ page }, testInfo) => {
  const uniqueSuffix = Date.now().toString().slice(-10);
  const username = process.env.E2E_SIGNUP_USERNAME ?? `e2e-${uniqueSuffix}`;

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
    await page.locator("#email").fill(signupEmail!);
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
    body: Buffer.from(JSON.stringify({ email: signupEmail, username }, null, 2)),
    contentType: "application/json",
  });
});
