import { setClock } from "../support/clock";
import { expect, test } from "../support/fixtures";
import { expectProblem } from "../support/problems";

const CLOCK = "/api/v1/admin/clock";

test("reset answers 204 and can run twice", async ({ api }) => {
  for (let run = 0; run < 2; run++) {
    const { response } = await api.POST("/admin/reset");
    expect(response.status).toBe(204);
  }
});

test("setting the clock answers 204", async ({ api }) => {
  const { response } = await api.POST("/admin/clock", {
    body: { now: "2026-03-28T23:30:00.000Z" },
  });

  expect(response.status).toBe(204);
});

test("the clock helper sends the instant with milliseconds", async ({
  api,
}) => {
  await expect(
    setClock(api, Temporal.Instant.from("2026-03-28T23:30:00Z")),
  ).resolves.toBeUndefined();
});

test("a missing now is the only issue reported", async ({ request }) => {
  const response = await request.post(CLOCK, { data: {} });

  expect(await expectProblem(response, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/now", code: "required" },
  ]);
});

for (const now of [
  "2026-11-10T15:18:00Z",
  "2026-11-10T15:18:00.000+01:00",
  "2026-02-30T00:00:00.000Z",
  "2026-11-10T23:59:60.000Z",
  "0000-01-01T00:00:00.000Z",
  "",
]) {
  test(`a non-canonical now (${now || "empty"}) is a format issue`, async ({
    request,
  }) => {
    const response = await request.post(CLOCK, { data: { now } });

    expect(await expectProblem(response, "validation-error", 422)).toEqual([
      { location: "body", pointer: "/now", code: "format" },
    ]);
  });
}

test("a wrong type is reported", async ({ request }) => {
  const response = await request.post(CLOCK, { data: { now: 5 } });

  expect(await expectProblem(response, "validation-error", 422)).toContainEqual(
    { location: "body", pointer: "/now", code: "type" },
  );
});

test("an unknown property is reported", async ({ request }) => {
  const response = await request.post(CLOCK, {
    data: { now: "2026-11-10T15:18:00.000Z", extra: 1 },
  });

  expect(await expectProblem(response, "validation-error", 422)).toContainEqual(
    { location: "body", pointer: "/extra", code: "unknown_property" },
  );
});

test("a body that is not an object is a type issue", async ({ request }) => {
  const response = await request.post(CLOCK, { data: [] });

  expect(await expectProblem(response, "validation-error", 422)).toContainEqual(
    { location: "body", pointer: "", code: "type" },
  );
});

test("malformed JSON is a malformed request", async ({ request }) => {
  const response = await request.post(CLOCK, {
    data: Buffer.from("{bad"),
    headers: { "content-type": "application/json" },
  });

  await expectProblem(response, "malformed-request", 400);
});

for (const contentType of [
  "text/plain",
  "application/x-www-form-urlencoded",
  "multipart/form-data",
  "application/octet-stream",
]) {
  test(`a ${contentType} body is an unsupported media type`, async ({
    request,
  }) => {
    const response = await request.post(CLOCK, {
      data: '{"now": "2026-11-10T15:18:00.000Z"}',
      headers: { "content-type": contentType },
    });

    await expectProblem(response, "unsupported-media-type", 415);
  });
}
