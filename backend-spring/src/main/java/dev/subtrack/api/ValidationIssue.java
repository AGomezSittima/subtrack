package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationIssue(
    String location, String pointer, IssueCode code, @Nullable Map<String, Object> params) {

  private record Field(String location, String pointer) implements Comparable<Field> {

    private static final Comparator<Field> ORDER =
        Comparator.comparing(Field::location).thenComparing(Field::pointer);

    @Override
    public int compareTo(Field other) {
      return ORDER.compare(this, other);
    }
  }

  public static List<ValidationIssue> onePerField(List<ValidationIssue> issues) {
    Map<Field, ValidationIssue> first = new TreeMap<>();
    for (ValidationIssue issue : issues) {
      first.merge(
          new Field(issue.location(), issue.pointer()),
          issue,
          (kept, other) -> other.code().compareTo(kept.code()) < 0 ? other : kept);
    }
    return List.copyOf(first.values());
  }

  public static ValidationIssue body(String pointer, IssueCode code) {
    return new ValidationIssue("body", pointer, code, null);
  }
}
