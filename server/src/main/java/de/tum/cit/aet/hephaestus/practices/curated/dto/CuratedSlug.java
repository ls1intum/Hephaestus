package de.tum.cit.aet.hephaestus.practices.curated.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The identity a catalog entry keeps for life. It reaches the detection sandbox as a path segment, so
 * the shape is enforced everywhere one is accepted rather than at the point it is finally used.
 */
@NotBlank(message = "Slug is required")
@Size(min = 3, max = 64, message = "Slug must be between 3 and 64 characters")
@Pattern(
    regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
    message = "Slug must contain only lowercase alphanumeric characters and hyphens," +
        " must not start or end with a hyphen, and must not contain consecutive hyphens"
)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CuratedSlug {}
