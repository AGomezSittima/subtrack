package dev.subtrack.api;

import dev.subtrack.service.UserSettings;
import org.jspecify.annotations.Nullable;

public record SettingsResponse(
    int defaultReminderDays,
    int trialReminderDays,
    int yearlyReminderDays,
    int endingReminderDays,
    int paymentHorizonDays,
    @Nullable String logoDevToken,
    @Nullable String locale,
    String theme,
    String fontSize,
    String reducedMotion,
    int weekStart,
    String lockPolicy,
    @Nullable String homeTimeZone,
    @Nullable String displayTimeZone) {

  static SettingsResponse of(UserSettings settings) {
    return new SettingsResponse(
        settings.defaultReminderDays(),
        settings.trialReminderDays(),
        settings.yearlyReminderDays(),
        settings.endingReminderDays(),
        settings.paymentHorizonDays(),
        settings.logoDevToken(),
        settings.locale(),
        settings.theme(),
        settings.fontSize(),
        settings.reducedMotion(),
        settings.weekStart(),
        settings.lockPolicy(),
        settings.homeTimeZone(),
        settings.displayTimeZone());
  }
}
