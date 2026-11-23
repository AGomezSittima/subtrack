import { expect, test } from "../support/fixtures";
import { setHomeZone } from "../support/settings";

const ZONE = "Pacific/Auckland";

test.use({ timezoneId: ZONE });

test("FR-53 the first load saves the device's time zone as home and says so", async ({
  page,
  api,
}) => {
  await page.goto("/");

  const notice = page.getByRole("status").filter({ hasText: ZONE });
  await expect(notice).toContainText(`Home time zone set to ${ZONE}`);
  const { data } = await api.GET("/settings");
  expect(data?.home_time_zone).toBe(ZONE);

  await notice.getByRole("button", { name: "Dismiss" }).click();
  await expect(notice).toBeHidden();

  await page.reload();
  await expect(page.getByRole("heading", { name: "Subtrack" })).toBeVisible();
  await expect(page.getByText("Home time zone set to")).toBeHidden();
});

test("FR-53 an existing home time zone is left alone", async ({
  page,
  api,
}) => {
  await setHomeZone(api, "Europe/Madrid");

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Subtrack" })).toBeVisible();
  await expect(page.getByText("Home time zone set to")).toBeHidden();
  const { data } = await api.GET("/settings");
  expect(data?.home_time_zone).toBe("Europe/Madrid");
});

test.describe("in Spanish", () => {
  test.use({ locale: "es-ES" });

  test("FR-53 the home time zone notice is translated", async ({ page }) => {
    await page.goto("/");

    await expect(
      page.getByRole("status").filter({ hasText: ZONE }),
    ).toContainText(`Zona horaria de casa establecida en ${ZONE}`);
  });
});
