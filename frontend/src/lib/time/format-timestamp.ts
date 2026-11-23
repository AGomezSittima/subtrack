import {
  type DisplayTimeZone,
  resolveDisplayTimeZone,
} from "./display-time-zone";

export type TimestampFormatOptions = Omit<
  Intl.DateTimeFormatOptions,
  "timeZone"
>;

/** A UTC instant as a clock time in the display time zone. */
export function formatTimestamp(
  instant: Temporal.Instant,
  displayZone: DisplayTimeZone,
  locale: string,
  options: TimestampFormatOptions = { dateStyle: "medium", timeStyle: "short" },
): string {
  return instant
    .toZonedDateTimeISO(resolveDisplayTimeZone(displayZone))
    .toLocaleString(locale, options);
}
