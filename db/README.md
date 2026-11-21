# Database migrations

`migrations/` holds the Flyway SQL migrations, the only place the SQLite schema changes. The `migrate` Compose service applies them to `/data/subs.db` on the `subs-data` volume before the backend starts.

- Naming: `V<n>__short_description.sql`. Never edit a migration that has been applied; add a new one.
- Money is `INTEGER` cents; dates and timestamps are `TEXT` with `CHECK` constraints on their format.
- Spring runs with Flyway disabled and `ddl-auto=validate`; FastAPI never calls `create_all`.
- Tables are `STRICT`. Every date column uses `CHECK (col GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(col) IS col)` and every timestamp column the matching `strftime('%Y-%m-%dT%H:%M:%fZ', col) IS col` check (exact patterns in `docs/sdd.md`, Date and timestamp storage). Copy them as they are; `IS` matters, since a `CHECK` that evaluates to NULL passes.
- `backend-fastapi/tests/integration/test_migrations.py` applies the migrations to a fresh file and checks the constraints; extend it when a migration adds a table or a check.
