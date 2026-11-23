package dev.subtrack.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Over-long values are left to {@code @MaxCodePoints}: Pydantic checks the length first. */
@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT,
  ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NonBlankTextValidator.class)
public @interface NonBlankText {

  int maxCodePoints();

  String message() default "must contain a character that is not whitespace";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
