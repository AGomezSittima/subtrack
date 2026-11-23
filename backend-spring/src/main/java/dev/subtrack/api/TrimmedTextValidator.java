package dev.subtrack.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class TrimmedTextValidator implements ConstraintValidator<TrimmedText, String> {

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null
        || (!value.isEmpty()
            && !isWhitespace(value.charAt(0))
            && !isWhitespace(value.charAt(value.length() - 1)));
  }

  private static boolean isWhitespace(char character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\r';
  }
}
