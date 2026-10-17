package de.tum.cit.aet.hephaestus.practices.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@NotNull
@Pattern(regexp = "[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*")
public @interface ValidPracticeKind {
    String message() default "Use a valid practice item kind.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
