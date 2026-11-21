import { expect, test } from "@playwright/test";

import { backends, env } from "../support/env";

test("API docs list every operation in the contract", async ({
  page,
  request,
}) => {
  const spec = await request.get("/openapi.yaml");
  expect(spec.ok()).toBe(true);
  const operationIds = Array.from(
    (await spec.text()).matchAll(/^\s+operationId:\s*(\w+)\s*$/gm),
    ([, id]) => id ?? "",
  );
  expect(operationIds.length).toBeGreaterThan(0);

  await page.goto("/");

  await expect(
    page.getByRole("heading", { level: 1, name: /^Subtrack API/ }),
  ).toBeVisible();
  const main = page.getByRole("main");
  await expect(
    main.getByRole("button", { name: /^(GET|POST|PUT|PATCH|DELETE) \// }),
  ).toHaveCount(operationIds.length);
  for (const id of operationIds) {
    await expect(
      main.getByRole("button", { name: new RegExp(` ${id}$`) }),
    ).toHaveCount(1);
  }
});

test("API docs proxy /api to the active backend on the same origin", async ({
  request,
}) => {
  const response = await request.get("/api/v1/health");

  expect(response.status()).toBe(200);
  const body: unknown = await response.json();
  expect(body).toMatchObject({
    status: "ok",
    backend:
      env.expectedBackend ??
      expect.stringMatching(new RegExp(`^(${backends.join("|")})$`)),
  });
});
