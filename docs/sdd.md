# Subtrack — Software Design Description (SDD)

Nov 10, 2026 · @Alvaro

How the system described in the SRS is built. Requirement IDs (FR-x, BR-x, NFR-x) refer to the SRS.

Requirements: Subtrack — Software Requirements Specification (SRS)

Coding conventions: Subtrack — Development Standards

## Design overview

One React + Vite frontend, two interchangeable backends behind one OpenAPI contract, and a SQLite file on a Docker volume, all run with Docker Compose.

| Concern | Decision | Satisfies |
| --- | --- | --- |
| Frontend | React + Vite + TypeScript, static build served by nginx | NFR-1, NFR-5 |
| Backend A | Python, FastAPI, SQLAlchemy 2, Pydantic | NFR-6 |
| Backend B | Java, Spring Boot, Spring Data JPA / Hibernate | NFR-6 |
| API | Contract-first OpenAPI 3.1 in `contract/openapi.yaml` | NFR-6 |
| Database | SQLite file `subs.db` on a named volume | NFR-2, NFR-3 |
| Schema | Plain SQL migrations applied by a Flyway one-shot container | NFR-6 |
| Deployment | Docker Compose, one profile per backend | NFR-1, NFR-4 |
| Scheduling | In-process daily job in the active backend, with catch-up | BR-18 to BR-20 |
| Logos | Logo.dev image URLs by domain, loaded by the browser | FR-2 |
| Business rules | Shared JSON test cases run by both backends | NFR-6 |
| UI components | shadcn/ui on Base UI, owned in the repo, with a custom Subtrack theme | NFR-5 |

### Versions

Chosen on 2026-11-12: latest stable versions that are compatible with each other (NFR-10), pinned in lockfiles and updated by hand while implementing.

| Area | Version | Notes |
| --- | --- | --- |
| Node.js | 26 LTS |  |
| TypeScript | 6.0 | TS 7.0 has no compiler API yet, so typescript-eslint's type-checked rules can't use it. Revisit only if a 7.x feature becomes necessary. |
| React | 19.x, latest minor |  |
| Vite | 8.x |  |
| Tailwind CSS, shadcn/ui, TanStack libraries | Latest at scaffold time |  |
| Python | 3.14 | Chosen over 3.15 for library maturity; no 3.15 feature is needed. |
| FastAPI, Pydantic, SQLAlchemy, argon2-cffi | Latest supporting 3.14 |  |
| Java | 25 LTS |  |
| Spring Boot | 4.1.1 | Hibernate, community SQLite dialect and Flyway versions managed by Spring's BOM where possible |
| sqlite-jdbc | Latest (xerial) |  |
| Flyway, nginx, Playwright images | Latest, pinned by tag |  |

### CI

GitHub Actions, two workflows:

- **`ci.yml`** on every push and pull request: format, lint and type checks for the parts whose paths changed, then the API suite against both Compose profiles (fastapi, spring) on GitHub-hosted Linux runners.
  - A branch push compares the whole branch with main to decide which parts changed. On main, every part runs. Changes to `ci.yml` or the `justfile` re-run every part.
  - A pull request from a branch of this repository is already covered by the push run of its head commit, so its `pull_request` run is skipped. Pull requests from forks get a full run.
  - If the API suite fails, the job log shows the test stack's container status and logs.
- **`e2e.yml`** on pushes to `main` and the staging branch: the full Playwright browser suite against both profiles.

Dependencies are updated manually while implementing; no update bot.

## Architecture and deployment

Only the web container publishes a port, on 127.0.0.1. SQLite is a file, not a server, so there is no database container and nothing to expose: the file lives on a named volume mounted only into the migration job and the active backend.

&#91;embedded content: Compose stack · 1 published port, 1 volume\]

The browser reaches only nginx; the active backend and the migration job share the SQLite volume inside the stack.

### Services

