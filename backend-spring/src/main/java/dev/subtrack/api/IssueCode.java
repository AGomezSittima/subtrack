package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum IssueCode {
  REQUIRED,
  UNKNOWN_PROPERTY,
  TYPE,
  FORMAT,
  PATTERN,
  ENUM,
  MINIMUM,
  MAXIMUM,
  MIN_LENGTH,
  MAX_LENGTH,
  INVALID;

  @JsonValue
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
