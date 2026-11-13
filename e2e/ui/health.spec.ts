import { expect, test } from "@playwright/test";

import { type Backend, env } from "../support/env";

const backendNames = {
  fastapi: "FastAPI",
  spring: "Spring Boot",
} satisfies Record<Backend, string>;

const expectedName = env.expectedBackend
  ? backendNames[env.expectedBackend]
  : new RegExp(Object.values(backendNames).join("|"));

test("home page shows the backend that answers", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Subtrack" })).toBeVisible();
  await expect(page.getByRole("main")).toContainText("Connected to the");
  await expect(page.getByRole("main")).toContainText(expectedName);
  await expect(page.getByRole("contentinfo")).toContainText(expectedName);
});

test.describe("in Spanish", () => {
  test.use({ locale: "es-ES" });

  test("home page shows the backend in Spanish", async ({ page }) => {
    await page.goto("/");

    await expect(page.locator("html")).toHaveAttribute("lang", "es");
    await expect(page.getByRole("main")).toContainText("Conectado al backend");
    await expect(page.getByRole("main")).toContainText(expectedName);
  });
});
