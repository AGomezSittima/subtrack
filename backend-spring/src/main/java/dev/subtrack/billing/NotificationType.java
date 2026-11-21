package dev.subtrack.billing;

import java.util.Locale;

public enum NotificationType {
  RENEWAL,
  YEARLY_RENEWAL,
  TRIAL_ENDING,
  ENDING,
  OVERDUE;

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
