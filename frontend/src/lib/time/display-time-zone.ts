import { deviceTimeZone } from "./device-time-zone";

/** `device` follows the browser; anything else is an IANA name. */
export type DisplayTimeZone = "device" | (string & {});

export function resolveDisplayTimeZone(zone: DisplayTimeZone): string {
  return zone === "device" ? deviceTimeZone() : zone;
}
