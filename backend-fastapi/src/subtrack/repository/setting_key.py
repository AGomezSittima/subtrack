from enum import StrEnum


class SettingKey(StrEnum):
    DEFAULT_REMINDER_DAYS = "default_reminder_days"
    TRIAL_REMINDER_DAYS = "trial_reminder_days"
    YEARLY_REMINDER_DAYS = "yearly_reminder_days"
    ENDING_REMINDER_DAYS = "ending_reminder_days"
    PAYMENT_HORIZON_DAYS = "payment_horizon_days"
    LOGO_DEV_TOKEN = "logo_dev_token"  # noqa: S105
    LOCALE = "locale"
    THEME = "theme"
    FONT_SIZE = "font_size"
    REDUCED_MOTION = "reduced_motion"
    WEEK_START = "week_start"
    LOCK_POLICY = "lock_policy"
    HOME_TIME_ZONE = "home_time_zone"
    DISPLAY_TIME_ZONE = "display_time_zone"
    LAST_SCHEDULER_RUN = "last_scheduler_run"
    PASSCODE_HASH = "passcode_hash"
