# Subtrack

Local, single-user subscription tracker. Also a learning project: one React frontend talks to **two interchangeable backends** (FastAPI and Spring Boot) that implement the same API contract and must behave identically.

## Source documents

Read these before changing behavior or design. Don't load them all up front; open the section you need.

- `docs/srs.md` — what the app does: requirements (FR-x), business rules (BR-x), non-functional requirements (NFR-x)
- `docs/sdd.md` — how it's built: architecture, schema, API, backend/frontend design, testing, versions, CI
- `docs/standards.md` — development standards (principles, abstractions, dates/money, TS/React, Tailwind/shadcn, backends, security, testing, monorepo)
- `docs/adr/` — decisions that changed the above

If a request conflicts with these docs, say so and ask before proceeding. A change in behavior updates the SRS or SDD in the same PR; a change to a decision gets an ADR.

## Stack and versions

| Area | Choice |
| --- | --- |
| Frontend | React 19.x + Vite 8.x + TypeScript 6.0, TanStack Router/Query/Form, shadcn/ui on Base UI, Tailwind CSS 4, react-i18next (en, es), Temporal (+ polyfill) |
| Backend A | Python 3.14, FastAPI, Pydantic, SQLAlchemy, uv |
| Backend B | Java 25, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Gradle (Kotlin DSL) |
| Database | SQLite file on a Docker volume; schema owned by Flyway SQL migrations |
| Node | 26 LTS, pnpm workspaces (frontend, e2e, contract) |
| Tests | Playwright (UI + shared API suite), Schemathesis, pytest, JUnit 5, Vitest |
| Runtime | Docker Compose; profiles `fastapi` and `spring`, one backend at a time |
| CI | GitHub Actions: `ci.yml` (every push), `e2e.yml` (main, staging) |

Do not upgrade to TypeScript 7 or Python 3.15 unless a feature requires it. Pin versions in lockfiles.

## Repository layout

```
contract/          openapi.yaml (source of truth), cases/*.json (shared BR tests), generated types (@subtrack/contract)
db/migrations/     Flyway SQL migrations — the ONLY place the schema changes
frontend/          React + Vite app
backend-fastapi/   src/subtrack/<module>/
backend-spring/    src/main/java/dev/subtrack/<module>/
e2e/               api/ (shared API suite), ui/ (browser flows), support/ (fixtures)
docs/              srs.md, sdd.md, standards.md, adr/
```

Both backends use the same module names: `api`, `service`, `billing`, `repository`, `persistence`, `scheduler`, `security`, `clock`, `config`, `cli`.

## Commands

All commands go through the root `justfile`. Prefer these over calling tools directly.

- `just up fastapi` / `just up spring` — start the stack with one backend (http://127.0.0.1:8080, or `SUBTRACK_PORT`)
- `just down` — stop the stack (data stays on the volume)
- `just test` — all fast checks: static analysis, unit tests, API suite against both backends
- `just test-e2e` — full browser suite against both backends
- `just lint` / `just fmt` — lint and format every part
- `just gen` — regenerate types from `contract/openapi.yaml`

If a recipe is missing, add it to the `justfile` rather than documenting a one-off command.

## Non-negotiable rules

- **Contract first.** API changes start in `contract/openapi.yaml`, then both backends and the frontend, ideally in one PR. Never let the two backends diverge; a behavior difference is a bug.
- **Schema only via migrations.** New `V<n>__description.sql` in `db/migrations/`. Backends never create or alter tables (Spring `ddl-auto=validate`, no `create_all`).
- **Dates.** Stored as ISO text: dates `YYYY-MM-DD`, timestamps UTC with milliseconds and `Z`. Billing dates are plain dates. In TypeScript use Temporal only, never `Date` (except inside `DatePicker`).
- **Money.** Integer cents everywhere. Never floats. Round derived values once, at the end (BR-22, BR-26).
- **Time is injected.** No direct `now()` / `LocalDate.now()` / `Temporal.Now` outside the `clock` module.
- **External services behind interfaces** we own (logo provider, API client, clock, repositories). No vendor types outside adapters.
- **No user-facing strings in code.** Everything through translation files (`en` and `es` together). The backend sends codes and parameters, never display text.
- **Security.** Never log, return or store secrets in readable form. Every endpoint is protected by default; exceptions are `/health`, `/auth/status`, `/auth/unlock`.
- **Tests at the highest useful level:** end to end > integration (shared API suite) > unit. Every BR has a case. No test reads the real clock.

## Workflow

- Small, focused changes. Run `just lint` and `just test` before saying a task is done.
- Commits use Conventional Commits with an area scope: `feat(frontend):`, `fix(spring):`, `feat(fastapi):`, `chore(contract):`, `chore(db):`, `docs:`.
- When implementing a requirement, reference its ID (FR-x / BR-x) in the test name or commit message.
- Path-specific rules live in `.claude/rules/` and load when you work on matching files.
