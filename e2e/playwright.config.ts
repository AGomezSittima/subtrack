import { defineConfig, devices } from "@playwright/test";

import { env } from "./support/env";

// Runs against a stack that is already up (`just test-api` / `just test-e2e` start it).
export default defineConfig({
  forbidOnly: env.ci,
  retries: env.ci ? 1 : 0,
  reporter: env.ci ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: env.baseUrl,
    trace: "retain-on-failure",
  },
  projects: [
    { name: "api", testDir: "./api" },
    {
      name: "ui",
      testDir: "./ui",
      use: { ...devices["Desktop Chrome"], locale: "en-US" },
    },
  ],
});
