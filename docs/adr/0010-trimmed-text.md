# 0010. Text is trimmed by the frontend and checked, never changed, by the backends

Date: 2026-11-23 · Status: Accepted · Amends [0009](0009-null-follows-the-device-and-no-blank-text.md)

## Context

ADR 0009 made optional text null or non-blank, with "blank" meaning only Unicode `White_Space`. That left two gaps. Required names (`ServiceName`) still accepted `"   "`. And every text field accepted values with whitespace at either end (`" Netflix"`, `"Gym\n"`), which look identical to the trimmed value but compare, sort and search differently (FR-1, FR-47, FR-48, FR-26).

## Decision

- **One rule for every text field.** Text is not empty and doesn't start or end with whitespace, where whitespace is exactly space (U+0020), tab (U+0009), line feed (U+000A) and carriage return (U+000D). Anything else is allowed, including spaces, tabs and line breaks inside the value and other Unicode spaces such as U+00A0 anywhere. So a blank value (only those four characters) is invalid.
- **Contract.** One schema, `TrimmedText` (`minLength: 1` and the pattern `^[^ \t\n\r](?:[\s\S]*[^ \t\n\r])?$`). `ServiceName`, `PlanName`, `PaymentMethodLabel`, `Note` and `LogoDevToken` are `TrimmedText` plus their own `maxLength`. `""` is a `min_length` issue; any other violation is a `pattern` issue.
- **Backends reject, never repair.** They validate and store exactly what was sent: no trimming, no normalization. FastAPI: `TrimmedText` in `api/types.py`. Spring: `@MinCodePoints(1)` plus `@TrimmedText`.
- **The frontend trims.** Every text input goes through `trimText()` (only those four characters, not `String.prototype.trim`), and optional fields through `optionalText()`, which sends null when nothing is left.
- **Database.** Every text column with this rule checks `col <> '' AND trim(col, char(9, 10, 13, 32)) = col` (optional columns also allow null). V10 adds the checks and trims stored values. URLs and domains keep their own formats and 0009's non-blank check.

## Alternatives

- **Unicode `White_Space` at the ends, as in 0009:** every platform's set differs, so each layer needs a 25-character list; the four ASCII characters are what users actually type or paste.
- **Backends trim:** two implementations of a silent repair that must agree, and the stored value is no longer what the client sent.
- **Frontend trims, backends accept anything:** any other client (curl, Schemathesis, a future import) could store `" Netflix"`.

## Consequences

- One spelling for every value, and the four characters are spelled the same way in every layer.
- A lone U+00A0 or U+3000 is valid text again (0009 rejected it as blank).
- Plan names live inside the `default_plans` JSON, which a `CHECK` can't inspect, so only the API and `just lint` (the catalogue schema) enforce them there; V10 trims the stored ones.
- V10 stops, leaving the database unchanged, if a required name or plan name is only whitespace: there is no valid value to turn it into.
- SDD: Database schema, Migrations, API contract (Conventions), Frontend design. Standards: Text.
