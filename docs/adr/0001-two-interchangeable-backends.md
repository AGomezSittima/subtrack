# 0001. Two interchangeable backends behind one contract

Date: 2026-11-10 · Status: Accepted

## Context

Subtrack is also a learning project: one of its goals is to compare two backend stacks on identical requirements and identical tests (SRS, Goals). For the comparison to mean anything, the two must be swappable without the user or the frontend noticing (NFR-6).

## Decision

- Two backends, FastAPI (Python) and Spring Boot (Java), implement the same API and the same modules: `api`, `service`, `billing`, `repository`, `persistence`, `scheduler`, `security`, `clock`, `config`, `cli`.
- `contract/openapi.yaml` is the source of truth. API changes start there, then both backends and the frontend follow, ideally in one PR.
- Both answer on the Compose network alias `api`, one at a time (profiles `fastapi` and `spring`), and share the same SQLite file, so switching needs no migration.
- Parity is enforced by tests, not by review: the shared API suite (`e2e/api/`) and the business-rule cases in `contract/cases/*.json` run against both. A behavior difference is a bug unless the contract allows both.

## Alternatives

- **One backend:** simpler, but drops the comparison goal entirely.
- **Two backends with their own APIs or databases:** easier to build, but nothing would then show that they behave the same, and switching would need a data migration.

## Consequences

- Every feature is built twice, and CI runs the API suite once per backend.
- Generated types from the contract keep the frontend independent of the backend in use.
- Schema, date storage and other shared formats must be owned outside both backends; see [0002](0002-sqlite-with-flyway-migrations.md) and [0003](0003-iso-text-dates-and-timestamps.md).
- SDD: Design overview, API contract, Backend design, Testing and repository layout.
