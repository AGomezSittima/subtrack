# 0003. Dates and timestamps stored as canonical ISO text

Date: 2026-11-10 · Status: Accepted

## Context

SQLite has no date or timestamp type, and each driver picks its own default: the SQLite JDBC driver stores epoch milliseconds, and SQLAlchemy's default timestamp format is not ISO with `Z`. Data written by one backend must read identically in the other (NFR-9, [0001](0001-two-interchangeable-backends.md)). Billing rules work on calendar dates, not instants (BR-1 onwards).

## Decision

- Dates are stored as `YYYY-MM-DD` text, for example `2026-12-01`. Billing dates are plain dates, with no time or zone.
- Timestamps are stored as UTC text with millisecond precision and a `Z` suffix, for example `2026-11-10T15:18:00.000Z`.
- Each backend writes these through explicit converters: JPA `AttributeConverter`s for `LocalDate` and `Instant` in Spring, SQLAlchemy `Date` plus a `TypeDecorator` for timestamps in FastAPI. Driver defaults are never used.
- `CHECK` constraints on every date and timestamp column reject other formats on write.
- In the frontend, dates are Temporal values (`Temporal.PlainDate`, `Temporal.Instant`), never `Date`, outside the `DatePicker`.

## Alternatives

- **Driver defaults:** the two backends would write different representations of the same value.
- **Epoch integers:** unambiguous, but unreadable in the database, and a billing date is not an instant.

## Consequences

- The database is readable as is, and text order matches chronological order.
- Every new date or timestamp column needs a converter in both backends and a `CHECK` constraint in its migration.
- The integration suite checks this across backends: write through one, restart on the other, read it back identically.
- SDD: Date and timestamp storage. Standards: Dates, times and money.
