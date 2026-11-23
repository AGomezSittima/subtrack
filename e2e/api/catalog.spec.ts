import type { components } from "@subtrack/contract";

import type { ApiClient } from "../support/api";
import { setClock } from "../support/clock";
import { expect, test } from "../support/fixtures";
import { expectProblem, type ValidationIssue } from "../support/problems";

type CatalogService = components["schemas"]["CatalogService"];
type CatalogServiceCreate = components["schemas"]["CatalogServiceCreate"];

const CATALOG = "/api/v1/catalog";
const CREATED = Temporal.Instant.from("2026-11-23T10:00:00.123Z");
const EDITED = Temporal.Instant.from("2026-11-24T08:30:00.456Z");

const gym = {
  name: "Gym",
  domain: "gym.example",
  category: "other",
  plans: [
    {
      plan: "Monthly",
      price_cents: 3500,
      interval_count: 1,
      interval_unit: "month",
    },
  ],
} satisfies CatalogServiceCreate;

const plan = {
  plan: "Monthly",
  interval_count: 1,
  interval_unit: "month",
} satisfies CatalogServiceCreate["plans"][number];
const valid = {
  name: "Gym",
  category: "other",
  plans: [plan],
} satisfies CatalogServiceCreate;

async function list(
  api: ApiClient,
  query: { q?: string; include_hidden?: boolean } = {},
): Promise<CatalogService[]> {
  const { data, response } = await api.GET("/catalog", { params: { query } });
  expect(response.status).toBe(200);
  return data?.items ?? [];
}

async function builtin(api: ApiClient, name: string): Promise<CatalogService> {
  const found = (await list(api, { q: name })).find(
    (entry) => entry.builtin && entry.name === name,
  );
  if (found === undefined) {
    throw new Error(`no built-in named ${name}`);
  }
  return found;
}

async function create(
  api: ApiClient,
  body: CatalogServiceCreate,
): Promise<CatalogService> {
  const { data, response } = await api.POST("/catalog", { body });
  expect(response.status).toBe(201);
  return data as CatalogService;
}

function asciiLower(text: string): string {
  return text.replace(/[A-Z]/g, (letter) => letter.toLowerCase());
}

test("FR-2 the catalogue lists the 54 built-in services by name, none hidden and without prices", async ({
  api,
}) => {
  const items = await list(api);

  expect(items).toHaveLength(54);
  expect(items.every((entry) => entry.builtin && !entry.hidden)).toBe(true);
  expect(
    items.flatMap((entry) => entry.plans.map((p) => p.price_cents)),
  ).toEqual(expect.not.arrayContaining([expect.any(Number)]));
  const names = items.map((entry) => entry.name);
  expect(names.slice(0, 3)).toEqual([
    "1Password",
    "Adobe Creative Cloud",
    "Amazon Music Unlimited",
  ]);
  const sorted = [...names].sort((a, b) =>
    asciiLower(a) < asciiLower(b) ? -1 : asciiLower(a) > asciiLower(b) ? 1 : 0,
  );
  expect(names).toEqual(sorted);
});

test("FR-2 q matches part of the name, ignoring ASCII case", async ({
  api,
}) => {
  const all = await list(api);

  const found = await list(api, { q: "PROTON" });

  expect(found.map((entry) => entry.name)).toEqual(
    all
      .map((entry) => entry.name)
      .filter((name) => name.toLowerCase().includes("proton")),
  );
  expect(found.length).toBeGreaterThan(1);
  expect(await list(api, { q: "%" })).toEqual([]);
});

test("FR-47 FR-51 a user service is created with optional prices at the current time", async ({
  api,
}) => {
  await setClock(api, CREATED);

  const created = await create(api, gym);
  const priceless = await create(api, {
    name: "Local paper",
    category: "news",
    plans: [{ plan: "Weekend", interval_count: 1, interval_unit: "week" }],
  });

  expect(created).toEqual({
    id: expect.any(Number),
    ...gym,
    builtin: false,
    hidden: false,
    created_at: "2026-11-23T10:00:00.123Z",
    updated_at: "2026-11-23T10:00:00.123Z",
  });
  expect(priceless).toMatchObject({
    domain: null,
    category: "news",
    plans: [
      {
        plan: "Weekend",
        price_cents: null,
        interval_count: 1,
        interval_unit: "week",
      },
    ],
  });
  expect(await list(api, { q: "gym" })).toEqual([created]);
});

