import { expect, test } from "../support/fixtures";
import { expectProblem } from "../support/problems";

for (const path of [
  "/api/v1/nope",
  "/api/v1",
  "/api/v1/health/",
  "/api/v2/health",
]) {
  test(`an unknown route (${path}) is not found`, async ({ request }) => {
    const response = await request.get(path);

    await expectProblem(response, "not-found", 404);
    expect(await response.json()).toEqual({
      type: "urn:subtrack:problem:not-found",
      title: "Not found",
      status: 404,
    });
  });
}

for (const method of ["DELETE", "PUT", "PATCH", "OPTIONS", "QUERY"]) {
  test(`${method} on a GET-only path is not allowed`, async ({ request }) => {
    const response = await request.fetch("/api/v1/health", { method });

    await expectProblem(response, "method-not-allowed", 405);
    expect(response.headers().allow).toBe("GET, HEAD");
  });
}

test("problem titles are the same in both backends", async ({ request }) => {
  const response = await request.fetch("/api/v1/health", { method: "DELETE" });

  expect(await response.json()).toEqual({
    type: "urn:subtrack:problem:method-not-allowed",
    title: "Method not allowed",
    status: 405,
  });
});
