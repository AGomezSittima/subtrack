package dev.subtrack.billing.support;

import dev.subtrack.billing.Interval;
import dev.subtrack.billing.IntervalUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.provider.Arguments;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Cases {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private Cases() {}

  public static Stream<Arguments> load(String fileName) {
    Path file = Path.of(System.getProperty("subtrack.cases.dir"), fileName);
    try {
      JsonNode document = JSON.readTree(Files.readString(file));
      return document.get("cases").valueStream().map(c -> Arguments.argumentSet(name(c), c));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String failureMessage(JsonNode testCase) {
    return "case " + name(testCase);
  }

  public static JsonNode normalized(ObjectNode actual) {
    return JSON.readTree(JSON.writeValueAsString(actual));
  }

  public static LocalDate date(JsonNode value) {
    return LocalDate.parse(value.asString());
  }

  public static @Nullable LocalDate optionalDate(JsonNode parent, String field) {
    JsonNode value = parent.get(field);
    return value == null || value.isNull() ? null : date(value);
  }

  public static @Nullable String isoDate(@Nullable LocalDate value) {
    return value == null ? null : value.toString();
  }

  public static Interval interval(JsonNode value) {
    return new Interval(
        value.get("count").asInt(), enumValue(IntervalUnit.class, value.get("unit")));
  }

  public static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode value) {
    return Enum.valueOf(type, value.asString().toUpperCase(Locale.ROOT));
  }

  public static String code(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  private static String name(JsonNode testCase) {
    String rules =
        testCase
            .get("rules")
            .valueStream()
            .map(JsonNode::asString)
            .collect(Collectors.joining(", "));
    return testCase.get("id").asString() + " [" + rules + "]";
  }
}
