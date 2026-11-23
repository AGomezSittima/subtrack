import { describe, expect, it } from "vitest";

import { trimText } from "./trim-text";

describe("trimText", () => {
  it.each([
    [" Netflix", "Netflix"],
    ["Netflix\t", "Netflix"],
    ["\nNetflix\r", "Netflix"],
    [" \t\r\n Family plan \n\r\t ", "Family plan"],
    [" \t\n\r", ""],
    ["", ""],
  ])("trims %j to %j", (value, expected) => {
    expect(trimText(value)).toBe(expected);
  });

  it.each([
    "Family plan",
    "Shared\nwith family",
    "Line\r\nbreak",
    " Visa ",
    "　﻿ ",
  ])(
    "keeps %j, which has no space, tab or line break at either end",
    (value) => {
      expect(trimText(value)).toBe(value);
    },
  );
});
