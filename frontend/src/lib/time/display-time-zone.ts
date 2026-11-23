import { deviceTimeZone } from "./device-time-zone";

/** An IANA name, or null to follow the browser. */
export type DisplayTimeZone = string | null;

export function resolveDisplayTimeZone(zone: DisplayTimeZone): string {
  return zone ?? deviceTimeZone();
}
