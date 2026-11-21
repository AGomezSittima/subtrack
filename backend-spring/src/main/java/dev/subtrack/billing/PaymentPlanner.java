package dev.subtrack.billing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PaymentPlanner {

  private static final Comparator<Payment> PAYMENT_ORDER =
      Comparator.comparing(Payment::dueDate)
          .thenComparing(payment -> payment.id() == null)
          .thenComparing(payment -> Objects.requireNonNullElse(payment.id(), 0L));

  private PaymentPlanner() {}

  public static long priceInForce(List<PriceChange> priceHistory, LocalDate on) {
    return priceHistory.stream()
        .filter(change -> !change.effectiveFrom().isAfter(on))
        .max(Comparator.comparing(PriceChange::effectiveFrom))
        .orElseThrow(() -> new BillingInputException(BillingErrorCode.PRICE_NOT_IN_FORCE))
        .priceCents();
  }

  public static PaymentPlan planPayments(
      LocalDate today,
      PaymentSettings settings,
      PaymentSubscription subscription,
      List<PriceChange> priceHistory,
      List<Payment> payments,
      boolean backfill) {
    if (priceHistory.isEmpty()) {
      throw new BillingInputException(BillingErrorCode.PRICE_HISTORY_EMPTY);
    }

    SubscriptionStatus status = nextStatus(today, subscription);
    List<Payment> kept =
        payments.stream().filter(payment -> !isCutOff(subscription, status, payment)).toList();
    List<Payment> planned =
        new ArrayList<>(kept.stream().map(p -> repriced(p, priceHistory)).toList());
    if (backfill) {
      planned.addAll(backfilled(subscription, priceHistory, planned));
    }
    planned.addAll(generated(today, settings, subscription, status, priceHistory, planned));
    if (subscription.autoConfirm() && subscription.archivedOn() == null) {
      planned.replaceAll(payment -> autoConfirmed(today, payment));
    }

    Map<Long, Payment> originals =
        payments.stream()
            .filter(payment -> payment.id() != null)
            .collect(Collectors.toMap(PaymentPlanner::idOf, Function.identity()));
    Set<Long> keptIds =
        kept.stream().map(Payment::id).filter(Objects::nonNull).collect(Collectors.toSet());
    Set<Long> updatedIds =
        planned.stream()
            .filter(payment -> payment.id() != null && !payment.equals(originals.get(payment.id())))
            .map(PaymentPlanner::idOf)
            .collect(Collectors.toSet());
    Set<Long> deletedIds = new HashSet<>(originals.keySet());
    deletedIds.removeAll(keptIds);
    return new PaymentPlan(
        status, planned.stream().sorted(PAYMENT_ORDER).toList(), updatedIds, deletedIds);
  }

  private static SubscriptionStatus nextStatus(LocalDate today, PaymentSubscription subscription) {
    LocalDate trialEndDate = subscription.trialEndDate();
    if (subscription.status() == SubscriptionStatus.TRIAL
        && trialEndDate != null
        && trialEndDate.isBefore(today)) {
      return SubscriptionStatus.ACTIVE;
    }
    return subscription.status();
  }

  private static boolean isCutOff(
      PaymentSubscription subscription, SubscriptionStatus status, Payment payment) {
    if (!payment.isScheduledExpected()) {
      return false;
    }
    LocalDate due = payment.dueDate();
    LocalDate pausedAt = subscription.pausedAt();
    LocalDate endsOn = subscription.endsOn();
    LocalDate archivedOn = subscription.archivedOn();
    return (status == SubscriptionStatus.PAUSED && pausedAt != null && due.isAfter(pausedAt))
        || (status == SubscriptionStatus.CANCELLED && endsOn != null && !due.isBefore(endsOn))
        || (archivedOn != null && due.isAfter(archivedOn));
  }

  private static Payment repriced(Payment payment, List<PriceChange> priceHistory) {
    if (!payment.isScheduledExpected()) {
      return payment;
    }
    return payment.withExpectedCents(priceInForce(priceHistory, payment.dueDate()));
  }

  private static List<Payment> backfilled(
      PaymentSubscription subscription, List<PriceChange> priceHistory, List<Payment> planned) {
    Set<LocalDate> taken = planned.stream().map(Payment::dueDate).collect(Collectors.toSet());
    LocalDate lastDay = subscription.createdOn().minusDays(1);
    return BillingDates.billingDatesBetween(
            subscription.anchorDate(), subscription.interval(), subscription.anchorDate(), lastDay)
        .stream()
        .filter(due -> !taken.contains(due))
        .map(due -> Payment.backfilled(due, priceInForce(priceHistory, due)))
        .toList();
  }

  private static List<Payment> generated(
      LocalDate today,
      PaymentSettings settings,
      PaymentSubscription subscription,
      SubscriptionStatus status,
      List<PriceChange> priceHistory,
      List<Payment> planned) {
    if (status != SubscriptionStatus.TRIAL && status != SubscriptionStatus.ACTIVE) {
      return List.of();
    }
    if (subscription.archivedOn() != null) {
      return List.of();
    }
    Set<LocalDate> taken =
        planned.stream()
            .filter(payment -> payment.source() != PaymentSource.MANUAL)
            .map(Payment::dueDate)
            .collect(Collectors.toSet());
    LocalDate start =
        Stream.of(subscription.createdOn(), subscription.resumedOn(), subscription.unarchivedOn())
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    LocalDate end = today.plusDays(settings.paymentHorizonDays());
    return BillingDates.billingDatesBetween(
            subscription.anchorDate(), subscription.interval(), start, end)
        .stream()
        .filter(due -> !taken.contains(due))
        .map(due -> Payment.expected(due, priceInForce(priceHistory, due)))
        .toList();
  }

  private static Payment autoConfirmed(LocalDate today, Payment payment) {
    if (!payment.isScheduledExpected() || payment.dueDate().isAfter(today)) {
      return payment;
    }
    return payment.paidOnDueDate();
  }

  private static long idOf(Payment payment) {
    return Objects.requireNonNull(payment.id());
  }
}
