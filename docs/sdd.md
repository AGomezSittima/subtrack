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
| Scheduling | In-process job in the active backend, at startup and then hourly, with catch-up | BR-18 to BR-20 |
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
| Flyway, nginx, Playwright, Schemathesis images | Latest, pinned by tag | Schemathesis 4.33.4; 4.34.0 was released the day it was pinned |

### CI

GitHub Actions, two workflows:

- **`ci.yml`** on every push and pull request: format, lint and type checks for the parts whose paths changed, then the API suite against both Compose profiles (fastapi, spring) on GitHub-hosted Linux runners.
  - A branch push compares the whole branch with main to decide which parts changed. On main, every part runs. Changes to `ci.yml` or the `justfile` re-run every part.
  - A pull request from a branch of this repository is already covered by the push run of its head commit, so its `pull_request` run is skipped. Pull requests from forks get a full run.
  - If the API suite fails, the job log shows the test stack's container status and logs.
- **`e2e.yml`** on pushes to `main` and the staging branch: the full Playwright browser suite against both profiles. It can also be started by hand on any branch (`workflow_dispatch`), to run the browser suite before merging.

Dependencies are updated manually while implementing; no update bot.

## Architecture and deployment

Only the web container publishes a port, on 127.0.0.1 (plus the API docs in the local-only `dev` profile, below). SQLite is a file, not a server, so there is no database container and nothing to expose: the file lives on a named volume mounted only into the migration job and the active backend.

&#91;embedded content: Compose stack · 1 published port, 1 volume\]

The browser reaches only nginx; the active backend and the migration job share the SQLite volume inside the stack.

### Services

| Service | Profiles | Role | Published port |
| --- | --- | --- | --- |
| web | fastapi, spring | nginx serving the React + Vite build, proxies `/api` to `api` | 127.0.0.1:8080 (`SUBTRACK_PORT`) |
| migrate | fastapi, spring | Flyway one-shot job applying `db/migrations/*.sql`, then exits | none |
| api-fastapi | fastapi | FastAPI backend, network alias `api` | none |
| api-spring | spring | Spring Boot backend, network alias `api` | none |
| docs | dev | nginx serving Swagger UI for `contract/openapi.yaml`, proxies `/api` to `api` | 127.0.0.1:8081 (`SUBTRACK_DOCS_PORT`) |

Both backends answer on the alias `api`, so the nginx config never changes. nginx resolves `api` at request time (`resolver 127.0.0.11` + a variable in `proxy_pass`); otherwise it refuses to start while the backend is still booting.

### API docs (dev profile)

Interactive API docs for local development only; `just up` never starts them. `just dev fastapi` (or `spring`) starts the stack with that backend plus the `docs` service, whose only profile is `dev`.

- Image `api-docs/`: nginx (same pinned tag as `web`) with Swagger UI from `swagger-ui-dist`, pinned to one version and verified by SHA-256 at build time. Its assets are part of the image, so the page loads nothing from a CDN.
- The spec is not copied into the image: `contract/` is mounted read-only and nginx serves only `/openapi.yaml` from it, without caching, so a reload shows the current contract.
- `/api` is proxied to the `api` alias exactly like in `web`, so "Try it out" is same-origin with the spec's server `/api/v1`: no CORS, and the session cookie works as in the app.
- Published on `127.0.0.1:${SUBTRACK_DOCS_PORT:-8081}` (`.env.example`). The `web` service and its nginx config are unchanged.
- Checked by the Playwright project `docs` (`e2e/docs/`): the page loads and lists every `operationId` in the contract, and `/api/v1/health` on the docs origin answers from the expected backend. It runs with `just test-docs [profile]` against a test stack started with the `dev` profile, and is not part of `just test` or CI.

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
    environment: { DB_PATH: /data/subs.db }
    volumes: [subs-data:/data]
    depends_on:
      migrate: { condition: service_completed_successfully }
    networks:
      default: { aliases: [api] }

  api-spring:
    build: ./backend-spring
    profiles: [spring]
    environment: { DB_PATH: /data/subs.db }
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

  docs:
    build: ./api-docs
    profiles: [dev]
    volumes: ["./contract:/contract:ro"]
    ports: ["127.0.0.1:${SUBTRACK_DOCS_PORT:-8081}:80"]

volumes:
  subs-data:
