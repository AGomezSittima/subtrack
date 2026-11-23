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
@Constraint(validatedBy = TrimmedTextValidator.class)
public @interface TrimmedText {

  String message() default "must not be empty or start or end with a space, tab or line break";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
