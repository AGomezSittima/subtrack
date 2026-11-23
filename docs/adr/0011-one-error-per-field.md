# 0011. One validation error per field, by precedence

Date: 2026-11-23 · Status: Accepted

## Context

The contract promised that every constraint failure is listed in `errors[]`. Pydantic stops at the first failed constraint of a field, Bean Validation reports all of them, so the backends answered differently for a value that is both too long and malformed: FastAPI returned `max_length`, Spring `max_length` and `pattern`. Spring compensated with validators that skipped values other validators would reject, which spread one rule across several classes.

## Decision

`errors[]` has one entry per invalid field (`location` + `pointer`), and every invalid field is listed. When a field fails several checks, the entry is the first in this order: missing (`required`), wrong type (`unknown_property`, `type`), length (`min_length`, `max_length`), format (`format`, `pattern`, `enum`), range (`minimum`, `maximum`), then `invalid`. The `code` enum in the contract is in this order. Both backends apply the rule where they build the problem (FastAPI `problem_response`, Spring `Problem.of`), so every error path follows it.

The existing exception stays: after a wrong type or unknown property, Jackson stops reading, so only the first may be listed.

## Alternatives

- **Every failure listed:** more information, but Pydantic can't produce it without re-running each constraint by hand.
- **Precedence inside each validator** (Spring skipping over-long or malformed values): what Spring did before; it hides the rule in validators that have to know about each other.

## Consequences

- A field gets one message, the most basic one: the user fixes the length before the format.
- Spring's validators are independent again; `@TrimmedText` and `@TimeZoneName` no longer skip values.
- An empty value is now `min_length` wherever the schema has `minLength` (time zone names were `pattern`).
- SDD: Errors. Contract: `Problem.errors`, `ValidationIssue.code`.