```

Both backends listen on port 8000 inside the network and serve everything under `/api/v1`. The host ports are optional configuration: `SUBTRACK_PORT` (default 8080) and `SUBTRACK_DOCS_PORT` (default 8081) from the shell or a root `.env` (`.env.example` documents them). The test overlay `docker-compose.test.yml` sets `SUBTRACK_TEST_MODE=true` and `SUBTRACK_TEMPLATE_DB` on both backends, makes the `migrate` job also migrate the template database (Testing), and adds the `schemathesis` service (profile `contract`).

### Everyday use

- Start: `docker compose --profile fastapi up -d` (or `--profile spring`)
- Start with the API docs: `just dev fastapi` (adds `--profile dev`)
- Stop: `docker compose --profile fastapi down` (`just down` stops every profile, `dev` included); data stays on the volume
- Switch backend: stop one profile, start the other; both read the same database
- Backup: the export endpoint, or copy `subs.db` out of the volume while stopped

## Database schema

Seven SQLite tables, all `STRICT` (a column only ever holds its declared type, so text can't slip into an `INTEGER` money column and pass its `>= 0` check). Money is `INTEGER` cents with `CHECK (… >= 0)`, dates are ISO `TEXT` (`YYYY-MM-DD`), timestamps are UTC ISO `TEXT`, each with the format checks under Date and timestamp storage. Every table has `id INTEGER PRIMARY KEY`, plus `created_at` and `updated_at` where rows change. Timestamp columns have no `DEFAULT`: backends write them from their injected clock. Closed sets (statuses, units, types, categories) are `CHECK … IN (…)`; adding a value later needs a migration that rebuilds the table, since SQLite can't alter a `CHECK`.

Categories are a closed set of codes that the frontend translates: `music`, `video`, `vpn`, `cloud`, `ai`, `software`, `domains`, `gaming`, `news`, `education`, `other`.

### catalog\_service

| Column | Type | Notes |
| --- | --- | --- |
| builtin\_key | text, unique, nullable, CHECK lowercase letters, digits and `-` | stable key of a built-in (`spotify`); null for a user entry |
| name | text | "Spotify"; not unique, the key is the identity |
| domain | text, nullable | "spotify.com", key for the logo |
| category | text, CHECK in the category codes |  |
| default\_plans | JSON text, CHECK JSON array | `[{"plan":"Individual","price_cents":1199,"interval_count":1,"interval_unit":"month"}]`, same interval shape as subscriptions; `price_cents` may be null |
| hidden | int 0/1 | default 0; CHECK only built-ins can be 1 |
| created\_at, updated\_at | timestamp |  |

Every row belongs to the user once installed: built-ins can be edited and hidden, user entries edited and deleted ([ADR 0007](adr/0007-catalogue-ownership.md)). Ids are always assigned by SQLite. Plans are stored in one canonical form, written identically by both backends and the generator: compact JSON, keys in the order `plan`, `price_cents`, `interval_count`, `interval_unit`, `price_cents` always present. A `CHECK` can't look inside the JSON (no subqueries), so its shape is validated by the API and by `just lint`.

### subscription

| Column | Type | Notes |
| --- | --- | --- |
| catalog\_service\_id | int, nullable | FK, ON DELETE SET NULL; null for free-text services |
| name | text |  |
| category | text, CHECK in the category codes |  |
| plan\_name | text, nullable |  |
| price\_cents | int | current price, mirrors the latest price\_history row |
| interval\_count | int, CHECK ≥ 1 |  |
| interval\_unit | text, CHECK in (day, week, month, year) |  |
| anchor\_date | date | first billing date |
| status | text, CHECK in (trial, active, paused, cancelled) |  |
| trial\_end\_date, cancelled\_at, paused\_at | date, nullable |  |
| resumed\_on | date, nullable | last resume or reactivation; no payments are generated before it |
| payment\_method | text, nullable | free label, e.g. "Visa ··4417" |
| auto\_confirm | int 0/1 | default 0 |
| reminders\_enabled | int 0/1 | default 1; 0 turns off renewal and yearly renewal notifications only |
| reminder\_days | int, nullable, CHECK 0 to 365 | renewal lead time; null = global default; kept while reminders are off |
| website, cancel\_url, notes | text, nullable |  |
| ends\_on | date, nullable | last day of access after cancelling |
| archived\_at | timestamp, nullable | set = archived |
| unarchived\_on | date, nullable | last unarchive; no payments are generated before it |
| created\_at, updated\_at | timestamp |  |

Row checks: a `cancelled` subscription has `cancelled_at` and `ends_on`; a `paused` one has `paused_at`; a `trial` has `trial_end_date` (BR-28).

### price\_history

| Column | Type | Notes |
| --- | --- | --- |
| subscription\_id | int | FK, ON DELETE CASCADE |
| price\_cents | int |  |
| effective\_from | date | UNIQUE (subscription\_id, effective\_from) |
| created\_at | timestamp | entries are never edited, so no updated\_at |

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
| created\_at, updated\_at | timestamp |  |

A partial unique index on (subscription\_id, due\_date) WHERE source != 'manual' makes payment generation idempotent. Row check: `paid_cents` and `paid_date` are set exactly when `status = 'paid'`.

### notification

| Column | Type | Notes |
| --- | --- | --- |
| type | text, CHECK | renewal, trial\_ending, yearly\_renewal, overdue, ending |
| subscription\_id, payment\_id | int, nullable | FK, ON DELETE SET NULL |
| severity | text, CHECK | info, warning, critical (BR-24) |
| trigger\_date | date | the day it was due to fire |
| dedupe\_key | text, UNIQUE | e.g. `renewal:12:2026-12-01` |
| read\_at | timestamp, nullable |  |
| params | JSON text, CHECK JSON object | e.g. {name, date, amount\_cents}; the frontend renders the text in the user's language |
| created\_at, updated\_at | timestamp |  |

### setting

Key-value table (`key` unique, `value` text, `updated_at`). `NULL` means "not set". The key list is closed (`CHECK key IN (…)`) and each key's value has its own check, so adding a key needs a migration that rebuilds this small table. V1 inserts every key with its default; those seed rows' `updated_at` is the migration time.

Keys and defaults: `default_reminder_days` (3), `trial_reminder_days` (3), `yearly_reminder_days` (14), `payment_horizon_days` (60), `logo_dev_token` (null), `last_scheduler_run` (null).

Also `ending_reminder_days` (7) and `locale` (`en` or `es`; null = follow the browser).

Time zone keys (V6, [ADR 0008](adr/0008-configurable-time-zones.md)): `home_time_zone` (an IANA name; null = not set yet, and the backend uses UTC) and `display_time_zone` (`device` or an IANA name; default `device`). The checks allow only the shape of a zone name (letters, digits, `/`, `_`, `+`, `-`, at most 64 characters); the backends check the name against their zone database.

Appearance and lock keys: `theme` (system, light, dark; default system), `font_size` (sm, md, lg; default md), `reduced_motion` (system, always; default system), `week_start` (1 = Monday … 7; default 1), `passcode_hash` (Argon2id in PHC string format; null = no passcode), `lock_policy` (on\_open, idle\_5, idle\_15, idle\_30, idle\_60, manual; default idle\_15), `failed_attempts` (0), `retry_after` (timestamp, null).

### session

| Column | Type | Notes |
| --- | --- | --- |
| token\_hash | text, unique, CHECK 64 lowercase hex | SHA-256 of the random session token; the token itself is only in the cookie |
| created\_at | timestamp |  |
| last\_activity\_at | timestamp | updated only by user-driven requests |

Sessions live in the database, so switching backend profile keeps you unlocked. Removing the passcode or locking deletes all rows.

### Indexes

Besides the unique constraints above (`catalog_service.builtin_key`, `price_history (subscription_id, effective_from)`, `notification.dedupe_key`, `setting.key`, `session.token_hash`):

| Index | Serves |
| --- | --- |
| `payment (subscription_id, due_date)` UNIQUE WHERE source != 'manual' | Idempotent payment generation (BR-7) |
| `payment (subscription_id, due_date)` | Payments of one subscription; the ON DELETE CASCADE lookup |
| `payment (due_date)` | Date-range lists, upcoming payments, monthly statistics |
| `payment (status, due_date)` | Expected, overdue and auto-confirm scans |
| `notification (trigger_date)` WHERE read\_at IS NULL | Unread list and badge count |
| `notification (subscription_id)`, `notification (payment_id)` | ON DELETE SET NULL lookups |
| `subscription (catalog_service_id)` | ON DELETE SET NULL lookup |

### Migrations and ownership

- `db/migrations/V1__init.sql` creates the schema and the default settings; later changes are new `Vn__` files. `V2__catalog_seed.sql` was reserved for the catalogue and stays empty: it is applied, so editing it would break Flyway's checksum. The built-ins arrive in V5.
- `V3__trial_end_date_and_resume_dates.sql` rebuilds `subscription` to add the trial check and the `resumed_on` and `unarchived_on` columns. SQLite can't add a table `CHECK` with `ALTER TABLE`, so it follows SQLite's documented rebuild: create the new table, copy, drop, rename, recreate the index. That procedure needs foreign keys off, or dropping the old table would cascade to `price_history` and `payment`. Flyway runs each migration in a transaction, where `PRAGMA foreign_keys = OFF` has no effect. But Flyway's sqlite-jdbc connection already leaves foreign keys off (checked with the pinned Flyway image), so the rebuild is safe. The migration starts by inserting `pragma_foreign_keys` into a temporary table whose `CHECK` requires 0, so it aborts before touching data if foreign keys are ever on. BEFORE INSERT/UPDATE triggers with `RAISE(ABORT, …)` would have avoided the rebuild, but they would keep the rule outside the table definition and wouldn't check existing rows. The test helper applies migrations with foreign keys off, like Flyway.
- `V4__catalogue_ownership_and_categories.sql` rebuilds `catalog_service` (adds `builtin_key`, `hidden` and the timestamps, makes `domain` nullable, drops the unique name) and `subscription` (the `gaming`, `news` and `education` categories), with the same procedure and foreign-key guard as V3. Existing catalogue rows become user entries, timestamped with the migration time.
- **Built-in catalogue.** `db/catalog/catalog.json` lists the built-ins (key, name, domain, category, plans without prices), validated against `db/catalog/catalog.schema.json`. `just catalog-migration` (`db/scripts/catalog-migration.ts`) applies the existing migrations to an in-memory database, and writes `V<next>__catalog_builtins.sql` with one `INSERT … ON CONFLICT (builtin_key) DO NOTHING` per key not installed yet, timestamped with the migration time and without ids. `V5__catalog_builtins.sql` is the first, with 54 services. The file only grows: `just lint` (`db/scripts/check-catalog.ts`) fails when the JSON is invalid, when one of its keys isn't installed by the migrations, when an installed key is gone from it, or when an entry differs from what its migration inserted ([ADR 0007](adr/0007-catalogue-ownership.md)). Both scripts use Node's built-in `node:sqlite`.
- `V6__time_zone_settings.sql` rebuilds `setting` to add `home_time_zone` and `display_time_zone` to the key list and the value checks, copying the existing rows. No table references `setting`, so the foreign-key guard isn't needed.
- `V7__subscription_reminders_enabled.sql` rebuilds `subscription` to add `reminders_enabled` and narrow the `reminder_days` check to null or 0 to 365, replacing the -1 "off" value, with the same procedure and foreign-key guard as V3. A stored -1 becomes `reminders_enabled = 0` with `reminder_days` null.
- `backend-fastapi/tests/integration/test_migrations.py` applies every migration to a fresh SQLite file (fixture `migrated_db` in `tests/conftest.py`, reused by repository tests) and checks the constraints.
- Only the Flyway `migrate` container changes the schema. Spring runs with Flyway disabled and `ddl-auto=validate`; FastAPI never calls `create_all`. A mismatch fails at startup.
- Every connection sets `PRAGMA foreign_keys = ON` (off by default in SQLite), `journal_mode = WAL` and a `busy_timeout` of 5 s. Each backend opens connections in one place: FastAPI `persistence/engine.py` (a SQLAlchemy `connect` event), Spring `persistence/DataSourceConfig` (sqlite-jdbc's `SQLiteConfig` under Hikari).
- The pool holds one connection. SQLite allows one writer at a time, so a second connection in the same process would only wait on the write lock, or fail with `SQLITE_BUSY` when a read transaction upgrades to a write. The busy timeout covers other processes, such as the `reset-passcode` command.
- Spring needs the `sqlite-jdbc` driver and the Hibernate community SQLite dialect. sqlite-jdbc reports `INTEGER PRIMARY KEY` as `INTEGER`, so entity ids declare `columnDefinition = "INTEGER"` to pass `ddl-auto=validate` while keeping 64-bit `Long` values.

### Date and timestamp storage

Both backends write the same ISO text (NFR-9); neither relies on driver defaults.

| Kind | Stored as | Example |
| --- | --- | --- |
| Date | `YYYY-MM-DD` | `2026-12-01` |
| Timestamp | UTC, millisecond precision, `Z` suffix | `2026-11-10T15:18:00.000Z` |

- **Database**: `CHECK` constraints on every date and timestamp column, so a wrong format fails on write instead of corrupting data. Every column uses one of two patterns, written identically everywhere (nullable columns wrap them in `col IS NULL OR (…)`):
  - Date: `col GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(col) IS col`
  - Timestamp: `col GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', col) IS col`

  The `GLOB` checks the shape; the round-trip through `date()` / `strftime()` rejects impossible values such as `2026-02-30` or `25:00`. It uses `IS`, not `=`: those functions return NULL for invalid input, and a `CHECK` that evaluates to NULL passes.
