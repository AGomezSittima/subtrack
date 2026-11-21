# @subtrack/contract

The API contract shared by both backends and the frontend.

| Path                        | Contents                                                                |
| --------------------------- | ----------------------------------------------------------------------- |
| `openapi.yaml`              | OpenAPI 3.1 contract, the source of truth for `/api/v1`                 |
| `generated/openapi.d.ts`    | TypeScript types generated from it. Don't edit by hand; run `just gen`  |
| `cases/`                    | Shared business-rule test cases loaded by both backend test suites      |
| `redocly.yaml`              | Lint rules for `openapi.yaml` (`recommended-strict`: any finding fails) |
| `.redocly.lint-ignore.yaml` | Findings ignored on purpose, each with its reason                       |
| `redocly.gen.yaml`          | Minimal structural checks used by `just gen`                            |
| `scripts/`                  | `validate-cases.ts`: checks `cases/` against `cases/schema/`            |

Workflow for an API change: edit `openapi.yaml`, run `just gen` and `just lint` (Redocly lint and case-file validation run in the contract's `lint` script), then update both backends and the frontend in the same PR. CI fails if the generated file is stale.

Consumers import types with `import type { paths, components } from "@subtrack/contract"`.
