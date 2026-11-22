import { type APIResponse, expect } from "@playwright/test";
import type { components } from "@subtrack/contract";

export type ProblemCode =
  components["schemas"]["ProblemType"] extends `urn:subtrack:problem:${infer Code}`
    ? Code
    : never;
export type ValidationIssue = components["schemas"]["ValidationIssue"];

const PROBLEM_KEYS = ["type", "title", "status", "detail", "errors"];

export async function expectProblem(
  response: APIResponse,
  code: ProblemCode,
  status: number,
): Promise<ValidationIssue[]> {
  expect(response.status()).toBe(status);
  expect(response.headers()["content-type"]).toBe("application/problem+json");
  const body: unknown = await response.json();
  expect(body).toMatchObject({
    type: `urn:subtrack:problem:${code}`,
    title: expect.any(String),
    status,
  });
  expect(PROBLEM_KEYS).toEqual(
    expect.arrayContaining(Object.keys(body as object)),
  );
  const { errors } = body as { errors?: ValidationIssue[] };
  return errors ?? [];
}
