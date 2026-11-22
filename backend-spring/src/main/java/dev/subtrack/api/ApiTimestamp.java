package dev.subtrack.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ApiTimestampValidator.class)
public @interface ApiTimestamp {

  String message() default "must be a UTC timestamp like 2026-11-10T15:18:00.000Z";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
