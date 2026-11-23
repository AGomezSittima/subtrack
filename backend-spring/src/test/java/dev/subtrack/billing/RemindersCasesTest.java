package dev.subtrack.billing;

import static dev.subtrack.billing.support.Cases.JSON;
import static dev.subtrack.billing.support.Cases.code;
import static dev.subtrack.billing.support.Cases.date;
import static dev.subtrack.billing.support.Cases.enumValue;
import static dev.subtrack.billing.support.Cases.failureMessage;
import static dev.subtrack.billing.support.Cases.interval;
import static dev.subtrack.billing.support.Cases.normalized;
import static dev.subtrack.billing.support.Cases.optionalDate;
import static org.assertj.core.api.Assertions.assertThat;

import dev.subtrack.billing.support.Cases;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class RemindersCasesTest {

  static Stream<Arguments> cases() {
    return Cases.load("reminders.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void reminders(JsonNode testCase) {
    JsonNode given = testCase.get("input");
    JsonNode settings = given.get("settings");

    var notifications =
        ReminderPlanner.planReminders(
            optionalDate(given, "last_run_date"),
            date(given.get("today")),
            new ReminderSettings(
                settings.get("default_reminder_days").asInt(),
                settings.get("trial_reminder_days").asInt(),
                settings.get("yearly_reminder_days").asInt(),
                settings.get("ending_reminder_days").asInt()),
            given.get("subscriptions").valueStream().map(RemindersCasesTest::subscription).toList(),
            given.get("payments").valueStream().map(RemindersCasesTest::payment).toList(),
            given
                .get("existing_dedupe_keys")
                .valueStream()
                .map(JsonNode::asString)
                .collect(Collectors.toSet()));

    ObjectNode actual = JSON.createObjectNode();
    ArrayNode array = actual.putArray("notifications");
    notifications.forEach(notification -> array.add(notificationJson(notification)));
    assertThat(normalized(actual)).as(failureMessage(testCase)).isEqualTo(testCase.get("expected"));
  }

  private static ReminderSubscription subscription(JsonNode value) {
    JsonNode reminderDays = value.get("reminder_days");
    return new ReminderSubscription(
        value.get("id").asLong(),
        value.get("name").asString(),
        interval(value.get("interval")),
        enumValue(SubscriptionStatus.class, value.get("status")),
        value.get("archived").asBoolean(),
        value.get("reminders_enabled").asBoolean(),
        reminderDays.isNull() ? null : reminderDays.asInt(),
        optionalDate(value, "trial_end_date"),
        optionalDate(value, "ends_on"));
  }

  private static ReminderPayment payment(JsonNode value) {
    return new ReminderPayment(
        value.get("id").asLong(),
        value.get("subscription_id").asLong(),
        date(value.get("due_date")),
        value.get("expected_cents").asLong(),
        enumValue(PaymentStatus.class, value.get("status")));
  }

  private static ObjectNode notificationJson(PlannedNotification notification) {
    ObjectNode node = JSON.createObjectNode();
    node.put("type", code(notification.type()));
    node.put("severity", code(notification.severity()));
    node.put("trigger_date", notification.triggerDate().toString());
    node.put("dedupe_key", notification.dedupeKey());
    node.put("subscription_id", notification.subscriptionId());
    node.put("payment_id", notification.paymentId());
    ObjectNode params = node.putObject("params");
    params.put("name", notification.params().name());
    params.put("date", notification.params().date().toString());
    if (notification.params().amountCents() != null) {
      params.put("amount_cents", notification.params().amountCents());
    }
    return node;
  }
}
