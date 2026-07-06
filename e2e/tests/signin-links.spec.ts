import { expect, test } from "@playwright/test";

const links = [
  { name: "Forgot password?", path: "/password", field: "#email" },
  { name: "Forgot username?", path: "/username", field: "#emailAddress" },
  { name: "Email activation link", path: "/accounts/active", field: "#emailAddress" },
  { name: "Unlock account", path: "/accounts/lock", field: "#email" },
  { name: "Signup here", path: "/signup", field: "#firstName" },
] as const;

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Sign in to OpenIssuer" })).toBeVisible();
});

for (const link of links) {
  test(`${link.name} opens ${link.path}`, async ({ page }) => {
    await page.getByRole("link", { name: link.name, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`${link.path.replaceAll("/", "\\/")}(?:\\?.*)?$`));
    await expect(page.locator(link.field)).toBeVisible();
  });
}
