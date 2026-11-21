package dev.subtrack.billing;

import static dev.subtrack.billing.support.Cases.JSON;
import static dev.subtrack.billing.support.Cases.date;
import static dev.subtrack.billing.support.Cases.failureMessage;
import static dev.subtrack.billing.support.Cases.interval;
import static dev.subtrack.billing.support.Cases.normalized;
import static org.assertj.core.api.Assertions.assertThat;

import dev.subtrack.billing.support.Cases;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class BillingDatesCasesTest {

  static Stream<Arguments> cases() {
    return Cases.load("billing-dates.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void billingDates(JsonNode testCase) {
    JsonNode given = testCase.get("input");

    var dates =
        BillingDates.billingDates(
            date(given.get("anchor")), interval(given.get("interval")), given.get("count").asInt());

    ObjectNode actual = JSON.createObjectNode();
    dates.stream().map(LocalDate::toString).forEach(actual.putArray("dates")::add);
    assertThat(normalized(actual)).as(failureMessage(testCase)).isEqualTo(testCase.get("expected"));
  }
}