| Service | Profiles | Role | Published port |
| --- | --- | --- | --- |
| web | fastapi, spring | nginx serving the React + Vite build, proxies `/api` to `api` | 127.0.0.1:8080 (`SUBTRACK_PORT`) |
| migrate | fastapi, spring | Flyway one-shot job applying `db/migrations/*.sql`, then exits | none |
| api-fastapi | fastapi | FastAPI backend, network alias `api` | none |
| api-spring | spring | Spring Boot backend, network alias `api` | none |

Both backends answer on the alias `api`, so the nginx config never changes. nginx resolves `api` at request time (`resolver 127.0.0.11` + a variable in `proxy_pass`); otherwise it refuses to start while the backend is still booting.

### docker-compose.yml (sketch)

The real file is `docker-compose.yml` at the repository root; it also adds healthchecks (so `docker compose up --wait` works) and `restart: unless-stopped`.

```yaml
services:
  migrate:
    image: flyway/flyway:13.12.2
    profiles: [fastapi, spring]
    # Flyway runs as root; the data folder is then handed to UID 10001, the non-root
    # user both backend images run as, so they can write the database and its WAL files.
    entrypoint: ["sh", "-c"]
    command:
      - flyway -url=jdbc:sqlite:/data/subs.db -locations=filesystem:/flyway/sql migrate && chown -R 10001:10001 /data
    volumes:
      - subs-data:/data
      - ./db/migrations:/flyway/sql:ro

  api-fastapi:
    build: ./backend-fastapi
    profiles: [fastapi]
    environment: { DB_PATH: /data/subs.db, TZ: Europe/Madrid }
    volumes: [subs-data:/data]
    depends_on:
      migrate: { condition: service_completed_successfully }
    networks:
      default: { aliases: [api] }

  api-spring:
    build: ./backend-spring
    profiles: [spring]
    environment: { DB_PATH: /data/subs.db, TZ: Europe/Madrid }
    volumes: [subs-data:/data]
    depends_on:
      migrate: { condition: service_completed_successfully }
    networks:
      default: { aliases: [api] }

  web:
    # Built from the repository root: the frontend build needs the pnpm workspace
    # (lockfile and the @subtrack/contract types).
    build: { context: ., dockerfile: frontend/Dockerfile }
    profiles: [fastapi, spring]
    ports: ["127.0.0.1:${SUBTRACK_PORT:-8080}:80"]

volumes:
  subs-data:
```

Both backends listen on port 8000 inside the network and serve everything under `/api/v1`. The host port is optional configuration: `SUBTRACK_PORT` (default 8080) from the shell or a root `.env` (`.env.example` documents it). The test overlay `docker-compose.test.yml` only sets `SUBTRACK_TEST_MODE=true` on both backends.

### Everyday use

- Start: `docker compose --profile fastapi up -d` (or `--profile spring`)
- Stop: `docker compose --profile fastapi down`; data stays on the volume
- Switch backend: stop one profile, start the other; both read the same database
- Backup: the export endpoint, or copy `subs.db` out of the volume while stopped

## Database schema

Six SQLite tables. Money is `INTEGER` cents, dates are ISO `TEXT` (`YYYY-MM-DD`), timestamps are UTC ISO `TEXT`. Every table has `id INTEGER PRIMARY KEY`, plus `created_at` and `updated_at` where rows change.

### catalog\_service

| Column | Type | Notes |
| --- | --- | --- |
| name | text, unique | "Spotify" |
| domain | text | "spotify.com", key for the logo |
| category | text | Music, Video, VPN, Cloud, AI, Software, Domains, Other |
| default\_plans | JSON text | `[{"plan":"Individual","price_cents":1199,"interval":"1 month"}]` |

### subscription

