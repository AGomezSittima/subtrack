import { expect, test } from "../support/fixtures";
import { expectProblem, type ValidationIssue } from "../support/problems";
import {
  defaultSettings,
  saveSettings,
  type Settings,
} from "../support/settings";

const SETTINGS = "/api/v1/settings";

test("FR-53 FR-54 settings start with no home or display time zone", async ({
  api,
}) => {
  const { data, response } = await api.GET("/settings");

  expect(response.status).toBe(200);
  expect(data).toEqual(defaultSettings);
});

test("FR-26 every setting is replaced and read back", async ({ api }) => {
  const changed = {
    default_reminder_days: 0,
    trial_reminder_days: 365,
    yearly_reminder_days: 30,
    ending_reminder_days: 1,
    payment_horizon_days: 366,
    logo_dev_token: "pk_abc123",
    locale: "es",
    theme: "dark",
    font_size: "lg",
    reduced_motion: "always",
    week_start: 7,
    lock_policy: "manual",
    home_time_zone: "Europe/Lisbon",
    display_time_zone: "America/Argentina/Buenos_Aires",
  } satisfies Settings;

  expect(await saveSettings(api, changed)).toEqual(changed);
  const { data } = await api.GET("/settings");
  expect(data).toEqual(changed);
});

for (const zone of [
  "Europe/Lisbon",
  "America/Argentina/Buenos_Aires",
  "Etc/UTC",
  "Asia/Kolkata",
  "Etc/GMT+5",
  // Legacy aliases that browsers still report as the device's zone.
  "America/Buenos_Aires",
  "Asia/Calcutta",
]) {
  test(`FR-53 FR-54 ${zone} is accepted as home and display time zone`, async ({
    api,
  }) => {
    const saved = await saveSettings(api, {
      ...defaultSettings,
      home_time_zone: zone,
      display_time_zone: zone,
    });

    expect(saved).toMatchObject({
      home_time_zone: zone,
      display_time_zone: zone,
    });
  });
}

test("FR-54 the display time zone can follow the device again", async ({
  api,
}) => {
  await saveSettings(api, {
    ...defaultSettings,
    display_time_zone: "Europe/Madrid",
  });

  const saved = await saveSettings(api, {
    ...defaultSettings,
    display_time_zone: null,
  });

  expect(saved.display_time_zone).toBeNull();
});

test("FR-53 the home time zone can be cleared, which falls back to UTC", async ({
  api,
}) => {
  await saveSettings(api, {
    ...defaultSettings,
    home_time_zone: "Pacific/Auckland",
  });

  const saved = await saveSettings(api, {
    ...defaultSettings,
    home_time_zone: null,
  });

  expect(saved.home_time_zone).toBeNull();
});

const zoneIssues: [string, Record<string, unknown>, ValidationIssue[]][] = [
  [
    "an unknown home time zone",
    { home_time_zone: "Mars/Olympus" },
    [{ location: "body", pointer: "/home_time_zone", code: "invalid" }],
  ],
  [
    "an unknown display time zone",
    { display_time_zone: "Mars/Olympus" },
    [{ location: "body", pointer: "/display_time_zone", code: "invalid" }],
  ],
  [
    "device as a time zone",
    { home_time_zone: "device", display_time_zone: "device" },
    [
      { location: "body", pointer: "/display_time_zone", code: "invalid" },
      { location: "body", pointer: "/home_time_zone", code: "invalid" },
    ],
  ],
  [
    "an offset instead of a zone name",
    { home_time_zone: "+01:00", display_time_zone: "-03:00" },
    [
      { location: "body", pointer: "/display_time_zone", code: "pattern" },
      { location: "body", pointer: "/home_time_zone", code: "pattern" },
    ],
  ],
  [
    "a GMT or UTC offset name",
    { home_time_zone: "GMT+1", display_time_zone: "UTC+1" },
    [
      { location: "body", pointer: "/display_time_zone", code: "invalid" },
      { location: "body", pointer: "/home_time_zone", code: "invalid" },
    ],
  ],
  [
    "a zone name in the wrong case",
    { home_time_zone: "europe/madrid" },
    [{ location: "body", pointer: "/home_time_zone", code: "invalid" }],
  ],
  [
    "an empty zone name",
    { home_time_zone: "", display_time_zone: "" },
    [
      { location: "body", pointer: "/display_time_zone", code: "pattern" },
      { location: "body", pointer: "/home_time_zone", code: "pattern" },
    ],
  ],
  [
    "a zone name that is too long",
    { home_time_zone: `Europe/${"A".repeat(58)}` },
    [
      {
        location: "body",
        pointer: "/home_time_zone",
        code: "max_length",
        params: { max_length: 64 },
      },
    ],
  ],
  [
    "a zone that is not a string",
    { home_time_zone: 1 },
    [{ location: "body", pointer: "/home_time_zone", code: "type" }],
  ],
];

for (const [name, change, issues] of zoneIssues) {
  test(`FR-53 FR-54 settings reject ${name}`, async ({ api, request }) => {
    const response = await request.put(SETTINGS, {
      data: { ...defaultSettings, ...change },
    });

    expect(await expectProblem(response, "validation-error", 422)).toEqual(
      issues,
    );
    const { data } = await api.GET("/settings");
    expect(data).toEqual(defaultSettings);
  });
}

for (const token of ["", " ", "\t\n", "\u00a0\u3000"]) {
  test(`settings reject the blank logo token ${JSON.stringify(token)}`, async ({
    api,
    request,
  }) => {
    const response = await request.put(SETTINGS, {
      data: { ...defaultSettings, logo_dev_token: token },
    });

    expect(await expectProblem(response, "validation-error", 422)).toEqual([
      { location: "body", pointer: "/logo_dev_token", code: "pattern" },
    ]);
    const { data } = await api.GET("/settings");
    expect(data).toEqual(defaultSettings);
  });
}

test("a logo token over 200 code points is too long, even when blank", async ({
  request,
}) => {
  const response = await request.put(SETTINGS, {
    data: { ...defaultSettings, logo_dev_token: " ".repeat(201) },
  });

  expect(await expectProblem(response, "validation-error", 422)).toEqual([
    {
      location: "body",
      pointer: "/logo_dev_token",
      code: "max_length",
      params: { max_length: 200 },
    },
  ]);
});

test("settings reject a missing time zone and out-of-range values", async ({
  request,
}) => {
  const { home_time_zone: _, ...withoutHomeZone } = defaultSettings;
  const response = await request.put(SETTINGS, {
    data: { ...withoutHomeZone, payment_horizon_days: 0, theme: "blue" },
  });

  expect(await expectProblem(response, "validation-error", 422)).toEqual([
    { location: "body", pointer: "/home_time_zone", code: "required" },
    {
      location: "body",
      pointer: "/payment_horizon_days",
      code: "minimum",
      params: { minimum: 1 },
    },
    { location: "body", pointer: "/theme", code: "enum" },
  ]);
});
