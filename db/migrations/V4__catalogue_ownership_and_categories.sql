CREATE TEMP TABLE v4_foreign_keys_must_be_off (
    foreign_keys INTEGER NOT NULL CHECK (foreign_keys = 0)
);
INSERT INTO v4_foreign_keys_must_be_off (foreign_keys) SELECT foreign_keys FROM pragma_foreign_keys;
DROP TABLE v4_foreign_keys_must_be_off;

CREATE TABLE catalog_service_new (
    id            INTEGER PRIMARY KEY,
    builtin_key   TEXT    UNIQUE
        CHECK (builtin_key IS NULL OR (builtin_key <> '' AND builtin_key NOT GLOB '*[^a-z0-9-]*')),
    name          TEXT    NOT NULL,
    domain        TEXT,
    category      TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'gaming', 'news', 'education', 'other')),
    default_plans TEXT    NOT NULL DEFAULT '[]'
        CHECK (json_valid(default_plans) AND json_type(default_plans) = 'array'),
    hidden        INTEGER NOT NULL DEFAULT 0 CHECK (hidden IN (0, 1)),
    created_at    TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at    TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    CONSTRAINT catalog_service_only_builtins_hidden CHECK (hidden = 0 OR builtin_key IS NOT NULL)
) STRICT;

INSERT INTO catalog_service_new (id, name, domain, category, default_plans, created_at, updated_at)
SELECT
    id,
    name,
    domain,
    category,
    default_plans,
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM catalog_service;

DROP TABLE catalog_service;

ALTER TABLE catalog_service_new RENAME TO catalog_service;

CREATE TABLE subscription_new (
    id                 INTEGER PRIMARY KEY,
    catalog_service_id INTEGER REFERENCES catalog_service (id) ON DELETE SET NULL,
    name               TEXT    NOT NULL,
    category           TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'gaming', 'news', 'education', 'other')),
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
    resumed_on         TEXT
        CHECK (resumed_on IS NULL OR (resumed_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(resumed_on) IS resumed_on)),
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
    unarchived_on      TEXT
        CHECK (unarchived_on IS NULL OR (unarchived_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(unarchived_on) IS unarchived_on)),
    created_at         TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at         TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    CONSTRAINT subscription_cancelled_has_dates CHECK (status <> 'cancelled' OR (cancelled_at IS NOT NULL AND ends_on IS NOT NULL)),
    CONSTRAINT subscription_paused_has_date CHECK (status <> 'paused' OR paused_at IS NOT NULL),
    CONSTRAINT subscription_trial_has_end_date CHECK (status <> 'trial' OR trial_end_date IS NOT NULL)
) STRICT;

INSERT INTO subscription_new (
    id,
    catalog_service_id,
    name,
    category,
    plan_name,
    price_cents,
    interval_count,
    interval_unit,
    anchor_date,
    status,
    trial_end_date,
    cancelled_at,
    paused_at,
    resumed_on,
    ends_on,
    payment_method,
    auto_confirm,
    reminder_days,
    website,
    cancel_url,
    notes,
    archived_at,
    unarchived_on,
    created_at,
    updated_at
)
SELECT
    id,
    catalog_service_id,
    name,
    category,
    plan_name,
    price_cents,
    interval_count,
    interval_unit,
    anchor_date,
    status,
    trial_end_date,
    cancelled_at,
    paused_at,
    resumed_on,
    ends_on,
    payment_method,
    auto_confirm,
    reminder_days,
    website,
    cancel_url,
    notes,
    archived_at,
    unarchived_on,
    created_at,
    updated_at
FROM subscription;

DROP TABLE subscription;

ALTER TABLE subscription_new RENAME TO subscription;

CREATE INDEX subscription_catalog_service_idx ON subscription (catalog_service_id);