| Column | Type | Notes |
| --- | --- | --- |
| catalog\_service\_id | int, nullable | FK, null for free-text services |
| name, category | text |  |
| plan\_name | text, nullable |  |
| price\_cents | int | current price, mirrors the latest price\_history row |
| interval\_count | int, CHECK ≥ 1 |  |
| interval\_unit | text, CHECK in (day, week, month, year) |  |
| anchor\_date | date | first billing date |
| status | text, CHECK in (trial, active, paused, cancelled) |  |
| trial\_end\_date, cancelled\_at, paused\_at | date, nullable |  |
| payment\_method | text, nullable | free label, e.g. "Visa ··4417" |
| auto\_confirm | int 0/1 | default 0 |
| reminder\_days | int, nullable | null = global default, -1 = off |
| website, cancel\_url, notes | text, nullable |  |
| ends\_on | date, nullable | last day of access after cancelling |
| archived\_at | timestamp, nullable | set = archived |

### price\_history

| Column | Type | Notes |
| --- | --- | --- |
| subscription\_id | int | FK, ON DELETE CASCADE |
| price\_cents | int |  |
| effective\_from | date | UNIQUE (subscription\_id, effective\_from) |

### payment

| Column | Type | Notes |
| --- | --- | --- |
| subscription\_id | int | FK, ON DELETE CASCADE |
| due\_date | date |  |
| expected\_cents | int | price in force on due\_date |
| paid\_cents | int, nullable |  |
| paid\_date | date, nullable |  |
| status | text, CHECK in (expected, paid, skipped, failed) |  |
| source | text, CHECK in (scheduled, backfill, manual) |  |
| note | text, nullable |  |

A partial unique index on (subscription\_id, due\_date) WHERE source != 'manual' makes payment generation idempotent.

### notification

| Column | Type | Notes |
| --- | --- | --- |
| type | text | renewal, trial\_ending, yearly\_renewal, overdue, ending |
| subscription\_id, payment\_id | int, nullable | FK, ON DELETE SET NULL |
| severity | text |  |
| trigger\_date | date | the day it was due to fire |
| dedupe\_key | text, UNIQUE | e.g. `renewal:12:2026-12-01` |
| read\_at | timestamp, nullable |  |
| params | JSON text | e.g. {name, date, amount\_cents}; the frontend renders the text in the user's language |

### setting

Key-value table: `default_reminder_days` (3), `trial_reminder_days` (3), `yearly_reminder_days` (14), `payment_horizon_days` (60), `logo_dev_token`, `last_scheduler_run`.

Also `ending_reminder_days` (7) and `locale` (`en` or `es`; empty = follow the browser).

Appearance and lock keys: `theme` (system, light, dark), `font_size` (sm, md, lg), `reduced_motion` (system, always), `week_start` (1 = Monday … 7), `passcode_hash` (Argon2id in PHC string format; empty = no passcode), `lock_policy` (on\_open, idle\_5, idle\_15, idle\_30, idle\_60, manual), `failed_attempts`, `retry_after`.

### session

| Column | Type | Notes |
| --- | --- | --- |
| token\_hash | text, unique | SHA-256 of the random session token; the token itself is only in the cookie |
| created\_at | timestamp |  |
| last\_activity\_at | timestamp | updated only by user-driven requests |

Sessions live in the database, so switching backend profile keeps you unlocked. Removing the passcode or locking deletes all rows.

### Migrations and ownership

- `db/migrations/V1__init.sql` creates the schema; `V2__catalog_seed.sql` inserts the initial catalogue; later changes are new `Vn__` files.
- Only the Flyway `migrate` container changes the schema. Spring runs with Flyway disabled and `ddl-auto=validate`; FastAPI never calls `create_all`. A mismatch fails at startup.
- Every connection sets `PRAGMA foreign_keys = ON` (off by default in SQLite), `journal_mode = WAL` and a `busy_timeout`.
- Spring needs the `sqlite-jdbc` driver and the Hibernate community SQLite dialect.

### Date and timestamp storage

Both backends write the same ISO text (NFR-9); neither relies on driver defaults.

| Kind | Stored as | Example |
| --- | --- | --- |
| Date | `YYYY-MM-DD` | `2026-12-01` |
| Timestamp | UTC, millisecond precision, `Z` suffix | `2026-11-10T15:18:00.000Z` |

