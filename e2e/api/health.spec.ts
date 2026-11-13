import { expect, test } from "@playwright/test";

import { backends, env } from "../support/env";

test("health reports which backend answers", async ({ request }) => {
  const response = await request.get("/api/v1/health");

  expect(response.status()).toBe(200);
  const body: unknown = await response.json();
  expect(body).toEqual({
    status: "ok",
    backend:
      env.expectedBackend ??
      expect.stringMatching(new RegExp(`^(${backends.join("|")})$`)),
    version: expect.any(String),
    last_scheduler_run: null,
  });
});
