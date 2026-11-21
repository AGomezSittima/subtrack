# Shared business-rule cases

Input → expected output cases for the business rules in `docs/srs.md`. The pytest and JUnit 5 suites both load these files and run them against their billing core, so the two backends can't drift apart (NFR-6).

Adding or changing a business rule starts here: add or change its cases first, then make both backends pass.

## Files

| File                      | Rules              | Input → expected                                                                                     |
| ------------------------- | ------------------ | ---------------------------------------------------------------------------------------------------- |
| `billing-dates.json`      | BR-1, BR-2         | `{anchor, interval, count}` → `{dates}`: billing dates n = 1 to `count` (the anchor itself is n = 0) |
| `cost-normalization.json` | BR-4, BR-5, BR-22  | `{price_cents, interval}` → `{monthly_cents, yearly_cents}`                                          |
| `allocation.json`         | BR-4, BR-22, BR-26 | `{items: [{part, price_cents, interval}]}` → `{total_cents, parts: [{part, cents}]}`                 |

`interval` is always `{count, unit}` with `unit` one of `day`, `week`, `month`, `year`. Dates are `YYYY-MM-DD`; amounts are integer cents.

## Format

```json
{
  "$schema": "./schema/billing-dates.schema.json",
  "description": "What the rules in this file say.",
  "rules": ["BR-1", "BR-2"],
  "cases": [
    {
      "id": "srs-monthly-from-31st",
      "rules": ["BR-1", "BR-2"],
      "description": "Why this case exists.",
      "input": {
        "anchor": "2026-01-31",
        "interval": { "count": 1, "unit": "month" },
        "count": 3
      },
      "expected": { "dates": ["2026-02-28", "2026-03-31", "2026-04-30"] }
    }
  ]
}
```

- `id` is stable kebab-case, unique within its file. Test names in both backends include it, so never rename one; add a new case instead. Ids starting with `srs-` reproduce a table row or example from the SRS. The other cases are extra edge cases.
- `rules` at the top lists the rules the file covers. Each case lists the rules it checks, and every rule in the file appears in at least one case.
- Each file has a JSON Schema in `schema/` (shared definitions in `schema/common.schema.json`). `just lint` validates every file against its schema and checks unique ids and rule coverage (`scripts/validate-cases.ts`).

## How to compute the expected values

Implementations must follow these exactly, or the backends disagree by a cent.

- **Billing dates (BR-1, BR-2).** The n-th date is `anchor + n × interval`, computed from the anchor every time, never from the previous date. Months and years clamp to the last day of a shorter month. Days and weeks never clamp. 1 year is 12 months.
- **Exact arithmetic (BR-4, BR-22).** Monthly equivalent = price × factor ÷ count, where the factor per unit is 365/12 for `day`, 52/12 for `week`, 1 for `month` and 1/12 for `year`. Keep it as an exact fraction (Python `Fraction`, Java `BigInteger` numerator and denominator, or `BigDecimal` with no intermediate rounding), never a float. The yearly equivalent is the exact monthly × 12 (BR-5).
- **Rounding (BR-22).** Round once, at the end, half-up to the cent: 2.5 → 3. Banker's rounding (2.5 → 2) is wrong. Never compute one value from another value that was already rounded.
- **Allocation (BR-26).**
  1. Sum the exact monthly equivalents per `part` label. Parts appear in order of their first item.
  2. The total is the exact sum of all parts, rounded once half-up.
  3. Round every part down.
  4. Give the leftover cents (total − sum of the rounded-down parts), one each, to the parts with the largest remainders.
  5. When two remainders are equal, the part that appears first gets its cent first. The SRS example (12.96, 12.96, 12.95) implies this.

## Not here yet: payment generation and reminders

BR-6 to BR-17 and BR-21 to BR-24 are not expressed as cases yet. Writing them now would mean deciding behavior the SRS leaves open. See the data-foundation PR for the proposed format and the open questions. They will get `payment-generation.json` and `reminders.json` once those are answered.
