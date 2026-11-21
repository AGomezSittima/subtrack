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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class AllocationCasesTest {

  static Stream<Arguments> cases() {
    return Cases.load("allocation.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void allocation(JsonNode testCase) {
    var items =
        testCase
            .get("input")
            .get("items")
            .valueStream()
            .map(item ->
                new AllocationItem(
                    partKey(item.get("part")),
                    item.get("price_cents").asLong(),
                    interval(item.get("interval"))))
            .toList();

    Allocation allocation = Allocations.allocate(items);

    ObjectNode actual = JSON.createObjectNode();
    actual.put("total_cents", allocation.totalCents());
    ArrayNode parts = actual.putArray("parts");
    for (AllocatedPart part : allocation.parts()) {
      ObjectNode node = parts.addObject();
      switch (part.part()) {
        case PartKey.SubscriptionKey(long id) -> node.put("part", id);
        case PartKey.LabelKey(String label) -> node.put("part", label);
        case PartKey.NoLabel _ -> node.putNull("part");
      }
      node.put("cents", part.cents());
    }
    assertThat(normalized(actual)).as(failureMessage(testCase)).isEqualTo(testCase.get("expected"));
  }

  private static PartKey partKey(JsonNode value) {
    if (value.isNull()) {
      return new PartKey.NoLabel();
    }
    if (value.isIntegralNumber()) {
      return new PartKey.SubscriptionKey(value.asLong());
    }
    return new PartKey.LabelKey(value.asString());
  }
}
