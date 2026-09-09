import { expect, Page, test } from "@playwright/test";

const issuerUrl = process.env.E2E_ISSUER_URL;
const adminUrl = process.env.E2E_ADMIN_URL;
const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;

test.skip(
  !issuerUrl || !adminUrl || !username || !password,
  "Set E2E_ISSUER_URL, E2E_ADMIN_URL, E2E_USERNAME, and E2E_PASSWORD.",
);

function escapedPath(path: string): RegExp {
  return new RegExp(`${path.replaceAll("/", "\\/")}(?:\\?.*)?$`);
}

async function expectApplicationPage(page: Page): Promise<void> {
  await expect(page.getByText("Whitelabel Error Page", { exact: true })).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("Internal Server Error");
}

test("admin login, clients, profile, and passkeys navigation", async ({ page }) => {
  const issuer = new URL(issuerUrl!);
  const admin = new URL(adminUrl!);

  await test.step("Sign in to the tenant admin application", async () => {
    await page.goto(new URL("/admin/dashboard", admin).toString());

    await expect(page).toHaveURL(new RegExp(`^${issuer.origin.replaceAll(".", "\\.")}/`));
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();

    await page.locator("#username").fill(username!);
    await page.locator("#password").fill(password!);
    const submit = page.locator("#submit");
    await expect(submit).toBeEnabled();
    await submit.click();

    const passkeyChallenge = page.getByRole("heading", { name: "Verify your passkey" });
    if (await passkeyChallenge.isVisible().catch(() => false)) {
      throw new Error(
        "The E2E account requires passkey MFA. Use a dedicated test account without an enrolled physical passkey.",
      );
    }

    await expect(page).toHaveURL(new RegExp(`^${admin.origin.replaceAll(".", "\\.")}/admin/dashboard`));
    await expect(
      page.getByRole("heading", { name: "Manage tenant authorization." }),
    ).toBeVisible();
    await expectApplicationPage(page);
  });

  await test.step("Open the clients list and an existing client", async () => {
    await page.getByRole("link", { name: "Clients", exact: true }).click();
    await expect(page).toHaveURL(escapedPath("/admin/clients"));
    await expect(page.locator('.admin-sidebar a.active')).toHaveText("Clients");
    await expectApplicationPage(page);

    const firstClient = page.locator("ol.list-group li a").first();
    if (await firstClient.count()) {
      await firstClient.click();
      await expect(page).toHaveURL(/\/admin\/clients\/[0-9a-f-]+(?:\?.*)?$/);
      await expect(
        page.getByRole("heading", { name: "Client Update Page" }),
      ).toBeVisible();
      await expect(page.getByRole("textbox", { name: "Client id" })).toBeVisible();
      await expectApplicationPage(page);
    }
  });

  await test.step("Open the AuthzManager profile", async () => {
    await page.getByRole("link", { name: "Your Profile", exact: true }).click();
    await expect(page).toHaveURL(escapedPath("/admin/user/profile"));
    await expect(page.locator("#authenticationId")).toHaveValue(username!);
    await expectApplicationPage(page);
  });

  await test.step("Open Passkeys through the issuer route", async () => {
    await page.getByRole("link", { name: "Passkeys", exact: true }).click();

    await expect(page).toHaveURL((url) =>
      url.origin === issuer.origin
      && url.pathname === "/issuer/mfa/passkeys"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString()
    );
    await expect(page.getByRole("heading", { name: /Passkeys$/ })).toBeVisible();
    await expect(page.locator("#registerPasskey")).toBeVisible();
    await expect(page.getByRole("link", { name: "Back to admin" })).toBeVisible();
    await expectApplicationPage(page);
  });

  await test.step("Preserve the admin return URL across issuer tabs", async () => {
    await page.getByRole("link", { name: "Profile", exact: true }).click();
    await expect(page).toHaveURL((url) =>
      url.origin === issuer.origin
      && url.pathname === "/account/profile"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString()
    );
    await expect(page.getByRole("heading", { name: /(My account|Profile)$/ })).toBeVisible();
    await expect(page.getByRole("link", { name: "Back to admin" })).toBeVisible();

    await page.getByRole("link", { name: "Passkeys", exact: true }).click();
    await expect(page).toHaveURL((url) =>
      url.origin === issuer.origin
      && url.pathname === "/mfa/passkeys"
      && url.searchParams.get("return_url") === new URL("/admin/user/profile", admin).toString()
    );
    await expectApplicationPage(page);
  });

  await test.step("Return to AuthzManager", async () => {
    await page.getByRole("link", { name: "Back to admin" }).click();
    await expect(page).toHaveURL(escapedPath("/admin/user/profile"));
    await expect(page.locator("#authenticationId")).toHaveValue(username!);
    await expectApplicationPage(page);
  });
});
