package dev.subtrack.billing;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PlannedNotification(
    NotificationType type,
    Severity severity,
    LocalDate triggerDate,
    String dedupeKey,
    long subscriptionId,
    @Nullable Long paymentId,
    NotificationParams params) {}
