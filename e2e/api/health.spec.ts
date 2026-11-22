import { backends, env } from "../support/env";
import { expect, test } from "../support/fixtures";

test("health reports which backend answers", async ({ api }) => {
  const { data, response } = await api.GET("/health");

  expect(response.status).toBe(200);
  expect(data).toEqual({
    status: "ok",
    backend:
      env.expectedBackend ??
      expect.stringMatching(new RegExp(`^(${backends.join("|")})$`)),
    version: expect.any(String),
    last_scheduler_run: null,
  });
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
