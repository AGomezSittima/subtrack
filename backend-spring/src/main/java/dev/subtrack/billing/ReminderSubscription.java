package dev.subtrack.billing;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record ReminderSubscription(
    long id,
    String name,
    Interval interval,
    SubscriptionStatus status,
    boolean archived,
    @Nullable Integer reminderDays,
    @Nullable LocalDate trialEndDate,
    @Nullable LocalDate endsOn) {

  public ReminderSubscription {
    if (status == SubscriptionStatus.TRIAL && trialEndDate == null) {
      throw new BillingInputException(BillingErrorCode.TRIAL_END_DATE_REQUIRED);
    }
    if (status == SubscriptionStatus.CANCELLED && endsOn == null) {
      throw new BillingInputException(BillingErrorCode.ENDS_ON_REQUIRED);
    }
  }
}
