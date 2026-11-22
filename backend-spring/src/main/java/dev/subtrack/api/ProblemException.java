package dev.subtrack.api;

import java.util.List;
import org.jspecify.annotations.Nullable;

public class ProblemException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ProblemType type;
  private final transient List<ValidationIssue> errors;

  public ProblemException(ProblemType type, @Nullable String detail, List<ValidationIssue> errors) {
    super(detail);
    this.type = type;
    this.errors = List.copyOf(errors);
  }

  public ProblemException(ProblemType type) {
    this(type, null, List.of());
  }

  public ProblemType type() {
    return type;
  }

  public List<ValidationIssue> errors() {
    return errors;
  }
}
