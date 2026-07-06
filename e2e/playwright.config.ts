import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  outputDir: "test-results",
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "https://free.openissuer.test:9001",
    ignoreHTTPSErrors: true,
    screenshot: "off",
    trace: "retain-on-failure",
    video: "off",
    ...devices["Desktop Chrome"],
  },
});
