import type { ApiClient } from "../support/api";
import { setClock } from "../support/clock";
import { backends, env } from "../support/env";
import { expect, test } from "../support/fixtures";
import { setHomeZone } from "../support/settings";

const NEAR_MIDNIGHT_UTC = Temporal.Instant.from("2026-11-23T23:30:00.000Z");

async function today(api: ApiClient): Promise<string | undefined> {
  const { data } = await api.GET("/health");
  return data?.today;
}

test("health reports which backend answers", async ({ api }) => {
  await setClock(api, NEAR_MIDNIGHT_UTC);

  const { data, response } = await api.GET("/health");

  expect(response.status).toBe(200);
  expect(data).toEqual({
    status: "ok",
    backend:
      env.expectedBackend ??
      expect.stringMatching(new RegExp(`^(${backends.join("|")})$`)),
    version: expect.any(String),
    today: "2026-11-23",
    last_scheduler_run: null,
  });
});

test("BR-3 without a home time zone, today is the date in UTC", async ({
  api,
}) => {
  await setClock(api, NEAR_MIDNIGHT_UTC);
  expect(await today(api)).toBe("2026-11-23");

  await setClock(api, Temporal.Instant.from("2026-11-24T00:30:00.000Z"));
  expect(await today(api)).toBe("2026-11-24");
});

test("BR-3 FR-53 today is the date in the home time zone, near midnight UTC", async ({
  api,
}) => {
  await setClock(api, NEAR_MIDNIGHT_UTC);

  await setHomeZone(api, "Europe/Madrid");
  expect(await today(api)).toBe("2026-11-24");

  await setHomeZone(api, "America/New_York");
  expect(await today(api)).toBe("2026-11-23");

  await setClock(api, Temporal.Instant.from("2026-11-24T00:30:00.000Z"));
  expect(await today(api)).toBe("2026-11-23");
});

test("BR-3 today follows the test clock across a DST change in the home zone", async ({
  api,
}) => {
  await setHomeZone(api, "Europe/Madrid");

  await setClock(api, Temporal.Instant.from("2026-03-28T22:59:00.000Z"));
  expect(await today(api)).toBe("2026-03-28");

  await setClock(api, Temporal.Instant.from("2026-03-28T23:00:00.000Z"));
  expect(await today(api)).toBe("2026-03-29");
});

test("BR-3 clearing the home time zone goes back to UTC", async ({ api }) => {
  await setClock(api, NEAR_MIDNIGHT_UTC);
  await setHomeZone(api, "Pacific/Auckland");
  expect(await today(api)).toBe("2026-11-24");

  await setHomeZone(api, null);
  expect(await today(api)).toBe("2026-11-23");
});

test("BR-3 a reset forgets the home time zone", async ({ api }) => {
  await setClock(api, NEAR_MIDNIGHT_UTC);
  await setHomeZone(api, "Europe/Madrid");

  await api.POST("/admin/reset");
  await setClock(api, NEAR_MIDNIGHT_UTC);

  expect(await today(api)).toBe("2026-11-23");
});

test("health answers HEAD like GET, without a body", async ({ request }) => {
  const response = await request.head("/api/v1/health");

  expect(response.status()).toBe(200);
  expect(await response.body()).toHaveLength(0);
});

test("the API sets no cookies while no passcode is set", async ({
  request,
}) => {
  const responses = [
    await request.get("/api/v1/health"),
    await request.get("/api/v1/nope"),
    await request.post("/api/v1/admin/reset"),
  ];

  for (const response of responses) {
    expect(response.headers()["set-cookie"]).toBeUndefined();
  }
});
