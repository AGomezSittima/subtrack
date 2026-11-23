package dev.subtrack.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public class NonBlankTextValidator implements ConstraintValidator<NonBlankText, String> {

  private static final Pattern NON_BLANK =
      Pattern.compile(
          "[^\\u0009-\\u000d\\u0020\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]");

  private int maxCodePoints;

  @Override
  public void initialize(NonBlankText constraint) {
    maxCodePoints = constraint.maxCodePoints();
  }

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null
        || value.codePointCount(0, value.length()) > maxCodePoints
        || NON_BLANK.matcher(value).find();
  }
}
