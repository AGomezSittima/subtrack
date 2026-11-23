package dev.subtrack.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class QueryParameters {

  private final List<ValidationIssue> issues = new ArrayList<>();

  @Nullable String text(String name, @Nullable String value, int minLength, int maxLength) {
    if (value != null) {
      int length = value.codePointCount(0, value.length());
      if (length < minLength) {
        issue(name, IssueCode.MIN_LENGTH, Map.of("min_length", (long) minLength));
      } else if (length > maxLength) {
        issue(name, IssueCode.MAX_LENGTH, Map.of("max_length", (long) maxLength));
      }
    }
    return value;
  }

  boolean bool(String name, @Nullable String value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return switch (value) {
      case "true" -> true;
      case "false" -> false;
      default -> {
        issue(name, IssueCode.TYPE, null);
        yield defaultValue;
      }
    };
  }

  void check() {
    if (!issues.isEmpty()) {
      throw new ProblemException(ProblemType.VALIDATION_ERROR, null, issues);
    }
  }

  private void issue(String name, IssueCode code, @Nullable Map<String, Object> params) {
    issues.add(new ValidationIssue("query", "/" + name, code, params));
  }
}
