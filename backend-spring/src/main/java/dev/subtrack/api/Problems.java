package dev.subtrack.api;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class Problems {

  private Problems() {}

  public static ResponseEntity<Object> response(ProblemType type) {
    return response(type, null, List.of(), HttpHeaders.EMPTY);
  }

  public static ResponseEntity<Object> response(
      ProblemType type,
      @Nullable String detail,
      List<ValidationIssue> errors,
      HttpHeaders headers) {
    return ResponseEntity.status(type.status())
        .headers(headers)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(Problem.of(type, detail, errors));
  }
}
