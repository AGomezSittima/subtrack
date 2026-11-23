package dev.subtrack.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class MinCodePointsValidator implements ConstraintValidator<MinCodePoints, String> {

  private int limit;

  @Override
  public void initialize(MinCodePoints constraint) {
    limit = constraint.value();
  }

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null || value.codePointCount(0, value.length()) >= limit;
  }
}