- **Contract**: `Date` is `YYYY-MM-DD` (format `date`) and `Timestamp` is `YYYY-MM-DDTHH:MM:SS.mmmZ` (format `date-time`). Their patterns accept exactly what the backends parse: years 0001 to 9999 and seconds 00 to 59, with the calendar check left to `format`. `Month` (statistics) has `^[0-9]{4}-(0[1-9]|1[0-2])$`.
- **Spring**: JPA `AttributeConverter`s for `LocalDate` and `Instant` (`IsoDateConverter`, `UtcTimestampConverter`, `autoApply`) that read and write these strings. The SQLite JDBC driver's default date storage (epoch milliseconds) is never used.
- **FastAPI**: `TypeDecorator`s `IsoDate` and `UtcTimestamp`, not SQLAlchemy's `Date` and `DateTime`, so both kinds go through the same strict code.
- **Parsing** is strict in both (`persistence/isotext.py`, `IsoText`): the shape, then a calendar check (year 0001 or later, seconds 00 to 59). A non-canonical value in the database fails to read instead of being guessed. Timestamps are truncated to milliseconds on write.
- **API**: the same text on the wire. Responses write timestamps with milliseconds (`2026-11-10T15:18:00.000Z`, which neither framework does by default), and request timestamps are checked by the same parser (issue code `format`).
- **Test**: `just test-cross` writes catalogue data through one backend, switches the same test stack to the other profile, and reads it back identically (ids, timestamps, plans), in both directions (Testing).