- **Database**: `CHECK` constraints on every date and timestamp column (e.g. `CHECK (due_date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]')`), so a wrong format fails on write instead of corrupting data.
- **Spring**: JPA `AttributeConverter`s for `LocalDate` and `Instant` that read and write these strings. The SQLite JDBC driver's default date storage (epoch milliseconds) is never used.
- **FastAPI**: SQLAlchemy's `Date` already writes `YYYY-MM-DD`; timestamps use a small custom column type (`TypeDecorator`) that writes the format above, since the default is not ISO with `Z`.
- **Test**: the integration suite has a cross-backend check: create data through one backend, restart on the other profile, read it back identically.

## API contract

REST + JSON under `/api/v1`, written first in `contract/openapi.yaml`. Both backends implement it exactly; the frontend only knows the contract. Money is integer cents, dates are `YYYY-MM-DD`. Errors use RFC 9457 problem JSON (`type`, `title`, `status`, `detail`, plus `errors[]` for validation).

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /catalog | Catalogue services, `?q=` search |
| GET | /subscriptions | List, `?status=&category=&sort=` |
| POST | /subscriptions | Create; `backfill: true` generates past payments as paid (FR-27) |
| GET | /subscriptions/{id} | Detail incl. next renewal, monthly equivalent, payment count and total paid (for the delete warning) |
| PATCH | /subscriptions/{id} | Edit fields except price |
| DELETE | /subscriptions/{id} | Delete with its payments and history |
| POST | /subscriptions/{id}/status | `{status, date}`: pause, resume, cancel (optional ends\_on), activate |
| GET | /subscriptions/{id}/prices | Price history |
| POST | /subscriptions/{id}/prices | `{price_cents, effective_from}` |
| GET | /payments | `?subscription_id=&status=&from=&to=` |
| POST | /payments | Manual one-off payment |
| POST | /payments/{id}/confirm | `{paid_cents?, paid_date?, note?}` |
| POST | /payments/{id}/skip | Mark skipped |
| POST | /payments/{id}/fail | Mark failed |
| PATCH | /payments/{id} | Edit note or amounts |
| GET | /notifications | `?unread=true`, newest first |
| GET | /notifications/unread-count | Badge count |
| POST | /notifications/{id}/read | Mark one read |
| POST | /notifications/read-all | Mark all read |
| GET | /stats/summary | Monthly and yearly totals, active count |
| GET | /stats/by-category | Monthly equivalent per category |
| GET | /stats/by-payment-method | Monthly equivalent per method |
| GET | /stats/upcoming | `?days=30`, payments + total |
| GET | /stats/monthly | `?from=&to=`: actual vs expected per month |
| GET | /settings | All settings |
| PUT | /settings | Update settings |
| POST | /admin/run-scheduler | Run the daily job now (dev and tests) |
| GET | /export | `?format=json` or `csv` |
| POST | /import | JSON from /export |
| GET | /health | `{status, backend: "fastapi" or "spring", version, last_scheduler_run}` (`last_scheduler_run` is null until the job has run) |
| POST | /admin/clock | Test mode only: set today's date; 404 otherwise |
| POST | /subscriptions/{id}/archive | Archive (FR-28); /unarchive restores it; GET /subscriptions?archived=true lists them |
| POST | /admin/reset | Test mode only: empty all tables and re-seed the catalogue; 404 otherwise |
| GET | /auth/status | {passcode\_enabled, unlocked, lock\_policy, retry\_after}; no other data |
| POST | /auth/unlock | {passcode}; sets the session cookie, or 401 / 429 with retry\_after |
| POST | /auth/lock | Lock now: deletes the session and clears the cookie |
| PUT | /auth/passcode | {current?, new}: set or change the passcode |
| DELETE | /auth/passcode | {current}: remove the passcode |

The `backend` field in `/health` lets the frontend show which implementation is answering.

### API evolution

The only consumer is the Subtrack frontend, shipped in the same repository and stack as the backends, so the API normally stays at v1.

