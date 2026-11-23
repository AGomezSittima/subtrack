package dev.subtrack.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class OneOfValidator implements ConstraintValidator<OneOf, String> {

  private CodeSet allowed = CodeSet.CATEGORY;

  @Override
  public void initialize(OneOf constraint) {
    allowed = constraint.value();
  }

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null || allowed.contains(value);
  }
}
