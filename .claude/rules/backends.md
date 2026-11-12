---
paths:
  - "backend-fastapi/**/*"
  - "backend-spring/**/*"
---

# Backend rules (both backends)

Full detail: `docs/sdd.md` (Backend design, Privacy lock, Date and timestamp storage) and `docs/standards.md` (Backend standards, Security).

## Parity
- Both backends implement `contract/openapi.yaml` exactly and pass the same tests. A behavior difference is a bug unless the contract allows both.
- When you change one backend, check whether the other needs the same change, and say so if you don't make it.
- Same modules in both, so they compare file by file: `api`, `service`, `billing`, `repository`, `persistence`, `scheduler`, `security`, `clock`, `config`, `cli`.

## Layering
- `api`: translate HTTP to service calls and back. No logic. Errors become RFC 9457 problem JSON in one handler.
- `service`: one use case per method, one transaction each.
- `billing`: pure functions (dates, costs, allocation). Imports nothing from web, ORM or framework packages.
- `repository` / `persistence`: data access; ORM models and API DTOs are separate types.
- Dependencies point inward; wire concrete adapters in one place (FastAPI `Depends` module, Spring constructor injection only).

## Data
- Never change the schema from code. Spring: Flyway disabled, `ddl-auto=validate`. FastAPI: no `create_all`.
- Every connection: `PRAGMA foreign_keys = ON`, `journal_mode = WAL`, a `busy_timeout`.
- Dates as `YYYY-MM-DD` text, timestamps as UTC ISO text with milliseconds and `Z`, via explicit converters (Spring `AttributeConverter`, SQLAlchemy `TypeDecorator`). Never driver defaults.
- Money as integer cents (`int` / `long`). Inserts that must be idempotent use `INSERT … ON CONFLICT DO NOTHING`.

## Time and scheduling
- Get the current date/time only from the injected `Clock`. In test mode, `POST /admin/clock` sets it.
- The scheduler job is plain code in `scheduler`; the trigger (APScheduler / `@Scheduled`) only calls it. It runs at startup, hourly, and on `POST /admin/run-scheduler`.

## Security
- Lock enforcement lives in one place (FastAPI router dependency / Spring Security filter chain). New endpoints are protected by default.
- Passcodes hashed with Argon2id (PHC string); session tokens stored as SHA-256 hashes. Never log `/auth/*` request bodies.
- Test-only endpoints (`/admin/clock`, `/admin/reset`) return 404 unless `SUBTRACK_TEST_MODE=true`.
- The server makes no outbound network calls.

## Python / FastAPI
- uv with committed lockfile. Ruff for format and lint; full type hints, strict type checker.
- Pydantic models for the API; settings via pydantic-settings. stdlib `logging`, never `print`.

## Java / Spring Boot
- Gradle wrapper with version catalog. Spotless for formatting; compiler warnings as errors.
- Java records for DTOs; `ProblemDetail` via `@RestControllerAdvice`. SLF4J, never `System.out`.
- `sqlite-jdbc` + `hibernate-community-dialects` (`SQLiteDialect`), versions from Spring's BOM where possible.