- **Additive changes** (new endpoints, new optional fields or query parameters) need no version. Clients ignore fields they don't know.
- **Breaking changes** (renaming or removing a field, changing a type or meaning) change the contract, both backends and the frontend in one PR, deployed together.
- **A v2 only if an independent consumer appears** (a script, another app): `contract/openapi.v2.yaml` next to v1, `/api/v2` served alongside `/api/v1`, and versioning only in the API layer (v1 and v2 routes map to the same services; service and billing code is never versioned). v1 responses then carry `Deprecation` and `Sunset` headers until it is removed, and the API suite runs once per version.

### Export format

Export files outlive the code, so they carry their own version:

```json
{ "format_version": 1, "exported_at": "2026-11-12T18:10:00.000Z", "app_version": "0.3.0", "data": { … } }
```

- `format_version` changes only when the file structure changes, independently of the API version and the database migrations.
- Import reads every format version it knows, converting older ones step by step, and rejects unknown (newer) versions with a clear error.
- Exports never include the passcode hash, sessions or test-mode state; import never overwrites them.

## Backend design

Both backends use the same four layers and the same module names, so code can be compared file by file.

1. **API layer**: routes, request/response models, problem-JSON error handler. No logic.
2. **Service layer**: use cases (create subscription, confirm payment, change price, run scheduler), one transaction each.
3. **Billing core**: pure functions with no I/O for BR-1 to BR-5 (next dates, clamping, normalization). The shared test cases target this module directly.
4. **Repository layer**: data access for the six tables.

Time is injected everywhere (a `Clock` in Java, a `today()` provider in Python), so tests can pin "today" and the scheduler can be tested across months.

| Concern | FastAPI | Spring Boot |
| --- | --- | --- |
| Routing | `APIRouter` per resource | `@RestController` per resource |
| Validation | Pydantic models | Bean Validation on DTO records |
| Data access | SQLAlchemy 2 (ORM or Core) | Spring Data JPA / Hibernate |
| SQLite driver | stdlib `sqlite3` | `sqlite-jdbc` + community dialect |
| Errors | custom exception handlers → problem JSON | built-in `ProblemDetail` |
| Startup hook | lifespan handler | `ApplicationReadyEvent` listener |
| Daily job | APScheduler cron trigger | `@Scheduled(cron, zone)` |
| Settings | pydantic-settings, env vars | `application.yml`, env vars |
| Tests | pytest | JUnit 5 + AssertJ |

### Scheduler

- Runs once at startup, then every hour, and on `POST /admin/run-scheduler`.
- Steps in order, each in its own transaction: activate ended trials (BR-12), generate payments up to the horizon (BR-6 to BR-8), auto-confirm (BR-11), create notifications for every trigger date from `last_scheduler_run` to today (BR-13 to BR-19), then store `last_scheduler_run`.
- Idempotency comes from the database: the payment unique index and the notification `dedupe_key`. Inserts use `INSERT … ON CONFLICT DO NOTHING`.
- Backfill (FR-27) reuses the generator with a start date in the past, writing `status = paid`, `source = backfill`.
- Only one backend runs at a time (Compose profiles), so there is no need for a distributed lock.

### Privacy lock

Scope: a privacy screen against casual use of a logged-in computer, and a learning comparison of session auth in FastAPI vs Spring Security. It is not data protection; the SQLite file is unencrypted by design (SRS assumptions).

When a passcode is set, every endpoint returns 401 without a valid session, except `/health`, `/auth/status` and `/auth/unlock`. Enforcement is one place per backend: a router-level dependency in FastAPI, the Spring Security filter chain in Spring.

