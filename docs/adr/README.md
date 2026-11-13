# Architecture decision records

Short records of decisions that shape Subtrack: what was decided, why, and what it costs. The SRS and SDD describe the system as it is now and are edited in place; ADRs keep the reasoning, so a later change can see what it is undoing.

## When to write one

- A decision changes the SDD or `docs/standards.md` (for example, replacing a pinned stack piece).
- A standard is broken on purpose and the reason should outlast the PR.
- A choice between real alternatives that someone would otherwise ask about later.

Routine work (a new endpoint, a version bump within the same major, a bug fix) does not need one.

## How

1. Copy [`template.md`](template.md) to `NNNN-short-title.md`, with the next free number.
2. Fill it in briefly; link requirement IDs (FR-x, BR-x, NFR-x) and the SDD sections it affects.
3. Update the SRS or SDD in the same PR, and add a row to the index below.

Accepted records are not rewritten. To change a decision, write a new ADR that supersedes the old one, and set the old one's status to `Superseded by NNNN`.

## Index

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-two-interchangeable-backends.md) | Two interchangeable backends behind one contract | Accepted |
| [0002](0002-sqlite-with-flyway-migrations.md) | SQLite file, schema owned by Flyway SQL migrations | Accepted |
| [0003](0003-iso-text-dates-and-timestamps.md) | Dates and timestamps stored as canonical ISO text | Accepted |
| [0004](0004-privacy-lock-scope.md) | The privacy lock is a privacy screen, not data protection | Accepted |

0001 to 0004 record decisions taken on 2026-11-10, when the SRS and SDD were written, before this log existed.
