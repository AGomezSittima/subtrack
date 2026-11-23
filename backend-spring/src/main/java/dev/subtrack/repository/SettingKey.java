package dev.subtrack.repository;

import java.util.Locale;

public enum SettingKey {
  DEFAULT_REMINDER_DAYS,
  TRIAL_REMINDER_DAYS,
  YEARLY_REMINDER_DAYS,
  ENDING_REMINDER_DAYS,
  PAYMENT_HORIZON_DAYS,
  LOGO_DEV_TOKEN,
  LOCALE,
  THEME,
  FONT_SIZE,
  REDUCED_MOTION,
  WEEK_START,
  LOCK_POLICY,
  HOME_TIME_ZONE,
  DISPLAY_TIME_ZONE,
  LAST_SCHEDULER_RUN,
  PASSCODE_HASH;

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
