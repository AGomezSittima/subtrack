import { expect } from "@playwright/test";
import type { components } from "@subtrack/contract";

import type { ApiClient } from "./api";

export type Settings = components["schemas"]["Settings"];

/** The settings right after the migrations (and after every reset). */
export const defaultSettings = {
  default_reminder_days: 3,
  trial_reminder_days: 3,
  yearly_reminder_days: 14,
  ending_reminder_days: 7,
  payment_horizon_days: 60,
  logo_dev_token: null,
  locale: null,
  theme: "system",
  font_size: "md",
  reduced_motion: "system",
  week_start: 1,
  lock_policy: "idle_15",
  home_time_zone: null,
  display_time_zone: "device",
} satisfies Settings;

export async function saveSettings(
  api: ApiClient,
  settings: Settings,
): Promise<Settings> {
  const { data, response } = await api.PUT("/settings", { body: settings });
  expect(response.status, "PUT /settings").toBe(200);
  if (data === undefined) {
    throw new Error("PUT /settings returned no body");
  }
  return data;
}

/** Sets the home time zone on top of the default settings. */
export async function setHomeZone(
  api: ApiClient,
  zone: string | null,
): Promise<void> {
  await saveSettings(api, { ...defaultSettings, home_time_zone: zone });
}
