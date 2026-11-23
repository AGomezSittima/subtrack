import { trimText } from "./trim-text";

export function optionalText(value: string | null): string | null {
  const trimmed = value === null ? "" : trimText(value);
  return trimmed === "" ? null : trimmed;
}
