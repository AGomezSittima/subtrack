package dev.subtrack.service;

import org.jspecify.annotations.Nullable;

public record UserSettings(
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
    @Nullable String displayTimeZone) {}
