CREATE TEMP TABLE v9_foreign_keys_must_be_off (
    foreign_keys INTEGER NOT NULL CHECK (foreign_keys = 0)
);
INSERT INTO v9_foreign_keys_must_be_off (foreign_keys) SELECT foreign_keys FROM pragma_foreign_keys;
DROP TABLE v9_foreign_keys_must_be_off;

CREATE TABLE catalog_service_new (
    id            INTEGER PRIMARY KEY,
    builtin_key   TEXT    UNIQUE
        CHECK (builtin_key IS NULL OR (builtin_key <> '' AND builtin_key NOT GLOB '*[^a-z0-9-]*')),
    name          TEXT    NOT NULL,
    domain        TEXT
        CHECK (domain IS NULL OR trim(domain, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
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

INSERT INTO catalog_service_new (
    id,
    builtin_key,
    name,
    domain,
    category,
    default_plans,
    hidden,
    created_at,
    updated_at
)
SELECT
    id,
    builtin_key,
    name,
    CASE WHEN trim(domain, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE domain END,
    category,
    default_plans,
    hidden,
    created_at,
    updated_at
FROM catalog_service;

DROP TABLE catalog_service;

ALTER TABLE catalog_service_new RENAME TO catalog_service;

CREATE TABLE subscription_new (
    id                 INTEGER PRIMARY KEY,
    catalog_service_id INTEGER REFERENCES catalog_service (id) ON DELETE SET NULL,
    name               TEXT    NOT NULL,
    category           TEXT    NOT NULL
        CHECK (category IN ('music', 'video', 'vpn', 'cloud', 'ai', 'software', 'domains', 'gaming', 'news', 'education', 'other')),
    plan_name          TEXT
        CHECK (plan_name IS NULL OR trim(plan_name, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
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
    payment_method     TEXT
        CHECK (payment_method IS NULL OR trim(payment_method, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
    auto_confirm       INTEGER NOT NULL DEFAULT 0 CHECK (auto_confirm IN (0, 1)),
    reminders_enabled  INTEGER NOT NULL DEFAULT 1 CHECK (reminders_enabled IN (0, 1)),
    reminder_days      INTEGER CHECK (reminder_days IS NULL OR reminder_days BETWEEN 0 AND 365),
    website            TEXT
        CHECK (website IS NULL OR trim(website, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
    cancel_url         TEXT
        CHECK (cancel_url IS NULL OR trim(cancel_url, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
    notes              TEXT
        CHECK (notes IS NULL OR trim(notes, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
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
    reminders_enabled,
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
    CASE WHEN trim(plan_name, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE plan_name END,
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
    CASE WHEN trim(payment_method, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE payment_method END,
    auto_confirm,
    reminders_enabled,
    reminder_days,
    CASE WHEN trim(website, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE website END,
    CASE WHEN trim(cancel_url, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE cancel_url END,
    CASE WHEN trim(notes, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE notes END,
    archived_at,
    unarchived_on,
    created_at,
    updated_at
FROM subscription;

DROP TABLE subscription;

ALTER TABLE subscription_new RENAME TO subscription;

CREATE INDEX subscription_catalog_service_idx ON subscription (catalog_service_id);

CREATE TABLE payment_new (
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
    note            TEXT
        CHECK (note IS NULL OR trim(note, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''),
    created_at      TEXT    NOT NULL
        CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS created_at),
    updated_at      TEXT    NOT NULL
        CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', updated_at) IS updated_at),
    CONSTRAINT payment_paid_fields CHECK (
        (status = 'paid' AND paid_cents IS NOT NULL AND paid_date IS NOT NULL)
        OR (status <> 'paid' AND paid_cents IS NULL AND paid_date IS NULL)
    )
) STRICT;

INSERT INTO payment_new (
    id,
    subscription_id,
    due_date,
    expected_cents,
    paid_cents,
    paid_date,
    status,
    source,
    note,
    created_at,
    updated_at
)
SELECT
    id,
    subscription_id,
    due_date,
    expected_cents,
    paid_cents,
    paid_date,
    status,
    source,
    CASE WHEN trim(note, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL ELSE note END,
    created_at,
    updated_at
FROM payment;

DROP TABLE payment;

ALTER TABLE payment_new RENAME TO payment;

CREATE UNIQUE INDEX payment_generated_unique_idx ON payment (subscription_id, due_date) WHERE source != 'manual';
CREATE INDEX payment_subscription_due_date_idx ON payment (subscription_id, due_date);
CREATE INDEX payment_due_date_idx ON payment (due_date);
CREATE INDEX payment_status_due_date_idx ON payment (status, due_date);
