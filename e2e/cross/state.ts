import { readFileSync, writeFileSync } from "node:fs";
import type { components } from "@subtrack/contract";

import { env } from "../support/env";

export interface CrossState {
  writer: components["schemas"]["Health"]["backend"];
  catalog: components["schemas"]["CatalogServiceList"];
  visible: components["schemas"]["CatalogServiceList"];
}

export function saveState(state: CrossState): void {
  writeFileSync(env.crossState, JSON.stringify(state));
}

export function loadState(): CrossState {
  return JSON.parse(readFileSync(env.crossState, "utf8")) as CrossState;
}

export function present<T>(value: T | undefined): T {
  if (value === undefined) {
    throw new Error("the response had no body");
  }
  return value;
}
