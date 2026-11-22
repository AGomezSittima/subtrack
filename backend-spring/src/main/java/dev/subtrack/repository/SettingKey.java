package dev.subtrack.repository;

import java.util.Locale;

public enum SettingKey {
  LAST_SCHEDULER_RUN,
  PASSCODE_HASH,
  LOCK_POLICY;

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
