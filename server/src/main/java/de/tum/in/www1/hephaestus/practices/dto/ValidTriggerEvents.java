package de.tum.in.www1.hephaestus.practices.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validates that each element in a {@code List<String>} is a known trigger event name.
 *
 * @see TriggerEventsValidator
 */
@Documented
@Constraint(validatedBy = TriggerEventsValidator.class)
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface ValidTriggerEvents {
    String message() default "Contains invalid trigger events";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
