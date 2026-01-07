package de.tum.in.www1.hephaestus.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents an intentional deviation from architecture rules.
 *
 * <p>This annotation marks code that intentionally violates an architecture rule for a documented
 * reason. Architecture tests should skip validation for annotated elements.
 *
 * <p><b>IMPORTANT:</b> Using this annotation requires PR approval from a staff engineer. Don't
 * abuse it.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ArchitectureBypass {
    /** The architecture rule being bypassed. */
    String rule();

    /** Why this bypass is necessary. */
    String reason();

    /** Who approved this bypass (GitHub username). */
    String approvedBy() default "";
}
