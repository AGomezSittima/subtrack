import { defineConfig, devices } from "@playwright/test";

import { env } from "./support/env";
import type { TestOptions } from "./support/fixtures";

// Runs against a stack that is already up (`just test-api` / `just test-e2e` / `just test-docs` start it).
export default defineConfig<TestOptions>({
  forbidOnly: env.ci,
  // Every test resets the stack's single database, so tests can't run at the same time.
  workers: 1,
  retries: env.ci ? 1 : 0,
  reporter: env.ci ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: env.baseUrl,
    trace: "retain-on-failure",
  },
  projects: [
    { name: "api", testDir: "./api" },
    { name: "cross-write", testDir: "./cross", testMatch: "write.spec.ts" },
    {
      name: "cross-read",
      testDir: "./cross",
      testMatch: "read.spec.ts",
      use: { resetDatabase: false },
    },
    {
      name: "ui",
      testDir: "./ui",
      use: { ...devices["Desktop Chrome"], locale: "en-US" },
    },
    {
      name: "docs",
      testDir: "./docs",
      use: { ...devices["Desktop Chrome"], baseURL: env.docsBaseUrl },
    },
  ],
});
