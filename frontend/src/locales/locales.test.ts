import { describe, expect, it } from "vitest";

import en from "./en/translation.json";
import es from "./es/translation.json";

function keyPaths(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix === "" ? key : `${prefix}.${key}`),
  );
}

describe("translations", () => {
  it("has the same keys in English and Spanish", () => {
    expect(keyPaths(es).sort()).toEqual(keyPaths(en).sort());
  });
});
