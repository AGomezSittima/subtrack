# Subtrack

A local, single-user subscription tracker. It's also a learning project: one React frontend talks to two interchangeable backends (FastAPI and Spring Boot). Both implement the same OpenAPI contract and share one SQLite database.

- Requirements: [`docs/srs.md`](docs/srs.md)
- Design: [`docs/sdd.md`](docs/sdd.md)
- Development standards: [`docs/standards.md`](docs/standards.md)
- Decisions: [`docs/adr/`](docs/adr/)

## Prerequisites

| Tool             | Version           | Notes                                             |
| ---------------- | ----------------- | ------------------------------------------------- |
| Docker + Compose | Compose v2+       | runs the stack                                    |
| Node.js          | 26 LTS (`.nvmrc`) |                                                   |
| pnpm             | 12.15.0           | pinned in `package.json` (`packageManager`)       |
| uv               | 0.11+             | installs Python 3.14 itself                       |
| JDK              | 25 (Temurin)      | Gradle runs through `./gradlew`; no global Gradle |
| just             | 1.59+             | `uv tool install rust-just`                       |

First time:

```sh
pnpm install          # also installs the git hooks (lefthook)
(cd backend-fastapi && uv sync)
just e2e-setup        # Playwright Chromium for the UI suite
```

## Commands

| Command                              | What it does                                                        |
| ------------------------------------ | ------------------------------------------------------------------- |
| `just up fastapi` / `just up spring` | Start the stack with one backend at http://127.0.0.1:8080           |
| `just down`                          | Stop the stack; data stays on the `subs-data` volume                |
| `just lint` / `just fmt`             | Lint and format every part                                          |
| `just test`                          | Static checks, unit tests, then the API suite against both backends |
| `just test-e2e`                      | Browser suite against both backends                                 |
| `just gen`                           | Regenerate types from `contract/openapi.yaml`                       |

`just test` and `just test-e2e` start their own stack under the Compose project `subtrack-test`, which has its own volume, and remove it afterwards. Stop your own stack first (`just down`), because both use port 8080.

## Layout

| Folder               | Contents                                                                                             |
| -------------------- | ---------------------------------------------------------------------------------------------------- |
| `contract/`          | `openapi.yaml` (source of truth), shared business-rule cases, generated types (`@subtrack/contract`) |
| `db/migrations/`     | Flyway SQL migrations, the only place the schema changes                                             |
| `frontend/`          | React + Vite app, served by nginx                                                                    |
| `backend-fastapi/`   | Python 3.14 / FastAPI backend                                                                        |
| `backend-spring/`    | Java 25 / Spring Boot backend                                                                        |
| `e2e/`               | Playwright: `api/` (shared API suite), `ui/` (browser flows), `support/`                             |
| `docs/`              | SRS, SDD, standards, ADRs                                                                            |
| `.github/workflows/` | `ci.yml` (every push), `e2e.yml` (main, staging)                                                     |

Each folder has its own README with details.
