# Database migrations

`migrations/` holds the Flyway SQL migrations, the only place the SQLite schema changes. The `migrate` Compose service applies them to `/data/subs.db` on the `subs-data` volume before the backend starts.

- Naming: `V<n>__short_description.sql`. Never edit a migration that has been applied; add a new one.
- Money is `INTEGER` cents; dates and timestamps are `TEXT` with `CHECK` constraints on their format.
- Spring runs with Flyway disabled and `ddl-auto=validate`; FastAPI never calls `create_all`.
