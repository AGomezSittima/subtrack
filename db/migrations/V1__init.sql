-- Date and timestamp checks compare with IS, not =: a CHECK that evaluates to NULL passes.

CREATE TABLE catalog_service (
    id            INTEGER PRIMARY KEY,
    name          TEXT    NOT NULL UNIQUE,
    domain        TEXT    NOT NULL,
    category      TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'other')),
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
    ends_on            TEXT
        CHECK (ends_on IS NULL OR (ends_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(ends_on) IS ends_on)),
    payment_method     TEXT,
    auto_confirm       INTEGER NOT NULL DEFAULT 0 CHECK (auto_confirm IN (0, 1)),
    reminder_days      INTEGER CHECK (reminder_days IS NULL OR reminder_days >= -1),
    website            TEXT,
    cancel_url         TEXT,
    notes              TEXT,
    archived_at        TEXT
        CHECK (archived_at IS NULL OR (archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', archived_at) IS archived_at)),
    created_at         TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at         TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    CONSTRAINT subscription_cancelled_has_dates CHECK (status <> 'cancelled' OR (cancelled_at IS NOT NULL AND ends_on IS NOT NULL)),
    CONSTRAINT subscription_paused_has_date CHECK (status <> 'paused' OR paused_at IS NOT NULL)
) STRICT;

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
    CONSTRAINT payment_paid_fields CHECK (
        (status = 'paid' AND paid_cents IS NOT NULL AND paid_date IS NOT NULL)
        OR (status <> 'paid' AND paid_cents IS NULL AND paid_date IS NULL)
    )
) STRICT;

CREATE UNIQUE INDEX payment_generated_unique_idx ON payment (subscription_id, due_date) WHERE source != 'manual';
CREATE INDEX payment_subscription_due_date_idx ON payment (subscription_id, due_date);
CREATE INDEX payment_due_date_idx ON payment (due_date);
CREATE INDEX payment_status_due_date_idx ON payment (status, due_date);

CREATE TABLE notification (
    id              INTEGER PRIMARY KEY,
    type            TEXT    NOT NULL
        CHECK (type IN ('renewal', 'trial_ending', 'yearly_renewal', 'overdue', 'ending')),
    severity        TEXT    NOT NULL CHECK (severity IN ('info', 'warning', 'critical')),
    subscription_id INTEGER REFERENCES subscription (id) ON DELETE SET NULL,
    payment_id      INTEGER REFERENCES payment (id) ON DELETE SET NULL,
    trigger_date    TEXT    NOT NULL
        CHECK (trigger_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(trigger_date) IS trigger_date),
    dedupe_key      TEXT    NOT NULL UNIQUE,
    params          TEXT    NOT NULL CHECK (json_valid(params) AND json_type(params) = 'object'),
    read_at         TEXT
        CHECK (read_at IS NULL OR (read_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', read_at) IS read_at)),
    created_at      TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at      TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at)
) STRICT;

CREATE INDEX notification_unread_idx ON notification (trigger_date) WHERE read_at IS NULL;
CREATE INDEX notification_subscription_idx ON notification (subscription_id);
CREATE INDEX notification_payment_idx ON notification (payment_id);
CREATE INDEX subscription_catalog_service_idx ON subscription (catalog_service_id);

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
    -- coalesce(..., 0): a CHECK that evaluates to NULL passes, so a missing required value must fail.
    CONSTRAINT setting_value_valid CHECK (coalesce(CASE key
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
        WHEN 'passcode_hash'         THEN value IS NULL OR value GLOB '$argon2id$*'
        WHEN 'logo_dev_token'        THEN value IS NULL OR value <> ''
        WHEN 'last_scheduler_run'    THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
        WHEN 'retry_after'           THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
    END, 0))
) STRICT;

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

CREATE TABLE session (
    id               INTEGER PRIMARY KEY,
    token_hash       TEXT    NOT NULL UNIQUE
        CHECK (length(token_hash) = 64 AND token_hash NOT GLOB '*[^0-9a-f]*'),
    created_at       TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    last_activity_at TEXT    NOT NULL
        CHECK (last_activity_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', last_activity_at) IS last_activity_at)
) STRICT;
