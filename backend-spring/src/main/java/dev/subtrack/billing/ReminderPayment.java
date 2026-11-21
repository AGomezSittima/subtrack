package dev.subtrack.billing;

import java.time.LocalDate;

public record ReminderPayment(
    long id, long subscriptionId, LocalDate dueDate, long expectedCents, PaymentStatus status) {}
