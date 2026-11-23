package dev.subtrack.billing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingInputExceptionTest {

  private static final Interval MONTHLY = new Interval(1, IntervalUnit.MONTH);
  private static final LocalDate DAY = LocalDate.of(2026, 11, 22);

  private static PaymentSubscription paymentSubscription(SubscriptionStatus status) {
    return new PaymentSubscription(
        DAY, MONTHLY, status, DAY, false, null, null, null, null, null, null);
  }

  @Test
  void intervalCountMustBePositive() {
    assertThatThrownBy(() -> new Interval(0, IntervalUnit.MONTH))
        .isInstanceOf(BillingInputException.class)
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.INTERVAL_COUNT_NOT_POSITIVE);
  }

  @Test
  void billingDateIndexMustNotBeNegative() {
    assertThatThrownBy(() -> BillingDates.billingDate(DAY, MONTHLY, -1))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.NEGATIVE_INDEX);
  }

  @Test
  void priceMustNotBeNegative() {
    assertThatThrownBy(() -> Costs.monthlyEquivalent(-1, MONTHLY))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.NEGATIVE_CENTS);
  }

  @Test
  void br28ATrialNeedsATrialEndDateForPaymentPlanning() {
    assertThatThrownBy(() -> paymentSubscription(SubscriptionStatus.TRIAL))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.TRIAL_END_DATE_REQUIRED);
  }

  @Test
  void br28ATrialNeedsATrialEndDateForReminderPlanning() {
    assertThatThrownBy(() ->
            new ReminderSubscription(
                1, "Spotify", MONTHLY, SubscriptionStatus.TRIAL, false, true, null, null, null))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.TRIAL_END_DATE_REQUIRED);
  }

  @ParameterizedTest
  @CsvSource({"PAUSED, PAUSED_AT_REQUIRED", "CANCELLED, ENDS_ON_REQUIRED"})
  void statusNeedsItsDate(SubscriptionStatus status, BillingErrorCode code) {
    assertThatThrownBy(() -> paymentSubscription(status)).hasFieldOrPropertyWithValue("code", code);
  }

  @Test
  void paymentPlanningNeedsAPriceHistory() {
    assertThatThrownBy(() ->
            PaymentPlanner.planPayments(
                DAY,
                new PaymentSettings(30),
                paymentSubscription(SubscriptionStatus.ACTIVE),
                List.of(),
                List.of(),
                false))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.PRICE_HISTORY_EMPTY);
  }

  @Test
  void noPriceIsInForceBeforeTheFirstChange() {
    assertThatThrownBy(() ->
            PaymentPlanner.priceInForce(
                List.of(new PriceChange(1199, DAY)), LocalDate.of(2026, 11, 21)))
        .hasFieldOrPropertyWithValue("code", BillingErrorCode.PRICE_NOT_IN_FORCE);
  }
}
