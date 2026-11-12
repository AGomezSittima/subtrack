---
paths:
  - "contract/**/*"
  - "db/**/*"
---

# Contract and database rules

Full detail: `docs/sdd.md` (API contract, API evolution, Database schema).

## Contract (`contract/openapi.yaml`)
- It is the source of truth. Change it first, then regenerate types (`just gen`), then update both backends and the frontend.
- REST + JSON under `/api/v1`. Money as integer cents, dates as `YYYY-MM-DD`, timestamps as UTC ISO with `Z`.
- Errors are RFC 9457 problem JSON (`type`, `title`, `status`, `detail`, plus `errors[]` for validation). The backend sends codes and parameters, never display text.
- Additive changes need no new version. Breaking changes update contract, both backends and frontend together. A `/api/v2` exists only if an independent consumer appears.
- Never hand-edit generated files.

## Shared test cases (`contract/cases/*.json`)
- Every BR table in `docs/srs.md` has a case file (input → expected output) loaded by both backend test suites.
- Adding or changing a business rule means adding or changing its cases first.

## Migrations (`db/migrations/`)
- The only place the schema changes. Flyway naming: `V<n>__short_description.sql`. Never edit an applied migration; add a new one.
- Every table has `id INTEGER PRIMARY KEY`; `created_at` / `updated_at` where rows change.
- Money columns are `INTEGER` cents. Date and timestamp columns are `TEXT` with `CHECK` constraints on their format.
- Use `CHECK` constraints for enums (status, interval unit, etc.) and foreign keys with explicit `ON DELETE` behavior.
- The catalogue seed lives in its own migration; catalogue changes are new migrations.

## Export format
- Export files carry `format_version`, independent of API and schema versions. Import converts older versions and rejects unknown ones.
- Exports never include the passcode hash, sessions or test-mode state; import never overwrites them.
