package dev.subtrack.billing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class ReminderPlanner {

  private static final Comparator<PlannedNotification> NOTIFICATION_ORDER =
      Comparator.comparing(PlannedNotification::triggerDate)
          .thenComparingLong(PlannedNotification::subscriptionId)
          .thenComparing(notification -> notification.type().code());

  private ReminderPlanner() {}

  public static String dedupeKey(
      NotificationType type, long subscriptionId, LocalDate triggerDate) {
    return type.code() + ":" + subscriptionId + ":" + triggerDate;
  }

  public static List<PlannedNotification> planReminders(
      @Nullable LocalDate lastRunDate,
      LocalDate today,
      ReminderSettings settings,
      List<ReminderSubscription> subscriptions,
      List<ReminderPayment> payments,
      Set<String> existingDedupeKeys) {
    LocalDate windowStart = lastRunDate != null ? lastRunDate : today;
    Map<Long, ReminderSubscription> byId =
        subscriptions.stream()
            .collect(Collectors.toMap(ReminderSubscription::id, Function.identity()));
    List<PlannedNotification> candidates = new ArrayList<>();
    for (ReminderSubscription subscription : subscriptions) {
      if (!subscription.archived()) {
        candidates.addAll(subscriptionTriggers(today, settings, subscription));
      }
    }
    for (ReminderPayment payment : payments) {
      ReminderSubscription subscription = byId.get(payment.subscriptionId());
      if (subscription != null && !subscription.archived()) {
        candidates.addAll(paymentTriggers(today, settings, subscription, payment));
      }
    }

    Set<String> seen = new HashSet<>(existingDedupeKeys);
    return candidates.stream()
        .sorted(NOTIFICATION_ORDER)
        .filter(notification -> !notification.triggerDate().isBefore(windowStart))
        .filter(notification -> !notification.triggerDate().isAfter(today))
        .filter(notification -> seen.add(notification.dedupeKey()))
        .toList();
  }

  private static List<PlannedNotification> paymentTriggers(
      LocalDate today,
      ReminderSettings settings,
      ReminderSubscription subscription,
      ReminderPayment payment) {
    if (payment.status() != PaymentStatus.EXPECTED) {
      return List.of();
    }
    NotificationParams params =
        new NotificationParams(subscription.name(), payment.dueDate(), payment.expectedCents());
    List<PlannedNotification> triggers = new ArrayList<>();
    if (subscription.status() != SubscriptionStatus.PAUSED
        && subscription.remindersEnabled()
        && !payment.dueDate().isBefore(today)) {
      NotificationType renewalType =
          subscription.interval().isYearlyOrLonger()
              ? NotificationType.YEARLY_RENEWAL
              : NotificationType.RENEWAL;
      triggers.add(
          notification(
              renewalType,
              subscription,
              payment.dueDate().minusDays(renewalLeadDays(settings, subscription)),
              payment.id(),
              params));
    }
    triggers.add(
        notification(
            NotificationType.OVERDUE,
            subscription,
            payment.dueDate().plusDays(1),
            payment.id(),
            params));
    return triggers;
  }

  private static List<PlannedNotification> subscriptionTriggers(
      LocalDate today, ReminderSettings settings, ReminderSubscription subscription) {
    List<PlannedNotification> triggers = new ArrayList<>();
    LocalDate trialEndDate = subscription.trialEndDate();
    if (subscription.status() == SubscriptionStatus.TRIAL
        && trialEndDate != null
        && !trialEndDate.isBefore(today)) {
      triggers.add(
          notification(
              NotificationType.TRIAL_ENDING,
              subscription,
              trialEndDate.minusDays(settings.trialReminderDays()),
              null,
              new NotificationParams(subscription.name(), trialEndDate, null)));
    }
    LocalDate endsOn = subscription.endsOn();
    if (subscription.status() == SubscriptionStatus.CANCELLED
        && endsOn != null
        && !endsOn.isBefore(today)) {
      NotificationParams params = new NotificationParams(subscription.name(), endsOn, null);
      for (LocalDate triggerDate :
          List.of(endsOn.minusDays(settings.endingReminderDays()), endsOn)) {
        triggers.add(
            notification(NotificationType.ENDING, subscription, triggerDate, null, params));
      }
    }
    return triggers;
  }

  private static int renewalLeadDays(ReminderSettings settings, ReminderSubscription subscription) {
    Integer reminderDays = subscription.reminderDays();
    if (reminderDays != null) {
      return reminderDays;
    }
    if (subscription.interval().isYearlyOrLonger()) {
      return settings.yearlyReminderDays();
    }
    return settings.defaultReminderDays();
  }

  private static PlannedNotification notification(
      NotificationType type,
      ReminderSubscription subscription,
      LocalDate triggerDate,
      @Nullable Long paymentId,
      NotificationParams params) {
    return new PlannedNotification(
        type,
        severity(type),
        triggerDate,
        dedupeKey(type, subscription.id(), triggerDate),
        subscription.id(),
        paymentId,
        params);
  }

  private static Severity severity(NotificationType type) {
    return switch (type) {
      case RENEWAL, YEARLY_RENEWAL -> Severity.INFO;
      case TRIAL_ENDING, ENDING -> Severity.WARNING;
      case OVERDUE -> Severity.CRITICAL;
    };
  }
}
