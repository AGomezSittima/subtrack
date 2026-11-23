import type { APIRequestContext, APIResponse } from "@playwright/test";
import type { components } from "@subtrack/contract";

import { expect, test } from "../support/fixtures";
import { expectProblem, type ValidationIssue } from "../support/problems";
import { defaultSettings } from "../support/settings";

const CATALOG = "/api/v1/catalog";
const SETTINGS = "/api/v1/settings";

type CatalogServiceCreate = components["schemas"]["CatalogServiceCreate"];

const plan = {
  plan: "Monthly",
  interval_count: 1,
  interval_unit: "month",
} satisfies CatalogServiceCreate["plans"][number];
const service = {
  name: "Gym",
  category: "other",
  plans: [plan],
} satisfies CatalogServiceCreate;

interface TextField {
  name: string;
  pointer: string;
  send: (request: APIRequestContext, value: string) => Promise<APIResponse>;
  read: (response: APIResponse) => Promise<unknown>;
}

async function createdId(request: APIRequestContext): Promise<number> {
  const response = await request.post(CATALOG, { data: service });
  expect(response.status()).toBe(201);
  return ((await response.json()) as { id: number }).id;
}

const fields: TextField[] = [
  {
    name: "a new catalogue service name",
    pointer: "/name",
    send: (request, value) =>
      request.post(CATALOG, { data: { ...service, name: value } }),
    read: async (response) =>
      ((await response.json()) as { name: string }).name,
  },
  {
    name: "an edited catalogue service name",
    pointer: "/name",
    send: async (request, value) =>
      request.patch(`${CATALOG}/${String(await createdId(request))}`, {
        data: { name: value },
      }),
    read: async (response) =>
      ((await response.json()) as { name: string }).name,
  },
  {
    name: "a catalogue plan name",
    pointer: "/plans/0/plan",
    send: (request, value) =>
      request.post(CATALOG, {
        data: { ...service, plans: [{ ...plan, plan: value }] },
      }),
    read: async (response) =>
      ((await response.json()) as { plans: { plan: string }[] }).plans[0]?.plan,
  },
  {
    name: "the logo token",
    pointer: "/logo_dev_token",
    send: (request, value) =>
      request.put(SETTINGS, {
        data: { ...defaultSettings, logo_dev_token: value },
      }),
    read: async (response) =>
      ((await response.json()) as { logo_dev_token: string }).logo_dev_token,
  },
];

const whitespace: [string, string][] = [
  ["space", " "],
  ["tab", "\t"],
  ["line feed", "\n"],
  ["carriage return", "\r"],
];

const malformed: [string, string][] = [
  ["only spaces", "   "],
  ["only space, tab, line feed and carriage return", " \t\n\r"],
  ...whitespace.flatMap(([label, char]): [string, string][] => [
    [`a leading ${label}`, `${char}Gym`],
    [`a trailing ${label}`, `Gym${char}`],
  ]),
];

const accepted: [string, string][] = [
  ["inner spaces", "Gym and pool"],
  ["an inner tab", "Gym\tpool"],
  ["inner line breaks", "Gym\npool\r\nsauna"],
  ["non-breaking spaces at both ends", " Gym "],
];

for (const field of fields) {
  test(`${field.name} can't be empty`, async ({ request }) => {
    const response = await field.send(request, "");

    expect(await expectProblem(response, "validation-error", 422)).toEqual([
      {
        location: "body",
        pointer: field.pointer,
        code: "min_length",
        params: { min_length: 1 },
      },
    ] satisfies ValidationIssue[]);
  });

  for (const [label, value] of malformed) {
    test(`${field.name} with ${label} is a pattern issue`, async ({
      request,
    }) => {
      const response = await field.send(request, value);

      expect(await expectProblem(response, "validation-error", 422)).toEqual([
        { location: "body", pointer: field.pointer, code: "pattern" },
      ] satisfies ValidationIssue[]);
    });
  }

  for (const [label, value] of accepted) {
    test(`${field.name} with ${label} is stored exactly as sent`, async ({
      request,
    }) => {
      const response = await field.send(request, value);

      expect(response.ok()).toBe(true);
      expect(await field.read(response)).toBe(value);
    });
  }
}

test("text read back is exactly what was sent", async ({ api }) => {
  const name = "Gym and pool";
  const planName = "Two\nlines";
  const { data: created } = await api.POST("/catalog", {
    body: { ...service, name, plans: [{ ...plan, plan: planName }] },
  });
  await api.PUT("/settings", {
    body: { ...defaultSettings, logo_dev_token: " pk_abc " },
  });

  const { data: catalog } = await api.GET("/catalog", {
    params: { query: { q: "pool" } },
  });
  const { data: settings } = await api.GET("/settings");

  expect(catalog?.items).toEqual([created]);
  expect(created).toMatchObject({ name, plans: [{ plan: planName }] });
  expect(settings?.logo_dev_token).toBe(" pk_abc ");
});
