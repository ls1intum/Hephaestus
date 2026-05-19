package de.tum.in.www1.hephaestus.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository or repository method as intentionally workspace-agnostic.
 *
 * <p>Use this to indicate that the annotated element legitimately does not filter by workspace.
 * Architecture tests will skip validation for these.
 *
 * <p><b>When to use:</b>
 *
 * <ul>
 *   <li>Lookup tables (UserRepository, LabelRepository)
 *   <li>Sync operations that find by external GitHub ID
 *   <li>Cross-workspace queries for admin operations
 * </ul>
 *
 * <p><b>Provide a reason:</b> Always document why this is workspace-agnostic.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkspaceAgnostic {
    /** Reason why this is intentionally workspace-agnostic. */
    String value();
}
