package de.tum.in.www1.hephaestus.workspace.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Meta-annotation to require the current user to be an OWNER of the current workspace.
 *
 * Usage:
 * <pre>
 *  @RequireWorkspaceOwner
 *  public ResponseEntity<Void> someOwnerEndpoint() { ... }
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@workspaceSecure.isOwner()")
public @interface RequireWorkspaceOwner {
}
