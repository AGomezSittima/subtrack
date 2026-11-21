-- V1: initial schema (docs/sdd.md, Database schema).
-- Flyway applies this file; backends never create or alter tables.
--
-- Conventions used by every table:
--   * STRICT tables, so a column only ever holds its declared type ('abc' never lands in an
--     INTEGER column, where it would also pass a `>= 0` check).
--   * Money is INTEGER cents with CHECK (... >= 0).
--   * Dates are TEXT `YYYY-MM-DD`. Every date column uses this exact check (shape, then a
--     round-trip through date() that rejects impossible dates such as 2026-02-30):
--       CHECK (col GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(col) IS col)
--   * Timestamps are TEXT, UTC with milliseconds and `Z`. Every timestamp column uses:
--       CHECK (col GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'
--              AND strftime('%Y-%m-%dT%H:%M:%fZ', col) IS col)
--     `IS`, not `=`: date() returns NULL for 2026-13-45, and a CHECK whose result is NULL passes.
--   * Nullable date and timestamp columns wrap the check in `col IS NULL OR (...)`.
--   * Timestamps have no DEFAULT: backends write them from their injected clock.
--   * Closed sets (statuses, units, types) are CHECK ... IN (...). Adding a value later needs a
--     migration that rebuilds the table, as SQLite can't alter a CHECK constraint.

-- Catalogue of known services, used to prefill new subscriptions (FR-2). Seeded by migrations.
CREATE TABLE catalog_service (
    id            INTEGER PRIMARY KEY,
    name          TEXT    NOT NULL UNIQUE,
    domain        TEXT    NOT NULL,
    category      TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'other')),
    -- [{"plan": "Individual", "price_cents": 1199, "interval_count": 1, "interval_unit": "month"}]
    default_plans TEXT    NOT NULL DEFAULT '[]'
        CHECK (json_valid(default_plans) AND json_type(default_plans) = 'array')
) STRICT;

