# Subtrack end-to-end tests

Playwright suites that run against the full Compose stack, once per backend profile. The stack must be running; the `just` recipes start a fresh test-mode stack, run the suite and remove the stack again.

| Folder     | Project                     | What                                                                                          | When                           |
| ---------- | --------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------ |
| `api/`     | `api`                       | Shared API suite over HTTP against `/api/v1`; every business rule gets a case here            | every push (`ci.yml`)          |
| `cross/`   | `cross-write`, `cross-read` | Data written through one backend reads back identically through the other (`just test-cross`) | every push (`ci.yml`)          |
| `ui/`      | `ui`                        | Browser flows in Chromium, in English plus one Spanish smoke flow                             | `main` and staging (`e2e.yml`) |
| `docs/`    | `docs`                      | The API docs page (dev profile) lists every operation; `/api` reaches the backend             | by hand (`just test-docs`)     |
| `support/` |                             | Environment, typed API client, reset fixture, clock helper, problem assertions                |                                |

```sh
just e2e-setup            # once: install Chromium
just test-api fastapi     # API suite against one backend
just test-cross           # write with one backend, read with the other, both directions
just test-e2e             # browser suite against both backends
just test-docs            # API docs check against a dev-profile stack (default fastapi)
```

To run against a stack you started yourself (`just up spring`):

```sh
EXPECTED_BACKEND=spring pnpm exec playwright test --project api
```

Environment: `BASE_URL` (default `http://127.0.0.1:${SUBTRACK_PORT:-8080}`), `DOCS_BASE_URL` (default `http://127.0.0.1:${SUBTRACK_DOCS_PORT:-8081}`), `EXPECTED_BACKEND` (`fastapi` or `spring`; unset accepts either). Only `support/env.ts` reads them.

API tests import `test` and `expect` from `support/fixtures.ts`. Each test then gets a typed `api` client (`openapi-fetch` over `@subtrack/contract`) and starts from a fresh database: the fixture calls `POST /admin/reset`, which also unpins the clock. Set the date with `setClock(api, Temporal.Instant)` from `support/clock.ts`.

Contract checks: `just test-api <profile>` runs Schemathesis after the API suite on the same stack (`just test-contract <profile>` runs it alone). `schemathesis/schemathesis.toml` lists the operations both backends implement; add yours when a slice implements them.

Tests run one at a time (`workers: 1`), since they share the stack's single database. `cross-read` turns off the automatic reset (`resetDatabase: false`) to read what `cross-write` left, and the two pass data through the file in `CROSS_STATE`.

Rules: selectors use roles and labels (enforced by `playwright/no-raw-locators`), each test starts from a fresh database, and no test reads the real clock.
