import { afterEach, describe, expect, it, vi } from "vitest";

import { formatPlainDate } from "./format-plain-date";

const EXTREME_ZONES = [
  "Pacific/Kiritimati",
  "Pacific/Pago_Pago",
  "Etc/UTC",
  "Europe/Madrid",
];
afterEach(() => {
  vi.unstubAllEnvs();
});

describe("formatPlainDate", () => {
  it.each(EXTREME_ZONES)(
    "never shifts the day when the device is in %s",
    (zone) => {
      vi.stubEnv("TZ", zone);
      const newYear = Temporal.PlainDate.from("2026-01-01");

      expect(formatPlainDate(newYear, "en", { dateStyle: "full" })).toBe(
        "Thursday, January 1, 2026",
      );
      expect(formatPlainDate(newYear, "es", { dateStyle: "long" })).toBe(
        "1 de enero de 2026",
      );
    },
  );

  it("keeps a DST change day as it is", () => {
    vi.stubEnv("TZ", "Europe/Madrid");

    expect(
      formatPlainDate(Temporal.PlainDate.from("2026-03-29"), "en", {
        dateStyle: "medium",
      }),
    ).toBe("Mar 29, 2026");
  });
});