## API contract

REST + JSON under `/api/v1`, written first in `contract/openapi.yaml`. Both backends implement it exactly; the frontend only knows the contract. Money is integer cents, dates are `YYYY-MM-DD`. Errors use RFC 9457 problem JSON (`type`, `title`, `status`, `detail`, plus `errors[]` for validation).

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /catalog | Catalogue services, `?q=` search; hidden built-ins only with `?include_hidden=true` |
| POST | /catalog | Add a user entry (FR-47) |
| PATCH | /catalog/{id} | Edit any entry; `hidden` for built-ins (FR-48, FR-49, FR-51) |
| DELETE | /catalog/{id} | Delete a user entry; built-ins are 409 (FR-50) |
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
| POST | /admin/run-scheduler | Test mode only: run the scheduler job now; 404 otherwise |
| GET | /export | `?format=json` or `csv` |
| POST | /import | JSON from /export |
| GET | /health | `{status, backend: "fastapi" or "spring", version, today, last_scheduler_run}` (`today` is the current date in the home time zone; `last_scheduler_run` is null until the job has run) |
| POST | /admin/clock | Test mode only: `{now}` pins the clock to a timestamp (today is its date in the home time zone); 404 otherwise |
| POST | /subscriptions/{id}/archive | Archive (FR-28); /unarchive restores it; GET /subscriptions?archived=true lists them |
| POST | /admin/reset | Test mode only: restore the database to its state right after migrations and unpin the clock; 404 otherwise |
| GET | /auth/status | {passcode\_enabled, unlocked, lock\_policy, retry\_after}; no other data |
| POST | /auth/unlock | {passcode}; sets the session cookie, or 401 / 429 with retry\_after |
| POST | /auth/lock | Lock now: deletes the session and clears the cookie |
| PUT | /auth/passcode | {current?, new}: set or change the passcode |
| DELETE | /auth/passcode | {current}: remove the passcode |

The `backend` field in `/health` lets the frontend show which implementation is answering.

`contract/openapi.yaml` is the detailed source; these are the decisions it settles.

- **Conventions.** Every object has `additionalProperties: false`. Lists come wrapped (`{"items": [...]}`) so fields can be added later. A subscription always carries its derived fields (`next_renewal_date`, `monthly_equivalent_cents`, `yearly_equivalent_cents`, `payment_count`, `total_paid_cents`), in lists too. A payment carries `subscription_name`. `PATCH` changes only the fields sent; `null` clears a nullable field, and `null` on a non-nullable one is a `type` issue. Query strings follow Starlette's parsing in both backends: a chunk without a name is ignored and the last repeated value wins (Spring parses the raw query string itself, since Tomcat rejects malformed chunks and Spring joins repeated values). Text limits (`maxLength`) count Unicode code points, as JSON Schema does (Java must use `codePointCount`, not `length()`).
- **Security.** One cookie scheme, `subtrack_session`, applied globally; `security: []` only on `/health`, `/auth/status`, `/auth/unlock` and the test-mode endpoints `/admin/clock` and `/admin/reset`.
- **Status changes** (`POST /subscriptions/{id}/status`, body `{status, date?, ends_on?}`; `date` defaults to today and is only for pause and cancel, `ends_on` only for cancel). Allowed: trial → active, paused, cancelled; active → paused, cancelled; paused → active, cancelled; cancelled → active. Anything else is 409 `invalid-status-transition`.
- **Payments.** A manual payment (`POST /payments`, `{subscription_id, paid_cents, paid_date, note?}`) is stored as `paid`, source `manual`, with `due_date = paid_date` and `expected_cents = paid_cents`. Confirming defaults `paid_cents` to `expected_cents` and `paid_date` to the due date. Confirm, skip and fail apply only to `expected` payments (otherwise 409 `payment-not-expected`).
- **Statistics.** Breakdowns are ordered by exact monthly equivalent descending, then key, and allocated by largest remainder with the parts in key order: categories by code, payment methods by label with no label last, subscriptions by id (BR-26; equal remainders go to the earlier part). The display order doesn't affect the allocation. `/stats/monthly` groups payments by the month of their due date: `planned_cents` is every payment's `expected_cents`, `actual_cents` the paid ones' `paid_cents`.
- **Catalogue.** Entries carry `builtin` and `hidden`, never the key. `GET /catalog` sorts by `name COLLATE NOCASE`, then id, and `q` matches with `instr(lower(name), lower(q))`: both run in SQL, so both backends match the same rows (case-insensitive for ASCII letters only). `include_hidden` accepts only `true` and `false`. A plan's `price_cents` is always present in responses, null when unset, and may be left out in requests. `PATCH` replaces `plans` as a whole and sets `updated_at` on every successful call. `hidden: true` on a user entry is 422 `invalid` on `/hidden`; a plan with the same name and interval as an earlier one in the list is 422 `invalid` on `/plans/<i>`. Deleting a built-in is 409 `builtin-service-not-deletable`; deleting a user entry unlinks its subscriptions. Changing the catalogue never changes an existing subscription.
- **Passcode.** `^[A-Za-z0-9]{6,128}$`. Setting or changing it deletes other sessions and returns a fresh session cookie. A missing or wrong `current` counts as a failed attempt.

