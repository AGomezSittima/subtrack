import { describe, expect, it } from "vitest";

import { optionalText } from "./optional-text";

describe("optionalText", () => {
  it.each([null, "", " ", "\t\n\r", " \r\n\t "])(
    "sends null for the empty value %j",
    (value) => {
      expect(optionalText(value)).toBeNull();
    },
  );

  it.each([
    [" Visa ··4417 ", "Visa ··4417"],
    ["\tShared\nwith family\n", "Shared\nwith family"],
    [" ", " "],
  ])("sends %j trimmed as %j", (value, expected) => {
    expect(optionalText(value)).toBe(expected);
  });
});
