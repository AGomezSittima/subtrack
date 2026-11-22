package dev.subtrack.api;

import dev.subtrack.persistence.IsoText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class ApiTimestampValidator implements ConstraintValidator<ApiTimestamp, String> {

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    try {
      IsoText.parseTimestamp(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
