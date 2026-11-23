import { describe, expect, it } from "vitest";

import { optionalText } from "./optional-text";

describe("optionalText", () => {
  it.each(["", " ", "\t\n\r", "\u00a0", "\u2028\u3000\u205f"])(
    "sends null for the blank value %j",
    (value) => {
      expect(optionalText(value)).toBeNull();
    },
  );

  it.each(["Family plan", " a ", "\ufeff", "Visa ··4417"])(
    "keeps %j as typed",
    (value) => {
      expect(optionalText(value)).toBe(value);
    },
  );
});