CREATE TABLE subscription (
    id                 INTEGER PRIMARY KEY,
    catalog_service_id INTEGER REFERENCES catalog_service (id) ON DELETE SET NULL,
    name               TEXT    NOT NULL,
    category           TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'other')),
    plan_name          TEXT,
    -- Current price; mirrors the latest price_history row.
    price_cents        INTEGER NOT NULL CHECK (price_cents >= 0),
    interval_count     INTEGER NOT NULL CHECK (interval_count >= 1),
    interval_unit      TEXT    NOT NULL CHECK (interval_unit IN ('day', 'week', 'month', 'year')),
    anchor_date        TEXT    NOT NULL
        CHECK (anchor_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(anchor_date) IS anchor_date),
    status             TEXT    NOT NULL CHECK (status IN ('trial', 'active', 'paused', 'cancelled')),
    trial_end_date     TEXT
        CHECK (trial_end_date IS NULL OR (trial_end_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(trial_end_date) IS trial_end_date)),
    cancelled_at       TEXT
        CHECK (cancelled_at IS NULL OR (cancelled_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(cancelled_at) IS cancelled_at)),
    paused_at          TEXT
        CHECK (paused_at IS NULL OR (paused_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(paused_at) IS paused_at)),
    -- Last day of access after cancelling (FR-3, BR-10).
    ends_on            TEXT
        CHECK (ends_on IS NULL OR (ends_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(ends_on) IS ends_on)),
    payment_method     TEXT,
    auto_confirm       INTEGER NOT NULL DEFAULT 0 CHECK (auto_confirm IN (0, 1)),
    -- NULL = global default, -1 = off (BR-13).
    reminder_days      INTEGER CHECK (reminder_days IS NULL OR reminder_days >= -1),
    website            TEXT,
    cancel_url         TEXT,
    notes              TEXT,
    -- Set = archived (FR-28).
    archived_at        TEXT
        CHECK (archived_at IS NULL OR (archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', archived_at) IS archived_at)),
    created_at         TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at         TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    -- A cancelled subscription always knows when it was cancelled and when access ends (BR-10).
    CONSTRAINT subscription_cancelled_has_dates CHECK (status <> 'cancelled' OR (cancelled_at IS NOT NULL AND ends_on IS NOT NULL)),
    -- A paused subscription always knows since when (BR-10).
    CONSTRAINT subscription_paused_has_date CHECK (status <> 'paused' OR paused_at IS NOT NULL)
) STRICT;

-- Price changes with the date they take effect (FR-6, BR-8, BR-9).
CREATE TABLE price_history (
    id              INTEGER PRIMARY KEY,
    subscription_id INTEGER NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    price_cents     INTEGER NOT NULL CHECK (price_cents >= 0),
    effective_from  TEXT    NOT NULL
        CHECK (effective_from GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(effective_from) IS effective_from),
    created_at      TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    UNIQUE (subscription_id, effective_from)
) STRICT;

CREATE TABLE payment (
    id              INTEGER PRIMARY KEY,
    subscription_id INTEGER NOT NULL REFERENCES subscription (id) ON DELETE CASCADE,
    due_date        TEXT    NOT NULL
        CHECK (due_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(due_date) IS due_date),
    -- Price in force on due_date (BR-8).
    expected_cents  INTEGER NOT NULL CHECK (expected_cents >= 0),
    paid_cents      INTEGER CHECK (paid_cents IS NULL OR paid_cents >= 0),
    paid_date       TEXT
        CHECK (paid_date IS NULL OR (paid_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(paid_date) IS paid_date)),
    status          TEXT    NOT NULL CHECK (status IN ('expected', 'paid', 'skipped', 'failed')),
    source          TEXT    NOT NULL CHECK (source IN ('scheduled', 'backfill', 'manual')),
    note            TEXT,
    created_at      TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at      TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    -- Paid amount and date are recorded exactly when the payment is paid.
    CONSTRAINT payment_paid_fields CHECK (
        (status = 'paid' AND paid_cents IS NOT NULL AND paid_date IS NOT NULL)
        OR (status <> 'paid' AND paid_cents IS NULL AND paid_date IS NULL)
    )
) STRICT;

-- One generated payment per billing date, so generation is idempotent (BR-7); manual payments are exempt.
CREATE UNIQUE INDEX payment_generated_unique_idx ON payment (subscription_id, due_date) WHERE source != 'manual';
-- Payments of one subscription in date order; also serves the ON DELETE CASCADE lookup.
CREATE INDEX payment_subscription_due_date_idx ON payment (subscription_id, due_date);
-- Global payment lists by date range, upcoming payments and monthly statistics.
CREATE INDEX payment_due_date_idx ON payment (due_date);
-- Expected payments by date: overdue, auto-confirm and pending-payment scans.
CREATE INDEX payment_status_due_date_idx ON payment (status, due_date);

CREATE TABLE notification (
    id              INTEGER PRIMARY KEY,
    type            TEXT    NOT NULL
        CHECK (type IN ('renewal', 'trial_ending', 'yearly_renewal', 'overdue', 'ending')),
    severity        TEXT    NOT NULL CHECK (severity IN ('info', 'warning', 'critical')),
    subscription_id INTEGER REFERENCES subscription (id) ON DELETE SET NULL,
    payment_id      INTEGER REFERENCES payment (id) ON DELETE SET NULL,
    -- The day it was due to fire.
    trigger_date    TEXT    NOT NULL
        CHECK (trigger_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(trigger_date) IS trigger_date),
    -- Creating the same notification twice is a no-op (BR-17).
    dedupe_key      TEXT    NOT NULL UNIQUE,
    -- Message parameters, e.g. {"name": "Spotify", "date": "2026-12-01", "amount_cents": 1199}.
    params          TEXT    NOT NULL CHECK (json_valid(params) AND json_type(params) = 'object'),
    read_at         TEXT
        CHECK (read_at IS NULL OR (read_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', read_at) IS read_at)),
    created_at      TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at      TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at)
) STRICT;

-- Unread notifications newest first, and the unread badge count (FR-17).
CREATE INDEX notification_unread_idx ON notification (trigger_date) WHERE read_at IS NULL;
-- Serves ON DELETE SET NULL when a subscription is deleted.
CREATE INDEX notification_subscription_idx ON notification (subscription_id);
-- Serves ON DELETE SET NULL when a payment is deleted.
CREATE INDEX notification_payment_idx ON notification (payment_id);
-- Serves ON DELETE SET NULL when a catalogue entry is removed.
CREATE INDEX subscription_catalog_service_idx ON subscription (catalog_service_id);

-- Key-value settings. NULL means "not set". The key list is closed and each value is checked;
-- adding a key needs a migration that rebuilds this (small, unreferenced) table.
CREATE TABLE setting (
    id         INTEGER PRIMARY KEY,
    key        TEXT    NOT NULL UNIQUE CHECK (key IN (
        'default_reminder_days', 'trial_reminder_days', 'yearly_reminder_days',
        'ending_reminder_days', 'payment_horizon_days', 'logo_dev_token', 'last_scheduler_run',
        'locale', 'theme', 'font_size', 'reduced_motion', 'week_start', 'passcode_hash',
        'lock_policy', 'failed_attempts', 'retry_after'
    )),
    value      TEXT,
    updated_at TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    -- coalesce(..., 0): a CHECK that evaluates to NULL passes, so a NULL value for a key that
    -- needs one (e.g. theme) must turn into a failure.
    CONSTRAINT setting_value_valid CHECK (coalesce(CASE key
        -- Lead times in days (0 to 365) and the payment horizon (1 to 366): decimal digits only.
        WHEN 'default_reminder_days' THEN value NOT GLOB '*[^0-9]*' AND value <> '' AND CAST(value AS INTEGER) BETWEEN 0 AND 365
        WHEN 'trial_reminder_days'   THEN value NOT GLOB '*[^0-9]*' AND value <> '' AND CAST(value AS INTEGER) BETWEEN 0 AND 365
        WHEN 'yearly_reminder_days'  THEN value NOT GLOB '*[^0-9]*' AND value <> '' AND CAST(value AS INTEGER) BETWEEN 0 AND 365
        WHEN 'ending_reminder_days'  THEN value NOT GLOB '*[^0-9]*' AND value <> '' AND CAST(value AS INTEGER) BETWEEN 0 AND 365
        WHEN 'payment_horizon_days'  THEN value NOT GLOB '*[^0-9]*' AND value <> '' AND CAST(value AS INTEGER) BETWEEN 1 AND 366
        WHEN 'failed_attempts'       THEN value NOT GLOB '*[^0-9]*' AND value <> ''
        WHEN 'week_start'            THEN value IN ('1', '2', '3', '4', '5', '6', '7')
        WHEN 'locale'                THEN value IS NULL OR value IN ('en', 'es')
        WHEN 'theme'                 THEN value IN ('system', 'light', 'dark')
        WHEN 'font_size'             THEN value IN ('sm', 'md', 'lg')
        WHEN 'reduced_motion'        THEN value IN ('system', 'always')
        WHEN 'lock_policy'           THEN value IN ('on_open', 'idle_5', 'idle_15', 'idle_30', 'idle_60', 'manual')
        -- Argon2id PHC string, e.g. $argon2id$v=19$m=...,t=...,p=...$salt$hash
        WHEN 'passcode_hash'         THEN value IS NULL OR value GLOB '$argon2id$*'
        WHEN 'logo_dev_token'        THEN value IS NULL OR value <> ''
        WHEN 'last_scheduler_run'    THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
        WHEN 'retry_after'           THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
    END, 0))
) STRICT;

-- Default settings (docs/sdd.md, setting). The seed rows are the only timestamps not written by a
-- backend clock: they record when the migration ran.
INSERT INTO setting (key, value, updated_at) VALUES
    ('default_reminder_days', '3',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('trial_reminder_days',   '3',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('yearly_reminder_days',  '14',      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('ending_reminder_days',  '7',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('payment_horizon_days',  '60',      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('logo_dev_token',        NULL,      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('last_scheduler_run',    NULL,      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('locale',                NULL,      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('theme',                 'system',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('font_size',             'md',      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('reduced_motion',        'system',  strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('week_start',            '1',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('passcode_hash',         NULL,      strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('lock_policy',           'idle_15', strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('failed_attempts',       '0',       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('retry_after',           NULL,      strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- Unlocked sessions of the privacy lock. Only the token's SHA-256 is stored; the token itself
-- lives only in the subtrack_session cookie.
CREATE TABLE session (
    id               INTEGER PRIMARY KEY,
    -- Lowercase hex SHA-256: 64 characters from [0-9a-f].
    token_hash       TEXT    NOT NULL UNIQUE
        CHECK (length(token_hash) = 64 AND token_hash NOT GLOB '*[^0-9a-f]*'),
    created_at       TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    -- Updated only by user-driven requests (not X-Subtrack-Background ones).
    last_activity_at TEXT    NOT NULL
        CHECK (last_activity_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', last_activity_at) IS last_activity_at)
) STRICT;
