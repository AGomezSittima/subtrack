package dev.subtrack.billing;

import static dev.subtrack.billing.support.Cases.JSON;
import static dev.subtrack.billing.support.Cases.failureMessage;
import static dev.subtrack.billing.support.Cases.interval;
import static dev.subtrack.billing.support.Cases.normalized;
import static org.assertj.core.api.Assertions.assertThat;

import dev.subtrack.billing.support.Cases;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class CostNormalizationCasesTest {

  static Stream<Arguments> cases() {
    return Cases.load("cost-normalization.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void costNormalization(JsonNode testCase) {
    JsonNode given = testCase.get("input");
    long priceCents = given.get("price_cents").asLong();
    Interval interval = interval(given.get("interval"));

    ObjectNode actual = JSON.createObjectNode();
    actual.put("monthly_cents", Costs.roundHalfUp(Costs.monthlyEquivalent(priceCents, interval)));
    actual.put("yearly_cents", Costs.roundHalfUp(Costs.yearlyEquivalent(priceCents, interval)));

    assertThat(normalized(actual)).as(failureMessage(testCase)).isEqualTo(testCase.get("expected"));
  }
}
