package de.tum.in.www1.hephaestus.workspace.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Meta-annotation to require the current user to be at least an ADMIN of the current workspace.
 * This includes ADMIN and OWNER roles.
 *
 * Usage:
 * <pre>
 *  @RequireAtLeastWorkspaceAdmin
 *  public ResponseEntity<Void> someAdminEndpoint() { ... }
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@workspaceSecure.isAdmin()")
public @interface RequireAtLeastWorkspaceAdmin {
}
