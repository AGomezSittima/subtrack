package dev.subtrack.billing;

import static dev.subtrack.billing.support.Cases.JSON;
import static dev.subtrack.billing.support.Cases.code;
import static dev.subtrack.billing.support.Cases.date;
import static dev.subtrack.billing.support.Cases.enumValue;
import static dev.subtrack.billing.support.Cases.failureMessage;
import static dev.subtrack.billing.support.Cases.interval;
import static dev.subtrack.billing.support.Cases.isoDate;
import static dev.subtrack.billing.support.Cases.normalized;
import static dev.subtrack.billing.support.Cases.optionalDate;
import static org.assertj.core.api.Assertions.assertThat;

import dev.subtrack.billing.support.Cases;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PaymentGenerationCasesTest {

  static Stream<Arguments> cases() {
    return Cases.load("payment-generation.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void paymentGeneration(JsonNode testCase) {
    JsonNode given = testCase.get("input");

    PaymentPlan plan =
        PaymentPlanner.planPayments(
            date(given.get("today")),
            new PaymentSettings(given.get("settings").get("payment_horizon_days").asInt()),
            subscription(given.get("subscription")),
            given
                .get("price_history")
                .valueStream()
                .map(change ->
                    new PriceChange(
                        change.get("price_cents").asLong(), date(change.get("effective_from"))))
                .toList(),
            given
                .get("existing_payments")
                .valueStream()
                .map(PaymentGenerationCasesTest::payment)
                .toList(),
            given.get("backfill").asBoolean());

    ObjectNode actual = JSON.createObjectNode();
    ArrayNode payments = actual.putArray("payments");
    plan.payments().forEach(payment -> payments.add(paymentJson(payment)));
    actual.put("subscription_status", code(plan.status()));
    assertThat(normalized(actual)).as(failureMessage(testCase)).isEqualTo(testCase.get("expected"));
  }

  private static PaymentSubscription subscription(JsonNode value) {
    return new PaymentSubscription(
        date(value.get("anchor_date")),
        interval(value.get("interval")),
        enumValue(SubscriptionStatus.class, value.get("status")),
        date(value.get("created_on")),
        value.get("auto_confirm").asBoolean(),
        optionalDate(value, "trial_end_date"),
        optionalDate(value, "paused_at"),
        optionalDate(value, "resumed_on"),
        optionalDate(value, "ends_on"),
        optionalDate(value, "archived_on"),
        optionalDate(value, "unarchived_on"));
  }

  private static Payment payment(JsonNode value) {
    JsonNode paidCents = value.get("paid_cents");
    return new Payment(
        Long.parseLong(value.get("ref").asString().substring(1)),
        date(value.get("due_date")),
        value.get("expected_cents").asLong(),
        enumValue(PaymentStatus.class, value.get("status")),
        enumValue(PaymentSource.class, value.get("source")),
        paidCents == null ? null : paidCents.asLong(),
        optionalDate(value, "paid_date"));
  }

  private static ObjectNode paymentJson(Payment payment) {
    ObjectNode node = JSON.createObjectNode();
    if (payment.id() != null) {
      node.put("ref", "p" + payment.id());
    }
    node.put("due_date", payment.dueDate().toString());
    node.put("expected_cents", payment.expectedCents());
    node.put("status", code(payment.status()));
    node.put("source", code(payment.source()));
    if (payment.paidCents() != null) {
      node.put("paid_cents", payment.paidCents());
    }
    if (payment.paidDate() != null) {
      node.put("paid_date", isoDate(payment.paidDate()));
    }
    return node;
  }
}