test("FR-47 a user service may share a built-in's name", async ({ api }) => {
  const spotify = await builtin(api, "Spotify");

  const mine = await create(api, { ...valid, name: "Spotify" });

  expect((await list(api, { q: "spotify" })).map((e) => e.id)).toEqual([
    spotify.id,
    mine.id,
  ]);
});

test("FR-48 FR-51 a built-in can be edited and given prices", async ({
  api,
}) => {
  const spotify = await builtin(api, "Spotify");
  await setClock(api, EDITED);
  const plans = [
    {
      plan: "Premium Individual",
      price_cents: 1199,
      interval_count: 1,
      interval_unit: "month",
    },
    {
      plan: "Premium Individual",
      price_cents: 11990,
      interval_count: 1,
      interval_unit: "year",
    },
  ] satisfies CatalogServiceCreate["plans"];

  const { data, response } = await api.PATCH("/catalog/{id}", {
    params: { path: { id: spotify.id } },
    body: { name: "Spotify Premium", plans },
  });

  expect(response.status).toBe(200);
  const expected = {
    ...spotify,
    name: "Spotify Premium",
    plans,
    updated_at: "2026-11-24T08:30:00.456Z",
  };
  expect(data).toEqual(expected);
  expect(await list(api, { q: "spotify" })).toEqual([expected]);
});

test("FR-48 an edit changes only the fields sent, and null clears the domain", async ({
  api,
}) => {
  await setClock(api, CREATED);
  const created = await create(api, gym);
  await setClock(api, EDITED);

  const untouched = await api.PATCH("/catalog/{id}", {
    params: { path: { id: created.id } },
    body: {},
  });
  const cleared = await api.PATCH("/catalog/{id}", {
    params: { path: { id: created.id } },
    body: { domain: null, category: "gaming" },
  });

  const updated_at = "2026-11-24T08:30:00.456Z";
  expect(untouched.data).toEqual({ ...created, updated_at });
  expect(cleared.data).toEqual({
    ...created,
    domain: null,
    category: "gaming",
    updated_at,
  });
});

test("FR-49 a hidden built-in is listed only with include_hidden, and can be shown again", async ({
  api,
}) => {
  const netflix = await builtin(api, "Netflix");

  const hidden = await api.PATCH("/catalog/{id}", {
    params: { path: { id: netflix.id } },
    body: { hidden: true },
  });

  expect(hidden.data?.hidden).toBe(true);
  expect(await list(api, { q: "netflix" })).toEqual([]);
  expect(await list(api, { q: "netflix", include_hidden: false })).toEqual([]);
  expect(await list(api, { q: "netflix", include_hidden: true })).toEqual([
    hidden.data,
  ]);
  expect(await list(api, { include_hidden: true })).toHaveLength(54);

  const shown = await api.PATCH("/catalog/{id}", {
    params: { path: { id: netflix.id } },
    body: { hidden: false },
  });

  expect(shown.data?.hidden).toBe(false);
  expect(await list(api, { q: "netflix" })).toEqual([shown.data]);
});

test("FR-49 a user service can't be hidden", async ({ api, request }) => {
  const created = await create(api, gym);

  const response = await request.patch(`${CATALOG}/${String(created.id)}`, {
    data: { hidden: true },
  });

  expect(await expectProblem(response, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/hidden", code: "invalid" },
  ]);
});

test("FR-50 a user service can be deleted", async ({ api }) => {
  const created = await create(api, gym);

  const first = await api.DELETE("/catalog/{id}", {
    params: { path: { id: created.id } },
  });
  const second = await api.DELETE("/catalog/{id}", {
    params: { path: { id: created.id } },
  });

  expect(first.response.status).toBe(204);
  expect(second.response.status).toBe(404);
  expect(await list(api, { q: "gym" })).toEqual([]);
});

test("FR-50 a built-in can't be deleted, only hidden", async ({
  api,
  request,
}) => {
  const spotify = await builtin(api, "Spotify");

  const response = await request.delete(`${CATALOG}/${String(spotify.id)}`);

  await expectProblem(response, "builtin-service-not-deletable", 409);
  expect(await list(api, { q: "spotify" })).toEqual([spotify]);
});

