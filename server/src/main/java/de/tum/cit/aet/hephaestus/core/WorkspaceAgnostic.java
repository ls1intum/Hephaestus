package de.tum.cit.aet.hephaestus.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository or repository method as intentionally workspace-agnostic.
 *
 * <p>Use this to indicate that the annotated element legitimately does not filter by workspace.
 * Architecture tests skip validation for annotated elements, and at runtime
 * {@code WorkspaceAgnosticAspect} opens a bypass on the thread so
 * {@code WorkspaceStatementInspector} does not flag the emitted SQL.
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
 *
 * <p><b>Load-bearing at runtime:</b> dropping the annotation from a repository that issues
 * cross-workspace queries will cause {@code TenancyViolationException} under
 * {@code hephaestus.tenancy.enforcement=throw}. The annotation is not just documentation.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkspaceAgnostic {
    /** Reason why this is intentionally workspace-agnostic. */
    String value();

    /**
     * Whether the AOP aspect should open a runtime bypass on
     * {@code WorkspaceContextHolder} for the duration of the annotated call.
     *
     * <p>Set to {@code false} ONLY if the annotated code path provably never emits SQL.
     */
    boolean runtimeBypass() default true;
}
