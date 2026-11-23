# 0008. Home and display time zones; "today" comes from the backend

Date: 2026-11-23 · Status: Accepted

## Context

"Today" was the date in a hard-coded Europe/Madrid (`APP_ZONE`, `AppZone`, `TZ` in compose), so the app was wrong for anyone elsewhere. Business rules depend on which calendar date today is (BR-3, BR-6, BR-13 to BR-19). Timestamps (created\_at, fired notifications, last\_scheduler\_run) are instants and only need a zone to be shown. The frontend also makes date-based decisions ("due today", "overdue", date-picker limits) that must agree with the backend's (FR-53, FR-54).

## Decision

- **Home time zone** (`home_time_zone`, an IANA name, initially null) decides which date is today for every business rule: due today, overdue, reminders, the scheduler's horizon and catch-up, creation dates. Only the backend uses it. While it is null, the backend uses UTC.
- **Display time zone** (`display_time_zone`: `device` or an IANA name, default `device`) is how timestamps are shown as clock times. Only the frontend uses it.
- Timestamps stay UTC in storage and on the wire. Plain dates are never converted anywhere.
- **"Today" comes from the backend.** `/health` returns `today`, the date of the injected clock's instant in the home zone. The frontend uses it for every date-based decision and never derives today from the device clock. There is one source of truth, and it follows the test clock.
- **First use.** If the home zone is null, the frontend saves the device's zone and shows a notice. No subscription can exist before the frontend has been opened, so nothing depends on the UTC fallback.
- **No recalculation.** Changing the home zone takes effect from the next request and scheduler run; stored dates stay as they are.
- Each backend validates names against its own zone database: Python's `tzdata` package, pinned and loaded explicitly so the container's OS doesn't matter, and the JDK's bundled tzdb. Offsets such as `+01:00` and `GMT+1` are not zone names and are rejected.

## Alternatives

- **A fixed zone in configuration or `TZ`:** one deployment-wide zone the user can't change, tied to the container.
- **Frontend computes today from the device clock:** two sources of truth that disagree near midnight or across devices, and the test clock (`POST /admin/clock`) wouldn't reach the browser.
- **One zone for both purposes:** a traveller would have to choose between correct due dates at home and local clock times on the road.
- **Recalculate stored dates when the home zone changes:** creation dates and `last_scheduler_run` dates would move under existing payments and notifications, risking duplicates or gaps for a rare action; idempotency (BR-7, BR-17) already absorbs a one-day shift.
- **Store timestamps in local time:** breaks canonical UTC text ([0003](0003-iso-text-dates-and-timestamps.md), NFR-9).

## Consequences

- The test clock now drives "today" in the browser too, so E2E tests can pin dates end to end.
- Python and Java ship different zone database versions (`tzdata` from PyPI, the JDK's tzdb). A zone added or renamed in a newer release may be valid in one backend and not yet in the other, and a rule change may give a different offset near a transition. Both are pinned; bump them together when possible.
- Every backend read of today goes through one cached `HomeZone`, which code that changes the setting must refresh (settings update, test reset, and later import).
- On the day the home zone changes, a reminder or overdue trigger may fire a day early or late, once. No duplicates are created.
- SDD: Backend design, Foundations, Scheduler, Database schema (setting), API contract, Frontend design, Testing, compose sketch. Standards: Dates, times and money.
