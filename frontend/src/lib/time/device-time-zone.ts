/** The browser's IANA time zone. */
export function deviceTimeZone(): string {
  return Temporal.Now.timeZoneId();
}
