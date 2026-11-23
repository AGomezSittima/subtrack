package dev.subtrack.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT,
  ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxCodePointsValidator.class)
public @interface MaxCodePoints {

  int value();

  String message() default "length in code points must be <= {value}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
