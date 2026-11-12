---
paths:
  - "e2e/**/*"
  - "backend-fastapi/tests/**/*"
  - "backend-spring/src/test/**/*"
  - "frontend/**/*.test.{ts,tsx}"
---

# Testing rules

Full detail: `docs/sdd.md` (Testing and repository layout) and `docs/standards.md` (Testing).

## Priority
End to end > integration > unit, on a base of static analysis. Write the highest-level test that catches the bug at a reasonable speed; go lower only when the higher level would be slow, flaky or unable to reach the case.

## Levels
- **E2E** (`e2e/ui/`): Playwright browser flows against the full Compose stack, once per backend profile. Runs on `main` and staging.
- **Integration** (`e2e/api/`): the shared API suite, written once in TypeScript and run against each backend on a fresh database. Every BR has a case here. Runs on every push.
- **Unit**: billing core from `contract/cases/*.json` (pytest, JUnit 5 parameterized); frontend hooks or components with real logic (Vitest + Testing Library, API mocked at the network layer with MSW).

## Rules
- No test reads the real clock. Set the date with `POST /admin/clock` or inject a fixed `Clock`.
- Each test starts from a fresh database (`POST /admin/reset`) and creates its own data. No test depends on another.
- Test-mode endpoints exist only with the `docker-compose.test.yml` overlay.
- Selectors use roles and labels (`getByRole`, `getByLabel`), never CSS classes.
- E2E runs in English by default, plus one smoke flow in Spanish.
- A bug fix starts with a failing test at the highest level that can reproduce it.
- Names describe behavior and reference the rule when there is one: `BR-2 clamps the 31st to the end of a short month`.