test("an unknown catalogue id is not found", async ({ request }) => {
  const edit = await request.patch(`${CATALOG}/999999`, { data: {} });
  const remove = await request.delete(`${CATALOG}/999999`);

  await expectProblem(edit, "not-found", 404);
  await expectProblem(remove, "not-found", 404);
});

const longName = "ñ".repeat(201);
const createIssues: [string, object, ValidationIssue[]][] = [
  [
    "an empty body",
    {},
    [
      { location: "body", pointer: "/category", code: "required" },
      { location: "body", pointer: "/name", code: "required" },
      { location: "body", pointer: "/plans", code: "required" },
    ],
  ],
  [
    "an empty name",
    { ...valid, name: "" },
    [
      {
        location: "body",
        pointer: "/name",
        code: "min_length",
        params: { min_length: 1 },
      },
    ],
  ],
  [
    "a name over 200 code points",
    { ...valid, name: longName },
    [
      {
        location: "body",
        pointer: "/name",
        code: "max_length",
        params: { max_length: 200 },
      },
    ],
  ],
  [
    "a domain that isn't a lowercase hostname",
    { ...valid, domain: "Spotify.com" },
    [{ location: "body", pointer: "/domain", code: "pattern" }],
  ],
  [
    "a domain over 253 characters",
    { ...valid, domain: `${"a".repeat(250)}.com` },
    [
      {
        location: "body",
        pointer: "/domain",
        code: "max_length",
        params: { max_length: 253 },
      },
    ],
  ],
  [
    "an unknown category",
    { ...valid, category: "games" },
    [{ location: "body", pointer: "/category", code: "enum" }],
  ],
  [
    "more than 50 plans",
    {
      ...valid,
      plans: Array.from({ length: 51 }, (_, i) => ({
        ...plan,
        plan: `P${String(i)}`,
      })),
    },
    [
      {
        location: "body",
        pointer: "/plans",
        code: "max_length",
        params: { max_length: 50 },
      },
    ],
  ],
  [
    "a plan without a name, a negative price, a zero count and an unknown unit",
    {
      ...valid,
      plans: [{ price_cents: -1, interval_count: 0, interval_unit: "months" }],
    },
    [
      {
        location: "body",
        pointer: "/plans/0/interval_count",
        code: "minimum",
        params: { minimum: 1 },
      },
      { location: "body", pointer: "/plans/0/interval_unit", code: "enum" },
      { location: "body", pointer: "/plans/0/plan", code: "required" },
      {
        location: "body",
        pointer: "/plans/0/price_cents",
        code: "minimum",
        params: { minimum: 0 },
      },
    ],
  ],
  [
    "an interval over 366 units and an empty plan name",
    { ...valid, plans: [{ ...plan, plan: "", interval_count: 367 }] },
    [
      {
        location: "body",
        pointer: "/plans/0/interval_count",
        code: "maximum",
        params: { maximum: 366 },
      },
      { location: "body", pointer: "/plans/0/plan", code: "pattern" },
    ],
  ],
  [
    "a plan name of only whitespace",
    { ...valid, plans: [{ ...plan, plan: " \t\u00a0\u2028" }] },
    [{ location: "body", pointer: "/plans/0/plan", code: "pattern" }],
  ],
  [
    "a plan whose name and interval repeat an earlier one",
    {
      ...valid,
      plans: [
        plan,
        { ...plan, interval_unit: "year" },
        { ...plan, price_cents: 999 },
      ],
    },
    [{ location: "body", pointer: "/plans/2", code: "invalid" }],
  ],
];

for (const [label, body, issues] of createIssues) {
  test(`FR-47 creating with ${label} is a validation error`, async ({
    request,
  }) => {
    const response = await request.post(CATALOG, { data: body });

    expect(await expectProblem(response, "validation-error", 422)).toEqual(
      issues,
    );
  });
}

const structuralIssues: [string, object, ValidationIssue][] = [
  [
    "a null name",
    { ...valid, name: null },
    { location: "body", pointer: "/name", code: "type" },
  ],
  [
    "a null category",
    { ...valid, category: null },
    { location: "body", pointer: "/category", code: "type" },
  ],
  [
    "a fractional price",
    { ...valid, plans: [{ ...plan, price_cents: 1.5 }] },
    { location: "body", pointer: "/plans/0/price_cents", code: "type" },
  ],
  [
    "a null plan",
    { ...valid, plans: [null] },
    { location: "body", pointer: "/plans/0", code: "type" },
  ],
  [
    "an unknown property",
    { ...valid, plans: [{ ...plan, extra: 1 }] },
    { location: "body", pointer: "/plans/0/extra", code: "unknown_property" },
  ],
];

