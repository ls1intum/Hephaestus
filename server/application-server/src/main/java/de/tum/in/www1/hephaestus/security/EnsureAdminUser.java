package de.tum.in.www1.hephaestus.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Meta-annotation to require the current user to have the 'admin' authority.
 *
 * Usage:
 * <pre>
 *  @EnsureAdminUser
 *  public ResponseEntity<Void> someAdminOnlyEndpoint() { ... }
 * </pre>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('admin')")
public @interface EnsureAdminUser {
}