### Errors

RFC 9457 problem JSON with media type `application/problem+json`: `type`, `title`, `status`, optional `detail`, plus `errors[]` (validation) or `retry_after` (too many attempts). `type` is a stable URN `urn:subtrack:problem:<code>` that the frontend translates. `title` (constant per type) and `detail` are developer-facing English and never displayed.

| Code | Status | When |
| --- | --- | --- |
| `malformed-request` | 400 | The body is not valid JSON |
| `unauthorized` | 401 | A passcode is set and there is no valid session |
| `invalid-passcode` | 401 | Wrong passcode on unlock, or missing/wrong `current` |
| `not-found` | 404 | Unknown resource or route, or a test endpoint outside test mode |
| `method-not-allowed` | 405 | The path exists but not with this method |
| `passcode-not-set` | 409 | Unlocking or removing the passcode while none is set |
| `invalid-status-transition` | 409 | A status change outside the transition list |
| `payment-not-expected` | 409 | Confirming, skipping or failing a payment that is not `expected` |
| `payment-not-paid` | 409 | Editing `paid_cents` or `paid_date` of a payment that is not `paid` |
| `price-change-exists` | 409 | A price change already takes effect on that date |
| `builtin-service-not-deletable` | 409 | Deleting a built-in catalogue service; it can only be hidden |
| `unsupported-media-type` | 415 | The body is not `application/json` |
| `validation-error` | 422 | Invalid parameters or body |
| `unsupported-format-version` | 422 | Import of an unknown (newer) export format |
| `too-many-attempts` | 429 | Unlock refused until `retry_after` (also a `Retry-After` header) |
| `internal-error` | 500 | Anything unexpected |

Each `errors[]` entry is `{location, pointer, code, params?}`: `location` is `body`, `query` or `path`; `pointer` is a JSON Pointer into the body (`/price_cents`), or `/<name>` for a parameter; `code` is one of `required`, `unknown_property`, `type`, `format`, `pattern`, `enum`, `minimum`, `maximum`, `min_length`, `max_length`, `invalid` (a cross-field rule such as `to` before `from`); `params` carries the limit (`minimum`, `maximum`, `min_length`, `max_length`, `allowed`). Both backends map their validator's errors to these codes.

- **What `errors[]` lists.** At least one entry, and every entry is accurate. Constraint failures (`required`, range, length, `format`, `pattern`, `enum`) are all listed. For a structural failure (wrong type, unknown property) only the first may be listed: Jackson stops at the first one, Pydantic doesn't. In Spring, required fields fail through Bean Validation, not Jackson, and dates and timestamps are bound as strings and checked by a constraint, so those are constraint failures in both backends. Entries are sorted by `location`, `pointer` and `code`.
- **Content type.** A body must be `application/json` or `application/*+json`. Any other content type, or a body sent without one, is 415. No body and no content type on an operation that needs one is 422 `required` at pointer `""`. Spring's multipart resolver is off, since the API takes no multipart bodies.
- **Routing errors.** Under `/api/v1`, a path with no route is 404 `not-found`, and a known path with another method is 405 `method-not-allowed` with an `Allow` header listing the allowed methods sorted, with `HEAD` wherever `GET` is allowed. `HEAD` works on every `GET` route. `OPTIONS` and non-standard methods such as `QUERY` are treated like any other method (Spring's implicit `OPTIONS` answer and its firewall's method list are switched off). Paths outside `/api/v1` are 404. While locked, 401 comes first (Privacy lock).
- **Unexpected errors** are 500 `internal-error` with only `type`, `title` and `status`. The stack trace goes to the log, never to the body.
- **Where.** FastAPI: `api/problems.py` (the `ProblemType` enum, `ProblemError`) and `api/errors.py` (the exception handlers). Spring: `api/ProblemType`, `ProblemHandler` (`@RestControllerAdvice` over `ResponseEntityExceptionHandler`) and `ProblemErrorController` (errors raised outside Spring MVC). Spring renders its own `Problem` record, not `ProblemDetail`, because Spring MVC fills in `ProblemDetail.instance`, which the contract doesn't allow.

### Linting

`just lint` runs Redocly CLI on the contract with `recommended-strict` (`contract/redocly.yaml`), so any finding fails. Deliberate exceptions live in `contract/.redocly.lint-ignore.yaml`, and `just gen` validates with a minimal structural config (`contract/redocly.gen.yaml`); the reasons are in ADR 0005.

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
- `data` (format version 1) holds `catalog`, `subscriptions`, `price_history`, `payments` and `notifications` (read state included), plus the user `settings`. Subscriptions, price history, payments and notifications are stored rows with their ids. It leaves out `failed_attempts`, `retry_after` and `last_scheduler_run`, so catch-up after an import starts from the importing app's own state. The file's row schemas (`Export*` in the contract) are kept apart from the API resources, so an API change never changes the file format by accident.
- `catalog` has no ids, since ids differ between installs ([ADR 0007](adr/0007-catalogue-ownership.md)). It lists every built-in as `{builtin_key, name, domain, category, plans, hidden, created_at, updated_at}`, so their edits and hidden state travel, and every user entry as `{ref, name, domain, category, plans, created_at, updated_at}`, where `ref` is a number local to the file. A subscription references its entry with `catalog: {"builtin_key": "spotify"}`, `catalog: {"ref": 3}` or `catalog: null`, instead of `catalog_service_id`.
- Import replaces all data in one transaction: it deletes subscriptions, price history, payments and notifications, inserts the file's rows with their ids, and applies its settings. For the catalogue, each built-in in the file updates the row with the same key, or is inserted as a built-in when this app doesn't have that key yet (a later catalogue migration then leaves it alone); built-ins missing from the file stay as they are. User entries are replaced: the existing ones are deleted and the file's inserted, mapping each `ref` to its new id. Subscription references are resolved by key or `ref`; one that doesn't resolve becomes null.
- The contract's `Export*` schemas still have `catalog_service_id`; they change to this design with the export slice. No version 1 file has been produced yet, so the version stays 1.
- `GET /export?format=csv` is one flat file for spreadsheets, not a backup, and can't be imported: one row per payment (archived subscriptions included) with columns `payment_id, subscription_id, subscription_name, category, payment_method, due_date, expected_cents, paid_cents, paid_date, status, source, note`. RFC 4180, UTF-8, header row, empty fields for null, amounts in cents.