for (const [label, body, issue] of structuralIssues) {
  test(`FR-47 creating with ${label} is a validation error`, async ({
    request,
  }) => {
    const response = await request.post(CATALOG, { data: body });

    expect(
      await expectProblem(response, "validation-error", 422),
    ).toContainEqual(issue);
  });
}

test("FR-48 an edit can't set a non-nullable field to null or repeat a plan", async ({
  api,
  request,
}) => {
  const spotify = await builtin(api, "Spotify");
  const url = `${CATALOG}/${String(spotify.id)}`;

  const nullName = await request.patch(url, { data: { name: null } });
  const textHidden = await request.patch(url, { data: { hidden: "true" } });
  const repeated = await request.patch(url, { data: { plans: [plan, plan] } });

  expect(await expectProblem(nullName, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/name", code: "type" },
  ]);
  expect(await expectProblem(textHidden, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/hidden", code: "type" },
  ]);
  expect(await expectProblem(repeated, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/plans/1", code: "invalid" },
  ]);
  expect(await builtin(api, "Spotify")).toEqual(spotify);
});

test("a query chunk without a name is ignored and the last repeated value wins", async ({
  request,
}) => {
  const ignored = await request.get(`${CATALOG}?=false&q=net`);
  const repeated = await request.get(`${CATALOG}?q=zzz&q=ne%74`);

  for (const response of [ignored, repeated]) {
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { items: CatalogService[] };
    expect(body.items.map((entry) => entry.name)).toEqual(["Netflix"]);
  }
});

for (const [query, issues] of [
  [
    "q=",
    [
      {
        location: "query",
        pointer: "/q",
        code: "min_length",
        params: { min_length: 1 },
      },
    ],
  ],
  [
    "include_hidden=yes",
    [{ location: "query", pointer: "/include_hidden", code: "type" }],
  ],
  [
    `q=${"x".repeat(101)}&include_hidden=1`,
    [
      { location: "query", pointer: "/include_hidden", code: "type" },
      {
        location: "query",
        pointer: "/q",
        code: "max_length",
        params: { max_length: 100 },
      },
    ],
  ],
] satisfies [string, ValidationIssue[]][]) {
  test(`listing with ${query.slice(0, 30)} is a validation error`, async ({
    request,
  }) => {
    const response = await request.get(`${CATALOG}?${query}`);

    expect(await expectProblem(response, "validation-error", 422)).toEqual(
      issues,
    );
  });
}

for (const [id, issue] of [
  ["abc", { location: "path", pointer: "/id", code: "type" }],
  ["1.0", { location: "path", pointer: "/id", code: "type" }],
  [
    "0",
    {
      location: "path",
      pointer: "/id",
      code: "minimum",
      params: { minimum: 1 },
    },
  ],
] satisfies [string, ValidationIssue][]) {
  test(`deleting catalogue id ${id} is a validation error`, async ({
    request,
  }) => {
    const response = await request.delete(`${CATALOG}/${id}`);

    expect(await expectProblem(response, "validation-error", 422)).toEqual([
      issue,
    ]);
  });
}

test("deleting a catalogue id beyond 64 bits is a validation error", async ({
  request,
}) => {
  const response = await request.delete(`${CATALOG}/9223372036854775808`);

  const issues = await expectProblem(response, "validation-error", 422);
  expect(issues).toEqual([
    {
      location: "path",
      pointer: "/id",
      code: "maximum",
      params: { maximum: expect.any(Number) },
    },
  ]);
  expect(await response.text()).toContain('"maximum":9223372036854775807');
});

for (const [path, allow] of [
  [CATALOG, "GET, HEAD, POST"],
  [`${CATALOG}/1`, "DELETE, PATCH"],
] satisfies [string, string][]) {
  test(`PUT on ${path} lists every allowed method`, async ({ request }) => {
    const response = await request.put(path, { data: {} });

    await expectProblem(response, "method-not-allowed", 405);
    expect(response.headers().allow).toBe(allow);
  });
}

test("the catalogue answers HEAD like GET, without a body", async ({
  request,
}) => {
  const response = await request.head(CATALOG);

  expect(response.status()).toBe(200);
  expect(await response.body()).toHaveLength(0);
});
