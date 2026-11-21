# Shared business-rule cases

Input → expected output cases for the business rules in `docs/srs.md`. The pytest and JUnit 5 suites both load these files and run them against their billing core, so the two backends can't drift apart (NFR-6).

Adding or changing a business rule starts here: add or change its cases first, then make both backends pass.

## Files

| File                      | Rules                                             | Input → expected                                                                                                  |
| ------------------------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `billing-dates.json`      | BR-1, BR-2                                        | `{anchor, interval, count}` → `{dates}`: billing dates n = 1 to `count` (the anchor itself is n = 0)              |
| `cost-normalization.json` | BR-4, BR-5, BR-22                                 | `{price_cents, interval}` → `{monthly_cents, yearly_cents}`                                                       |
| `allocation.json`         | BR-4, BR-22, BR-26                                | `{items: [{part, price_cents, interval}]}` → `{total_cents, parts: [{part, cents}]}`                              |
| `payment-generation.json` | BR-1, BR-6 to BR-12, BR-20, BR-21, BR-27          | `{today, settings, backfill, subscription, price_history, existing_payments}` → `{payments, subscription_status}` |
| `reminders.json`          | BR-13 to BR-17, BR-19, BR-20, BR-23, BR-24, BR-27 | `{last_run_date, today, settings, subscriptions, payments, existing_dedupe_keys}` → `{notifications}`             |

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
- `rules` at the top lists the rules the file covers. Each case lists the rules it checks, and every rule in the file appears in at least one case. Rule ids appear only in `rules`, never in a `description`.
- Each file has a JSON Schema in `schema/` (shared definitions in `schema/common.schema.json`). `just lint` validates every file against its schema and checks unique ids and rule coverage (`scripts/validate-cases.ts`).

## How to compute the expected values

Implementations must follow these exactly, or the backends disagree by a cent.

- **Billing dates (BR-1, BR-2).** The n-th date is `anchor + n × interval`, computed from the anchor every time, never from the previous date. Months and years clamp to the last day of a shorter month. Days and weeks never clamp. 1 year is 12 months.
- **Exact arithmetic (BR-4, BR-22).** Monthly equivalent = price × factor ÷ count, where the factor per unit is 365/12 for `day`, 52/12 for `week`, 1 for `month` and 1/12 for `year`. Keep it as an exact fraction (Python `Fraction`, Java `BigInteger` numerator and denominator, or `BigDecimal` with no intermediate rounding), never a float. The yearly equivalent is the exact monthly × 12 (BR-5).
- **Rounding (BR-22).** Round once, at the end, half-up to the cent: 2.5 → 3. Banker's rounding (2.5 → 2) is wrong. Never compute one value from another value that was already rounded.
- **Allocation (BR-26).**
  1. Sum the exact monthly equivalents per `part`.
  2. Put the parts in key order, whatever the input order: subscription ids numerically, labels (category keys, payment method labels) by Unicode code point, and the part without a label (`null`) last. The expected `parts` come in this order; the API may show them in another.
  3. The total is the exact sum of all parts, rounded once half-up.
  4. Round every part down.
  5. Give the leftover cents (total − sum of the rounded-down parts), one each, to the parts with the largest remainders.
  6. When two remainders are equal, the earlier part in key order gets its cent first.
- **Payment planning (BR-6 to BR-12, BR-27).** One call plans one subscription. The scheduler and every use case that changes a subscription call it, and `payments` is the full set afterwards, so each step below shows up in the result. Steps in order:
  1. **Status.** A `trial` whose `trial_end_date` is before `today` becomes `active`.
  2. **Remove.** Scheduled payments still `expected` are deleted when they are due after `paused_at` (status `paused`), on or after `ends_on` (status `cancelled`), or after `archived_on` (archived). Paid, skipped, failed, backfill and manual payments are never removed.
  3. **Reprice.** Scheduled payments still `expected` get the price in force on their due date: the latest `price_history` entry with `effective_from` ≤ due date.
  4. **Backfill.** Only with `backfill: true`: every billing date from the anchor (n = 0) to the day before `created_on` without a payment becomes `paid`, `source: backfill`, with `paid_cents` = the price in force and `paid_date` = the due date.
  5. **Generate.** Only for status `trial` or `active` and not archived: every billing date from the latest of `created_on`, `resumed_on` and `unarchived_on` to `today` + `payment_horizon_days`, both inclusive, that has no backfill or scheduled payment becomes `expected` at the price in force. Manual payments don't block a date.
  6. **Auto-confirm.** With `auto_confirm` on and not archived, every scheduled payment still `expected` with due date ≤ `today` becomes `paid` with `paid_cents` = `expected_cents` and `paid_date` = its due date. This applies while paused or cancelled too.

  Existing payments keep their `ref`; new ones have none. `payments` is ordered by due date.

- **Reminder planning (BR-13 to BR-17, BR-19, BR-23, BR-24, BR-27).** The input payments are those left after payment planning. The window runs from `last_run_date` (or `today` when it is null) to `today`, both inclusive, and only triggers inside it fire. Archived subscriptions get nothing.
  - **Renewal.** For each `expected` payment of a subscription that isn't paused, with due date ≥ `today`: it fires on due date − lead time. The lead time is `reminder_days` when set, else `yearly_reminder_days` for yearly-or-longer intervals (unit `year`, or unit `month` with count ≥ 12) and `default_reminder_days` otherwise. -1 means none. Its type is `yearly_renewal` for yearly-or-longer intervals, otherwise `renewal`.
  - **Overdue.** For each `expected` payment, whatever the subscription's status: it fires on due date + 1.
  - **Trial ending.** For status `trial` with `trial_end_date` ≥ `today`: it fires on `trial_end_date` − `trial_reminder_days`.
  - **Ending.** For status `cancelled` with `ends_on` ≥ `today`: it fires on `ends_on` − `ending_reminder_days`, and again on `ends_on`.
  - **Dedupe.** Each notification has `dedupe_key` = `type:subscription_id:trigger_date`. A key in `existing_dedupe_keys`, or one already produced in this call, creates nothing.
  - **Severity.** `overdue` is `critical`; `trial_ending` and `ending` are `warning`; `renewal` and `yearly_renewal` are `info`.
  - **Params.** `params` is `{name, date, amount_cents}`, where `date` is the event date. `amount_cents` is the payment's `expected_cents`, only on renewal, yearly renewal and overdue. `payment_id` is null for trial ending and ending.
  - **Order.** Notifications are ordered by trigger date, then subscription id, then type.

## Rules without case files

- **BR-25** (plain sums of prices and payments) gets cases with the statistics functions.
- **BR-28** (a trial has a trial end date) is a constraint, not a computation. The database, the contract and these case schemas enforce it.
- **BR-18** (the order of the scheduler's steps) belongs to the job, not to a pure function. Payment planning follows the same order inside one call.
