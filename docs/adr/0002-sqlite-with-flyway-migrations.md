# 0002. SQLite file, schema owned by Flyway SQL migrations

Date: 2026-11-10 · Status: Accepted

## Context

Subtrack is local and single-user, started and stopped with one command (NFR-1), and its data must survive stops, restarts and rebuilds (NFR-2). The data store must not be reachable from outside the stack (NFR-3). Both backends read and write the same data (NFR-6, [0001](0001-two-interchangeable-backends.md)), so neither ORM can be the one that owns the schema.

## Decision

- Data lives in one SQLite file, `subs.db`, on the named volume `subs-data`, mounted only into the migration job and the active backend.
- The schema is plain SQL in `db/migrations/V<n>__description.sql`, applied by a one-shot Flyway container (`migrate`) before the backend starts.
- Backends never create or alter tables: Spring runs with Flyway disabled and `ddl-auto=validate`, FastAPI never calls `create_all`. A mismatch fails at startup.
- Every connection sets `PRAGMA foreign_keys = ON`, `journal_mode = WAL` and a `busy_timeout`.

## Alternatives

- **A database server container (for example PostgreSQL):** one more service to run and secure, for a single user; SQLite is a file, so there is nothing to expose.
- **Migrations owned by one backend (Alembic, or Flyway inside Spring):** would make the other backend depend on it to have a schema at all.
- **ORM schema generation (`create_all`, `ddl-auto=update`):** two ORMs would generate different schemas.

## Consequences

- The schema is the same whichever backend runs, and the migration history is plain SQL that both can be validated against.
- SQLite limits apply: one writer at a time (fine for one user), and few column types; dates need an explicit format ([0003](0003-iso-text-dates-and-timestamps.md)).
- Spring needs `sqlite-jdbc` and the Hibernate community SQLite dialect.
- Backup is copying `subs.db` out of the volume while the stack is stopped, or the export endpoint.
- SDD: Design overview, Architecture and deployment, Migrations and ownership.
