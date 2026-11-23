export type PlainDateFormatOptions = Pick<
  Intl.DateTimeFormatOptions,
  "dateStyle" | "weekday" | "era" | "year" | "month" | "day"
>;

/** A calendar date as it is, never converted to or from any time zone. */
export function formatPlainDate(
  date: Temporal.PlainDate,
  locale: string,
  options: PlainDateFormatOptions = { dateStyle: "medium" },
): string {
  return date.toLocaleString(locale, options);
}
