import type { components } from "@subtrack/contract";

export type Backend = components["schemas"]["Health"]["backend"];

export const backends = [
  "fastapi",
  "spring",
] as const satisfies readonly Backend[];

function expectedBackend(value: string | undefined): Backend | undefined {
  if (value === undefined || value === "") {
    return undefined;
  }
  const backend = backends.find((candidate) => candidate === value);
  if (backend === undefined) {
    throw new Error(
      `EXPECTED_BACKEND must be one of ${backends.join(", ")}, got "${value}"`,
    );
  }
  return backend;
}

/** The only place the test suites read the environment. */
export const env = {
  baseUrl: process.env.BASE_URL ?? "http://127.0.0.1:8080",
  /** Set by `just test-api <profile>` / `just test-e2e-profile <profile>`; unset accepts either. */
  expectedBackend: expectedBackend(process.env.EXPECTED_BACKEND),
  ci: process.env.CI === "true",
};
