# 0009. Null follows the device; optional text is null or non-blank

Date: 2026-11-23 · Status: Accepted, amended by [0010](0010-trimmed-text.md) · Amends [0008](0008-configurable-time-zones.md)

## Context

The "No sentinel values" standard (a value never stands for something other than itself) left two exceptions in the contract and schema. `display_time_zone` held either an IANA name or the word `device`, so one string field mixed a zone with a mode. Optional free text (`notes`, payment `note`) accepted `""` and whitespace as well as `null`, so "no notes" had several spellings, and `plan_name`, `payment_method` and `logo_dev_token` accepted whitespace-only values (FR-1, FR-9, FR-54).

## Decision

- **`display_time_zone` is an IANA name or null**, and null means "follow the device". The default is null; V8 turns a stored `device` into null. The DB check for both zone keys is the shape of a name only; `device` is no longer special anywhere.
- **Following the device or system:** free-form value fields use null (`locale`, `display_time_zone`); enum fields use an explicit option (`theme: system`, `reduced_motion: system`).
- **Optional text is null or non-blank.** A value must contain at least one character outside Unicode `White_Space` (U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F, U+3000). This applies to `PlanName`, `PaymentMethodLabel`, `Note` and `LogoDevToken` in the contract (one `pattern`), and to every optional text column in the database (`CHECK (col IS NULL OR trim(col, <those characters>) <> '')`). V9 turns stored blanks into null. Values are stored as sent; surrounding whitespace is not trimmed.
- The character list is spelled out in every layer (contract pattern, Pydantic, Java, SQLite, frontend) because each platform's `\s` or `isspace` differs: Java's `\s` is ASCII-only by default, Python's `isspace` adds U+001C–U+001F, JavaScript's `\s` adds U+FEFF and drops U+0085, and SQLite's `trim` removes only spaces unless given a list.
- The frontend sends null for an empty or whitespace-only optional field (`optionalText` in `src/lib/text/`).

## Alternatives

- **Keep `device`:** a mode hidden inside a zone field; every reader has to know the magic word.
- **A separate `display_time_zone_mode` enum plus a nullable zone:** two fields for one choice, with an invalid combination (mode `fixed`, zone null) to guard against.
- **Each platform's own `\s`:** simpler patterns, but the two backends and the database would disagree on values such as a lone U+00A0 or U+001C.
- **Trim values before storing:** changes what the user typed and isn't needed to remove the sentinel.
- **`minLength: 1` only:** stops `""` but still accepts `"   "`.

## Consequences

- One spelling for "none" in every optional text field, and one for "follow the device".
- The whitespace list is duplicated in five places; a change must touch them all, and the migration tests and the shared API suite check that they agree.
- In Spring, `@NonBlankText` leaves over-long values to `@MaxCodePoints`, because Pydantic checks the length first and reports one issue.
- SDD: Database schema (setting, subscription, payment, catalog\_service, migrations), API contract (Conventions), Frontend design. Standards: Principles. SRS: FR-54.
