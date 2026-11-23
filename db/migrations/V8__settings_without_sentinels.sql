CREATE TABLE setting_new (
    id         INTEGER PRIMARY KEY,
    key        TEXT    NOT NULL UNIQUE CHECK (key IN (
        'default_reminder_days', 'trial_reminder_days', 'yearly_reminder_days',
        'ending_reminder_days', 'payment_horizon_days', 'logo_dev_token', 'last_scheduler_run',
        'locale', 'theme', 'font_size', 'reduced_motion', 'week_start', 'passcode_hash',
        'lock_policy', 'failed_attempts', 'retry_after', 'home_time_zone', 'display_time_zone'
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
        WHEN 'logo_dev_token'        THEN value IS NULL OR trim(value, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) <> ''
        WHEN 'last_scheduler_run'    THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
        WHEN 'retry_after'           THEN value IS NULL OR (value GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z' AND strftime('%Y-%m-%dT%H:%M:%fZ', value) IS value)
        WHEN 'home_time_zone'        THEN value IS NULL OR (length(value) BETWEEN 1 AND 64 AND value GLOB '[A-Za-z]*' AND value NOT GLOB '*[^A-Za-z0-9/_+-]*' AND value NOT GLOB '*//*' AND value NOT GLOB '*/')
        WHEN 'display_time_zone'     THEN value IS NULL OR (length(value) BETWEEN 1 AND 64 AND value GLOB '[A-Za-z]*' AND value NOT GLOB '*[^A-Za-z0-9/_+-]*' AND value NOT GLOB '*//*' AND value NOT GLOB '*/')
    END, 0))
) STRICT;

INSERT INTO setting_new (id, key, value, updated_at)
    SELECT
        id,
        key,
        CASE
            WHEN key = 'display_time_zone' AND value = 'device' THEN NULL
            WHEN key = 'logo_dev_token' AND trim(value, char(9, 10, 11, 12, 13, 32, 133, 160, 5760, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8232, 8233, 8239, 8287, 12288)) = '' THEN NULL
            ELSE value
        END,
        updated_at
    FROM setting;

DROP TABLE setting;

ALTER TABLE setting_new RENAME TO setting;
