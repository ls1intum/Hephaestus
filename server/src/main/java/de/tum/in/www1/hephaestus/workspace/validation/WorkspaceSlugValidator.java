package de.tum.in.www1.hephaestus.workspace.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates workspace slugs for controller path variables and DTO fields.
 */
public class WorkspaceSlugValidator implements ConstraintValidator<WorkspaceSlug, String> {

    private static final String SLUG_PATTERN = "^[a-z0-9][a-z0-9-]{2,50}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // validation annotations like @NotBlank should handle nullability
        }
        return value.matches(SLUG_PATTERN);
    }
}
