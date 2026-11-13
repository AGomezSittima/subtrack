# Subtrack end-to-end tests

Playwright suites that run against the full Compose stack, once per backend profile. The stack must be running; the `just` recipes start a fresh test-mode stack, run the suite and remove the stack again.

| Folder     | Project | What                                                                               | When                           |
| ---------- | ------- | ---------------------------------------------------------------------------------- | ------------------------------ |
| `api/`     | `api`   | Shared API suite over HTTP against `/api/v1`; every business rule gets a case here | every push (`ci.yml`)          |
| `ui/`      | `ui`    | Browser flows in Chromium, in English plus one Spanish smoke flow                  | `main` and staging (`e2e.yml`) |
| `support/` |         | Environment and fixtures shared by both                                            |                                |

```sh
just e2e-setup            # once: install Chromium
just test-api fastapi     # API suite against one backend
just test-e2e             # browser suite against both backends
```

To run against a stack you started yourself (`just up spring`):

```sh
EXPECTED_BACKEND=spring pnpm exec playwright test --project api
```

Environment: `BASE_URL` (default `http://127.0.0.1:8080`), `EXPECTED_BACKEND` (`fastapi` or `spring`; unset accepts either). Only `support/env.ts` reads them.

Rules: selectors use roles and labels (enforced by `playwright/no-raw-locators`), each test starts from a fresh database (`POST /admin/reset`, once it exists), and no test reads the real clock.
