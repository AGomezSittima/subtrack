package dev.subtrack.billing;

public record ReminderSettings(
    int defaultReminderDays,
    int trialReminderDays,
    int yearlyReminderDays,
    int endingReminderDays) {}
