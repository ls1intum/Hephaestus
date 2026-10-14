package de.tum.cit.aet.hephaestus.practices.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotBlank
@Size(max = 64)
@Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
public @interface ValidPracticeSlug {
    String message() default "Use a valid practice slug.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
