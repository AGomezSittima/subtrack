package dev.subtrack.api;

import dev.subtrack.clock.TimeZones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class TimeZoneNameValidator implements ConstraintValidator<TimeZoneName, String> {

  static final String SHAPE = "^[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+)*$";
  static final int MAX_LENGTH = 64;

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null || TimeZones.isKnown(value);
  }
}