| Concern | Design |
| --- | --- |
| Hashing | Argon2id in PHC string format (argon2-cffi; Spring Security's Argon2 encoder). The cross-backend test sets the passcode on one backend and unlocks on the other. |
| Session | Random 256-bit token in cookie `subtrack_session`: HttpOnly, SameSite=Strict, Path=/api. Only its hash is stored. |
| Idle lock | A session expires when `now − last_activity_at` exceeds the policy. Requests sent with header `X-Subtrack-Background: 1` (notification polling) are checked but never extend it. The frontend also locks itself on the same timer, using keyboard and pointer activity. |
| Lock on open | On every app load with policy on\_open, the frontend calls `POST /auth/lock` before anything else, so each open starts locked. |
| Wrong attempts | From the 6th failure, `retry_after` = now + 2^(n−5) seconds, capped at 15 minutes; unlock returns 429 until then. Reset on success. |
| Forgotten passcode | Each backend image ships a command: `docker compose exec api-fastapi subtrack reset-passcode` (or `api-spring`). It clears the hash, counters and sessions. |
| Logging | Request bodies of `/auth/*` are never logged. |

The API suite covers: locked access returns 401 everywhere, background requests don't extend the session (using the test clock), the backoff sequence, and changing or removing the passcode.

## Frontend design

A single-page app built with React + Vite + TypeScript, compiled to static files and served by nginx. It talks only to `/api/v1` and never knows which backend answers.

| Concern | Choice |
| --- | --- |
| Routing | TanStack Router (file-based routes, Zod-validated search params, loaders prefetching through TanStack Query) |
| Server state | TanStack Query (caching, refetch after mutations) |
| API client | Types generated from `contract/openapi.yaml` (openapi-typescript + openapi-fetch) |
| Charts | Recharts |
| Forms | TanStack Form + Zod (Standard Schema, no adapter) |
| Notifications | Unread count polled every 60 s; no websockets needed for one local user |
| Dates and times | Temporal, with a polyfill loaded at entry for Safari |
| UI components | shadcn/ui on Base UI in src/components/ui; Field with TanStack Form; data table with TanStack Table; charts via shadcn chart components (Recharts) |
| Theme | Custom palette, font and radius as shadcn tokens in src/styles/global.css (:root + @theme inline) |
| Icons | Lucide (shadcn default) |
| Localization | react-i18next with en and es files in src/locales; dates, numbers and EUR amounts via Intl and Temporal toLocaleString for the active locale; notifications and API errors rendered from type + params |

### Pages

| Page | Contents | Requirements |
| --- | --- | --- |
| Dashboard | Monthly/yearly totals, next 30 days, spend-over-time chart, category breakdown | FR-19 to FR-23 |
| Subscriptions | List with filters, sort, logo, next renewal, monthly equivalent | FR-4, FR-5 |
| Subscription detail | Edit form, status actions, price history, payments | FR-1, FR-3, FR-6, FR-13 |
| New subscription | Catalogue search with prefill, backfill option | FR-1, FR-2, FR-27 |
| Payments | Due and overdue payments to confirm, skip or fail | FR-9 to FR-13 |
| Notifications | Bell with badge in the header, full list page | FR-17 |
| Settings | Appearance, language and region, reminders, horizon, Logo.dev token, privacy lock, export/import | FR-25, FR-26 |

**Notifications in the UI.** On load, the app waits for the startup catch-up (the backend reports `last_scheduler_run` in `/health`), then opens a "While you were away" dialog listing new notifications grouped as ending soon, overdue and renewing soon. Ending and overdue items also appear as a pinned alert panel at the top of the dashboard until resolved. Severity maps to the `destructive`, `warning` and `primary` tokens plus an icon, never color alone.

**Lock screen.** The app first calls `/auth/status`. While locked, only the lock route renders: no layout, navigation or badge. Any 401 from the API clears the whole TanStack Query cache, so no data stays in memory, and redirects to the lock screen.

**Appearance.** Theme toggles the `.dark` class on `<html>` (dark token values live under `.dark` in `global.css`). Font size sets the root font size (small 14px, default 16px, large 18px); everything is in `rem`, so the whole UI scales. Reduced motion sets a `data-reduced-motion` attribute that disables animations. Settings come from the backend, and theme and font size are also cached in localStorage and applied by a tiny inline script in `index.html` before React loads, so the page never flashes the wrong theme or size.

**Sensitive data warning (FR-46).** A frontend-only check on the payment method and notes fields: a run of 13 to 19 digits (spaces or dashes allowed) that passes the Luhn checksum, or an IBAN pattern, shows a non-blocking warning under the field. The backends don't validate it.

The footer shows which backend is answering, from `/health`.

### Logos

- The browser loads `https://img.logo.dev/{domain}?token={publishable key}`; the domain comes from the catalogue entry or the subscription's website.
- The publishable key is safe in client code, so the backends never call Logo.dev and need no outbound network access.
- No token, no domain, or an image error → a monogram with the service's initials.
- Attribution is not required for a personal project on the free plan; add the "Logos provided by Logo.dev" link only if the app ever becomes commercial.

### DatePicker

The app works only with `Temporal.PlainDate`; shadcn's Calendar works with `Date`. One wrapper bridges them.

```tsx
// src/components/date-picker.tsx: the only file that converts to and from Date
type DatePickerProps = {
  value: Temporal.PlainDate | undefined
  onChange: (value: Temporal.PlainDate | undefined) => void
  min?: Temporal.PlainDate
  max?: Temporal.PlainDate
}
```

- Conversion builds a local `Date` at noon from year, month and day (and reads them back the same way), so time zones and DST can never shift the day.
- Forms and features import `DatePicker`, never Calendar or react-day-picker.
- Covered by unit tests at month ends and DST change days, and used in the E2E flows for creating subscriptions.

## Testing and repository layout

Testing priority is **end to end > integration > unit**, on a base of static analysis. Most effort goes into tests that exercise the real stack, because the main risk is the two backends drifting apart, not a single function.

| Level | Effort | What | Tooling |
| --- | --- | --- | --- |
| Static | always on | Types, lint, format on every commit | tsc, ESLint, Prettier, Ruff, type checker, Spotless |
| End to end | most | User flows in the browser against the full Compose stack, run once per profile (fastapi, spring) | Playwright |
| Integration (API) | second | One HTTP suite written once in TypeScript against `/api/v1`, run against each backend on a fresh database; covers every BR table through the API. Plus contract checks against `openapi.yaml` | Playwright request fixture, Schemathesis |
| Unit | least | Billing core date math (BR-1 to BR-5) from the shared JSON cases; a few hooks or components with real logic | pytest, JUnit 5, Vitest |

**Controlling time in tests.** With `SUBTRACK_TEST_MODE=true`, each backend replaces its system clock with a settable one and exposes `POST /admin/clock` (`{today}`). Tests set the date, call `POST /admin/run-scheduler`, and check payments and notifications. POST /admin/reset empties all tables and re-seeds the catalogue so every test starts from a fresh database. All three test endpoints return 404 outside test mode.

**When suites run.** Static checks and the API suite run on every push, against both backends. The full browser suite runs only on main and the staging branch.

The `e2e/` folder holds both suites: `e2e/ui/` for browser flows and `e2e/api/` for the shared API suite.

### Repository layout (monorepo)

```
subtrack/
├── .github/workflows/       ci.yml (every push), e2e.yml (main, staging)
├── contract/                openapi.yaml, shared BR test cases, generated types (pnpm package)
├── db/migrations/           SQL migrations, the only schema owner
├── frontend/                React + Vite app
├── backend-fastapi/         same module names as backend-spring
├── backend-spring/          api, service, billing, repository, persistence, scheduler, security, clock, config, cli
├── e2e/                     Playwright: api/ (shared API suite) and ui/ (browser flows)
├── docs/                    srs.md, sdd.md, standards.md, adr/
├── docker-compose.yml       normal stack, profiles fastapi and spring
├── docker-compose.test.yml  test overlay: enables test mode
├── justfile                 single entry point for commands
└── pnpm-workspace.yaml      frontend, e2e, contract
```

Once the repository exists, its README files are the authoritative description of the folder structure; this list only covers the top level.

Everything lives in one repository, `subtrack`, with a root `justfile` as the single entry point for build, test and run commands.

## Sources

- [Logo.dev attribution rules](https://www.logo.dev/docs/platform/attribution)
- [Logo.dev vs Brandfetch](https://www.logo.dev/docs/brandfetch)
