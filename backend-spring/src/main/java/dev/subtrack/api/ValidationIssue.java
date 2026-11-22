package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationIssue(
    String location, String pointer, IssueCode code, @Nullable Map<String, Object> params) {

  public static final Comparator<ValidationIssue> ORDER =
      Comparator.comparing(ValidationIssue::location)
          .thenComparing(ValidationIssue::pointer)
          .thenComparing(issue -> issue.code().code());

  public static ValidationIssue body(String pointer, IssueCode code) {
    return new ValidationIssue("body", pointer, code, null);
  }
}
