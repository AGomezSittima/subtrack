package dev.subtrack.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentPlanChangesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
  private static final PaymentSettings SETTINGS = new PaymentSettings(90);
  private static final Interval MONTHLY = new Interval(1, IntervalUnit.MONTH);
  private static final PaymentSubscription SUBSCRIPTION =
      subscription(SubscriptionStatus.ACTIVE, null);
  private static final List<PriceChange> PRICES = List.of(new PriceChange(1199, TODAY));

  private static PaymentSubscription subscription(
      SubscriptionStatus status, @Nullable LocalDate pausedAt) {
    return new PaymentSubscription(
        TODAY, MONTHLY, status, TODAY, false, null, pausedAt, null, null, null, null);
  }

  private static List<Payment> saved(List<Payment> payments) {
    return IntStream.range(0, payments.size())
        .mapToObj(i -> {
          Payment p = payments.get(i);
          return new Payment(
              (long) i + 1,
              p.dueDate(),
              p.expectedCents(),
              p.status(),
              p.source(),
              p.paidCents(),
              p.paidDate());
        })
        .toList();
  }

  @Test
  void br20PlanningItsOwnResultAgainChangesNothing() {
    PaymentPlan first =
        PaymentPlanner.planPayments(TODAY, SETTINGS, SUBSCRIPTION, PRICES, List.of(), false);

    PaymentPlan second =
        PaymentPlanner.planPayments(
            TODAY, SETTINGS, SUBSCRIPTION, PRICES, saved(first.payments()), false);

    assertThat(first.inserts()).hasSize(3);
    assertThat(second.inserts()).isEmpty();
    assertThat(second.updatedIds()).isEmpty();
    assertThat(second.deletedIds()).isEmpty();
  }

  @Test
  void br9Br10RepricedAndCutOffPaymentsAreReportedById() {
    List<Payment> existing =
        saved(
            PaymentPlanner.planPayments(TODAY, SETTINGS, SUBSCRIPTION, PRICES, List.of(), false)
                .payments());
    PaymentSubscription paused = subscription(SubscriptionStatus.PAUSED, LocalDate.of(2027, 1, 1));
    List<PriceChange> raised =
        List.of(PRICES.getFirst(), new PriceChange(1299, LocalDate.of(2026, 12, 1)));

    PaymentPlan plan =
        PaymentPlanner.planPayments(TODAY, SETTINGS, paused, raised, existing, false);

    assertThat(plan.updatedIds()).containsExactly(2L);
    assertThat(plan.deletedIds()).containsExactly(3L);
    assertThat(plan.inserts()).isEmpty();
  }
}
