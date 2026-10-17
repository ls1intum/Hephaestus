package de.tum.cit.aet.hephaestus.core.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "^[1-9][0-9]*$")
@DecimalMax("9223372036854775807")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccountId {
    String message() default "Use a positive account ID.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
