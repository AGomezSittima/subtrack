package dev.subtrack.billing;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PaymentSubscription(
    LocalDate anchorDate,
    Interval interval,
    SubscriptionStatus status,
    LocalDate createdOn,
    boolean autoConfirm,
    @Nullable LocalDate trialEndDate,
    @Nullable LocalDate pausedAt,
    @Nullable LocalDate resumedOn,
    @Nullable LocalDate endsOn,
    @Nullable LocalDate archivedOn,
    @Nullable LocalDate unarchivedOn) {

  public PaymentSubscription {
    if (status == SubscriptionStatus.TRIAL && trialEndDate == null) {
      throw new BillingInputException(BillingErrorCode.TRIAL_END_DATE_REQUIRED);
    }
    if (status == SubscriptionStatus.PAUSED && pausedAt == null) {
      throw new BillingInputException(BillingErrorCode.PAUSED_AT_REQUIRED);
    }
    if (status == SubscriptionStatus.CANCELLED && endsOn == null) {
      throw new BillingInputException(BillingErrorCode.ENDS_ON_REQUIRED);
    }
  }
}
