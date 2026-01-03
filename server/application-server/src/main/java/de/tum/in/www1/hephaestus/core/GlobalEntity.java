package de.tum.in.www1.hephaestus.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity as globally scoped (not workspace-specific).
 *
 * <p>Global entities are shared across workspaces and do not have a direct or indirect
 * relationship to a specific workspace.
 *
 * <p><b>Examples:</b>
 *
 * <ul>
 *   <li>User - can belong to multiple workspaces
 *   <li>Workspace - is the tenant root itself
 *   <li>Organization - synced from GitHub, workspace assigned separately
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GlobalEntity {
    /** Reason why this entity is globally scoped. */
    String value();
}
