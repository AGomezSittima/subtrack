package dev.subtrack.billing;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record Payment(
    @Nullable Long id,
    LocalDate dueDate,
    long expectedCents,
    PaymentStatus status,
    PaymentSource source,
    @Nullable Long paidCents,
    @Nullable LocalDate paidDate) {

  public static Payment expected(LocalDate dueDate, long expectedCents) {
    return new Payment(
        null, dueDate, expectedCents, PaymentStatus.EXPECTED, PaymentSource.SCHEDULED, null, null);
  }

  public static Payment backfilled(LocalDate dueDate, long cents) {
    return new Payment(
        null, dueDate, cents, PaymentStatus.PAID, PaymentSource.BACKFILL, cents, dueDate);
  }

  public boolean isScheduledExpected() {
    return source == PaymentSource.SCHEDULED && status == PaymentStatus.EXPECTED;
  }

  public Payment withExpectedCents(long cents) {
    return new Payment(id, dueDate, cents, status, source, paidCents, paidDate);
  }

  public Payment paidOnDueDate() {
    return new Payment(
        id, dueDate, expectedCents, PaymentStatus.PAID, source, expectedCents, dueDate);
  }
}
