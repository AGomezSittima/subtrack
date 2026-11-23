package dev.subtrack.api;

import dev.subtrack.clock.TimeZones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public class TimeZoneNameValidator implements ConstraintValidator<TimeZoneName, String> {

  static final String SHAPE = "^[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+)*$";
  static final int MAX_LENGTH = 64;
  static final String DEVICE = "device";

  private static final Pattern SHAPE_PATTERN = Pattern.compile(SHAPE);

  private boolean allowDevice;

  @Override
  public void initialize(TimeZoneName constraint) {
    allowDevice = constraint.allowDevice();
  }

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    if (value == null
        || value.codePointCount(0, value.length()) > MAX_LENGTH
        || !SHAPE_PATTERN.matcher(value).matches()) {
      return true;
    }
    return (allowDevice && DEVICE.equals(value)) || TimeZones.isKnown(value);
  }
}
