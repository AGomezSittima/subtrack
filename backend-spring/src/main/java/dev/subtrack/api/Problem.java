package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

// Not Spring's ProblemDetail: Spring MVC fills in its instance, which the contract forbids.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Problem(
    URI type, String title, int status, @Nullable String detail, List<ValidationIssue> errors) {

  public static Problem of(
      ProblemType type, @Nullable String detail, List<ValidationIssue> errors) {
    return new Problem(
        type.uri(), type.title(), type.status(), detail, ValidationIssue.onePerField(errors));
  }
}
