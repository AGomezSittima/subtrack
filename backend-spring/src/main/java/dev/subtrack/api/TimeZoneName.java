package dev.subtrack.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A known IANA zone name; malformed values are left to {@code @Pattern} and the length check. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeZoneNameValidator.class)
public @interface TimeZoneName {

  String message() default "must be a known IANA time zone";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