## Backend design

Both backends use the same four layers and the same module names, so code can be compared file by file.

1. **API layer**: routes, request/response models, problem-JSON error handler. No logic.
2. **Service layer**: use cases (create subscription, confirm payment, change price, run scheduler), one transaction each.
3. **Billing core**: pure functions with no I/O: billing dates and cost normalization (BR-1 to BR-5), allocation (BR-26), payment planning (BR-6 to BR-12, BR-27) and reminder planning (BR-13 to BR-17, BR-19, BR-23, BR-24). The shared test cases target this module directly.
4. **Repository layer**: data access for the seven tables.

Time is injected everywhere (a UTC `java.time.Clock` bean in Java, a `Clock` with `now()` in Python), so tests can pin "today" and the scheduler can be tested across months. Only the `clock` module reads the system time; Ruff (`TID251`) and Checkstyle enforce it.

**Home time zone.** "Today" is always `HomeZone.today()`: the date of the clock's instant in the home time zone, or in UTC while `home_time_zone` is null ([ADR 0008](adr/0008-configurable-time-zones.md)). `HomeZone` (in `clock`, one per backend) caches the zone, so reading today never hits the database. It is loaded at startup (FastAPI `build_container`; Spring `ApplicationStartedEvent`, before the startup scheduler run), set after a settings update commits, and reloaded after `POST /admin/reset`; any later code that writes the setting (import) must refresh it too. FastAPI never loads it inside a request: the pool has one connection, so a second transaction would wait on the first. Zone names are validated against each backend's own database: FastAPI loads zones only from the pinned `tzdata` package (`clock/zones.py`), never from the OS; Spring accepts only `ZoneId.getAvailableZoneIds()` (`clock/TimeZones`), so offsets such as `+01:00` or `GMT+1` are rejected. An unknown name is a 422 `invalid` issue. Changing the home zone recalculates nothing.

### Billing core

The billing core is split the same way in both backends. A Python module matches a Java class with static functions, together with the plain domain types it takes and returns (frozen dataclasses and `StrEnum`s in Python; records and enums in Java). Nothing in it imports web, ORM or framework code, and "today" is always a parameter.

| Python `subtrack.billing` | Java `dev.subtrack.billing` | Functions |
| --- | --- | --- |
| `interval` | `Interval`, `IntervalUnit` | `is_yearly_or_longer` / `isYearlyOrLonger` |
| `dates` | `BillingDates` | `billing_date`, `billing_dates`, `billing_dates_between` |
| `costs` | `Costs`, `Fraction` | `monthly_equivalent`, `yearly_equivalent`, `round_half_up`, `floor_cents` |
| `allocation` | `Allocations`, `PartKey` | `allocate` |
| `payments` | `PaymentPlanner` | `plan_payments`, `price_in_force` |
| `reminders` | `ReminderPlanner` | `plan_reminders`, `dedupe_key` |
| `errors` | `BillingInputException`, `BillingErrorCode` | — |

- **Exact arithmetic.** Derived money values are exact rationals: Python `fractions.Fraction`, Java a `Fraction` record over `BigInteger`. They are rounded once, half-up, at the end. `BigDecimal` is not used, because factors such as 365/12 have no exact decimal form.
- **Calendar steps.** Python `dateutil.relativedelta` and Java `LocalDate.plusMonths` both clamp to the end of a shorter month. Each date is `anchor + n × interval`.
- **Label order.** Allocation compares labels by Unicode code point. Java compares code-point arrays, not `String.compareTo`, which orders by UTF-16 unit.
- **Invalid input** (an interval count below 1, a trial without a trial end date, a paused or cancelled subscription without its date, an empty price history) raises `BillingInputException` with a `BillingErrorCode`, never display text.

### Foundations

The plumbing every slice uses, split the same way in both backends:

| Concern | Python `subtrack` | Java `dev.subtrack` |
| --- | --- | --- |
| Connections, PRAGMAs, pool | `persistence/engine.py` | `persistence/DataSourceConfig` |
| ISO text and column types | `persistence/isotext.py`, `persistence/types.py` | `persistence/IsoText`, `IsoDateConverter`, `UtcTimestampConverter` |
| Rows | `persistence/models.py` (`SettingRow`, `SessionRow`) | `persistence/SettingEntity`, `SessionEntity` |
| Repositories | `repository/settings.py`, `sessions.py`, `template.py`, `transaction.py` | `repository/SettingRepository`, `SessionRepository`, `TemplateRepository` |
| Clock and home zone | `clock/clock.py`, `system.py`, `settable.py`, `home_zone.py`, `zones.py` | `clock/ClockConfig`, `SettableClock`, `HomeZone`, `TimeZones` |
| Settings | `api/settings.py`, `service/settings.py` | `api/SettingsController`, `SettingsRequest`, `SettingsResponse`, `service/SettingsService`, `UserSettings` |
| Problem JSON | `api/problems.py`, `api/errors.py` | `api/ProblemType`, `Problem`, `ProblemHandler`, `ProblemErrorController` |
| Test-mode endpoints | `api/admin.py`, `service/admin.py` | `api/AdminController`, `service/AdminService` |
| Lock | `security/lock.py`, `middleware.py`, `tokens.py` | `security/LockEnforcer`, `SecurityConfig`, `LockAuthorizationManager`, `SessionTokens` |
| Wiring | `api/dependencies.py`, `main.create_app` | constructor injection |

