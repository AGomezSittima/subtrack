import { afterEach, describe, expect, it, vi } from "vitest";

import { formatTimestamp } from "./format-timestamp";

const TIME = { hour: "2-digit", minute: "2-digit", hourCycle: "h23" } as const;
afterEach(() => {
  vi.unstubAllEnvs();
});

describe("formatTimestamp", () => {
  it("shows a UTC instant as the clock time in a pinned display zone", () => {
    const instant = Temporal.Instant.from("2026-11-23T23:30:00Z");

    expect(formatTimestamp(instant, "Europe/Madrid", "en", TIME)).toBe("00:30");
    expect(formatTimestamp(instant, "America/New_York", "en", TIME)).toBe(
      "18:30",
    );
    expect(
      formatTimestamp(instant, "Europe/Madrid", "en", { dateStyle: "short" }),
    ).toBe("11/24/26");
  });

  it("skips the missing hour on the day clocks go forward", () => {
    const before = Temporal.Instant.from("2026-03-29T00:30:00Z");
    const after = Temporal.Instant.from("2026-03-29T01:30:00Z");

    expect(formatTimestamp(before, "Europe/Madrid", "en", TIME)).toBe("01:30");
    expect(formatTimestamp(after, "Europe/Madrid", "en", TIME)).toBe("03:30");
  });

  it("shows the repeated hour twice on the day clocks go back", () => {
    const first = Temporal.Instant.from("2026-10-25T00:30:00Z");
    const second = Temporal.Instant.from("2026-10-25T01:30:00Z");

    expect(formatTimestamp(first, "Europe/Madrid", "en", TIME)).toBe("02:30");
    expect(formatTimestamp(second, "Europe/Madrid", "en", TIME)).toBe("02:30");
  });

  it("follows the device when no display zone is set", () => {
    const instant = Temporal.Instant.from("2026-11-23T23:30:00Z");

    vi.stubEnv("TZ", "Asia/Tokyo");
    expect(formatTimestamp(instant, null, "en", TIME)).toBe("08:30");

    vi.stubEnv("TZ", "America/Argentina/Buenos_Aires");
    expect(formatTimestamp(instant, null, "en", TIME)).toBe("20:30");
  });
});