- **Transactions.** A Python service takes a `Transaction`, a context manager that yields the repositories bound to one SQLAlchemy session and commits or rolls back. Spring services use `@Transactional`. The template restore is the one exception: SQLite can't `ATTACH` inside a transaction, so the repository manages its own connection and transaction.
- **Test-mode beans.** The admin routes, the settable clock and the template repository exist only when `SUBTRACK_TEST_MODE=true` (FastAPI mounts the router conditionally; Spring uses `@ConditionalOnBooleanProperty`). Other paths answer 404 as unknown routes.
- **Strict JSON.** Pydantic request models use `extra="forbid"` and `strict=True`. Jackson fails on unknown properties and doesn't coerce scalars or accept floats as integers. A missing field is `required` and an explicit `null` on a non-nullable field is `type`: Pydantic tells them apart with `MISSING` for optional `PATCH` fields; Spring request bodies are field-bound classes rather than records (Jackson runs absent record components through the null handler) with `@JsonSetter(nulls = FAIL)` on non-nullable fields. Lengths count code points (`@MinCodePoints`, `@MaxCodePoints`), and code sets are checked with `@OneOf`. Path ids and query parameters are parsed by hand in both backends (`api/parameters.py`; `PathIds`, `QueryString`, `QueryParameters`).

| Concern | FastAPI | Spring Boot |
| --- | --- | --- |
| Routing | `APIRouter` per resource | `@RestController` per resource |
| Validation | Pydantic models | Bean Validation on DTO records |
| Data access | SQLAlchemy 2 (ORM or Core) | Spring Data JPA / Hibernate |
| SQLite driver | stdlib `sqlite3` | `sqlite-jdbc` + community dialect |
| Errors | custom exception handlers → problem JSON | `@RestControllerAdvice` → own `Problem` record (Errors) |
| Startup hook | lifespan handler | `ApplicationReadyEvent` listener |
| Scheduler job (hourly) | APScheduler cron trigger | `@Scheduled(cron, zone)` |
| Settings | pydantic-settings, env vars | `application.yml`, env vars |
| Tests | pytest | JUnit 5 + AssertJ |

### Scheduler

The scheduler job contains no business rules. It loads data, calls the pure planning functions of the billing core, and saves what they return. The same planners serve every use case that changes payments, so the rules exist in one place per backend and are pinned by the shared cases.

- Runs once at startup, then every hour, and in test mode on `POST /admin/run-scheduler`.
- **Load.** Today comes from `HomeZone`, the injected clock's date in the home time zone. The job reads the settings and `last_scheduler_run`; the date of `last_scheduler_run` and a subscription's creation date are their timestamps' dates in the home time zone.
- **Payment planning**, one transaction per subscription. The job loads the subscription, its price history and its payments, then calls `plan_payments(today, settings, subscription, price_history, payments, backfill=false)`. The planner returns a `PaymentPlan`: the new status, the full set of payments afterwards (new ones have no id and are the inserts), `updated_ids` and `deleted_ids`. Its changes are worked out in BR-18 order: activate an ended trial (BR-12), remove payments that a pause, cancellation or archive cuts off (BR-10, BR-27), reprice (BR-9), generate up to the horizon (BR-6 to BR-8), auto-confirm (BR-11). The job saves exactly that.
- **Reminder planning**, one transaction. The job loads the subscriptions, their payments and the existing dedupe keys, then calls `plan_reminders(last_run_date, today, settings, subscriptions, payments, existing_dedupe_keys)` (BR-13 to BR-17, BR-19, BR-23, BR-24, BR-27). It inserts the returned notifications with `INSERT … ON CONFLICT DO NOTHING`.
- **Finish.** It stores `last_scheduler_run`.
- **Use cases.** Create (with or without backfill, FR-27), pause, resume, cancel, reactivate, archive, unarchive and price change each call `plan_payments` in their own transaction, after changing the subscription. Their payments are right immediately, without waiting for the next run. Resume and reactivate set `resumed_on`; unarchive sets `unarchived_on`.
- **Planners.** Pure functions in `billing`. They take plain values (dates, cents, statuses) and return plain values; they never read the clock or the database. Their cases are `contract/cases/payment-generation.json` and `contract/cases/reminders.json`.
- **Idempotency** comes from the database: the payment unique index and the notification `dedupe_key`. The planners are also idempotent, so a second run returns no changes (BR-20).
- Only one backend runs at a time (Compose profiles), so there is no need for a distributed lock.

### Privacy lock

Scope: a privacy screen against casual use of a logged-in computer, and a learning comparison of session auth in FastAPI vs Spring Security. It is not data protection; the SQLite file is unencrypted by design (SRS assumptions).

When a passcode is set, every endpoint returns 401 without a valid session, except `/health`, `/auth/status` and `/auth/unlock`, and in test mode `/admin/clock` and `/admin/reset`, so the test suite can reset and set the clock on a locked database. `/admin/run-scheduler` stays locked. Enforcement is one place per backend: an ASGI middleware in FastAPI ([ADR 0006](adr/0006-fastapi-lock-middleware.md)), the Spring Security filter chain in Spring. Both run before routing and body parsing. Each request is decided in this order:

1. Paths outside `/api/v1` are not locked.
2. Outside test mode, `/admin/clock` and `/admin/reset` answer 404 whether or not a session exists, so a locked API doesn't reveal them.
3. The exceptions above pass.
4. Any other `/api/v1` path needs a valid session, or answers 401, whether the route exists, the method is allowed or the body is valid.

A request passes when no passcode is set, or when the SHA-256 of its `subtrack_session` cookie is in `session` and that session isn't idle-expired under `lock_policy`: `idle_N` expires once more than N minutes have passed since `last_activity_at`, and `on_open` and `manual` never expire. A passing request sets `last_activity_at` to now, unless it carries `X-Subtrack-Background: 1`. Time comes from the injected clock.

Spring Security adds several things by default that the design doesn't use. They're switched off in `SecurityConfig`:

| Default | Why it's off |
| --- | --- |
| Form login and its login page, HTTP basic, logout | Unlock and lock are JSON endpoints (`/auth/unlock`, `/auth/lock`); there are no users |
| Generated default user and password | `UserDetailsServiceAutoConfiguration` is excluded; there is no user store |
| CSRF tokens | The session cookie is `SameSite=Strict`, so cross-site requests don't carry it. Bodies must be JSON (415 otherwise), which a cross-site form can't send, and there is no CORS |
| Request cache | It exists to redirect back after a login page |
| HTTP session (`JSESSIONID`) | `STATELESS`: the session is the `subtrack_session` cookie and the `session` table |
| Default security headers | FastAPI sends none, and the backends must answer identically. If they're wanted, both should add them in the same PR |
| Firewall method list | `StrictHttpFirewall` lets any method through, so unlisted methods get 405 from routing, as in FastAPI |

Anonymous authentication stays on, so a denied request reaches the entry point that writes the 401 problem. Error dispatches are permitted, so errors raised outside MVC still render as problem JSON.

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
| Dates and times | Temporal, with a polyfill loaded at entry for Safari; "today" from `/health`; helpers in `src/lib/time` |
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

**Dates and time zones** ([ADR 0008](adr/0008-configurable-time-zones.md)). Everything date-related goes through `src/lib/time/`, the frontend's clock module (the only place ESLint allows `Temporal.Now`):

- `useToday()` returns today as a `Temporal.PlainDate` from `/health`, refetched every 60 s and when the window regains focus. Every date-based decision (due today, overdue, date-picker limits) uses it; the frontend never derives today from the device clock.
- `formatTimestamp(instant, displayZone, locale)` shows a UTC timestamp in the display time zone (`device` resolves to `deviceTimeZone()`).
- `formatPlainDate(date, locale)` formats a plain date with no time zone at all, so a due date never shifts.

**Home time zone on first load.** When `GET /settings` returns `home_time_zone: null`, the root layout saves the device's zone with `PUT /settings` once and shows a dismissible notice ("Home time zone set to …; you can change it in settings"). If the backend rejects the device's zone, it stays unset and no notice appears. The settings page itself comes with the settings slice.

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
| Unit | least | Billing core from every shared JSON case file (dates, costs, allocation, payment and reminder planning), each case its own parameterized test named by id and rules; a few hooks or components with real logic | pytest, JUnit 5, Vitest |

**Controlling time in tests.** With `SUBTRACK_TEST_MODE=true`, each backend replaces its system clock with a settable one and exposes `POST /admin/clock` (`{now}`, a UTC timestamp; "today" is its date in the home time zone, UTC right after a reset, and the time of day drives idle-lock tests). The settable clock follows the system clock until it is pinned. Tests set the clock, call `POST /admin/run-scheduler`, and check payments and notifications. All three test endpoints return 404 outside test mode.

**Fresh database.** `POST /admin/reset` returns the database to its state right after migrations, so the migrations stay the only source of seeded data (default settings, catalogue):

- The test overlay's `migrate` job also migrates an empty `/data/template.db`, which the backends find through `SUBTRACK_TEMPLATE_DB`. The normal stack never has one, and a backend in test mode refuses to start without it.
- Reset attaches the template. Then, in one transaction with `PRAGMA defer_foreign_keys = ON` (foreign keys stay on), it deletes every table's rows and copies the template's rows back. The table list comes from the template's `sqlite_master`, minus `flyway_schema_history` and SQLite's own tables, so a new table needs no code change. Ids start again from 1.
- Reset also unpins the clock, so each test sets the date it needs.

**Shared API suite.** Tests run one at a time (`workers: 1`), since they share the stack's single database. `e2e/support/` holds a client typed from `@subtrack/contract` (openapi-fetch), an automatic fixture that calls `POST /admin/reset` before each test, and `setClock(api, Temporal.Instant)`. Cases the test stack can't reach (test mode off, a locked database, reset effects on rows) are covered by the backend tests, which write the passcode and session rows directly.

**Cross-backend check.** `just test-cross` runs the Playwright projects `cross-write` and `cross-read` (`e2e/cross/`) on one test stack: write with fastapi, stop it, start spring on the same volume, read; then write with spring and read with fastapi. The writer saves the responses to a state file and the reader compares them; `cross-read` turns off the automatic reset (`resetDatabase: false`). nginx resolves `api` per request, so the switch needs no restart of `web`. It is part of `just test` and has its own CI job.

**Contract checks.** Schemathesis (pinned image, `schemathesis` service in the test overlay) runs after the API suite in `just test-api` (alone: `just test-contract`). It only covers operations listed in `e2e/schemathesis/schemathesis.toml`, and each slice adds its own there. The `ignored_auth` check and the generation of security parameters are off, because the test stack sets no passcode and is open by design.

**Business-rule cases.** `contract/cases/*.json` hold the cases for billing dates, cost normalization, allocation, payment planning and reminder planning (format, rules per file and how to compute each in `contract/cases/README.md`), validated against `contract/cases/schema/` by `just lint`.

**When suites run.** Static checks and the API suite run on every push, against both backends. The full browser suite runs only on main and the staging branch.

The `e2e/` folder holds both suites: `e2e/ui/` for browser flows and `e2e/api/` for the shared API suite.

### Repository layout (monorepo)

```
subtrack/
├── .github/workflows/       ci.yml (every push), e2e.yml (main, staging)
├── contract/                openapi.yaml, shared BR test cases, generated types (pnpm package)
├── db/                      migrations/ (SQL, the only schema owner), catalog/ (built-ins), scripts/ (generator, check)
├── frontend/                React + Vite app
├── backend-fastapi/         same module names as backend-spring
├── backend-spring/          api, service, billing, repository, persistence, scheduler, security, clock, config, cli
├── api-docs/                Swagger UI image for the dev profile
├── e2e/                     Playwright: api/ (shared API suite), ui/ (browser flows), docs/ (API docs check)
├── docs/                    srs.md, sdd.md, standards.md, adr/
├── docker-compose.yml       normal stack, profiles fastapi and spring
├── docker-compose.test.yml  test overlay: enables test mode
├── justfile                 single entry point for commands
└── pnpm-workspace.yaml      frontend, e2e, contract, db
```

Once the repository exists, its README files are the authoritative description of the folder structure; this list only covers the top level.

Everything lives in one repository, `subtrack`, with a root `justfile` as the single entry point for build, test and run commands.

## Sources

- [Logo.dev attribution rules](https://www.logo.dev/docs/platform/attribution)
- [Logo.dev vs Brandfetch](https://www.logo.dev/docs/brandfetch)
