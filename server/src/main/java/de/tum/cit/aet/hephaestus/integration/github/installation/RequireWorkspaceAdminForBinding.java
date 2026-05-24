package de.tum.cit.aet.hephaestus.integration.github.installation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Permissive security marker for the {@code POST /api/v1/admin/workspaces/{workspaceId}/integrations/github/bind}
 * endpoint.
 *
 * <p>Current SpEL: {@code isAuthenticated()}. The
 * {@link de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin}
 * annotation we use elsewhere reads workspace context from
 * {@code WorkspaceContextHolder} (populated by {@code WorkspaceContextFilter}) — but that
 * filter only matches the {@code /workspaces/{slug}/...} pattern. The {@code /api/v1/admin/...}
 * admin family has no equivalent populator yet, so any {@code @workspaceSecure.isAdmin()}
 * call from this controller will see a null context and return false (= every bind
 * permanently 403s). We name this annotation explicitly so {@code grep} finds the gap.
 *
 * <p>TODO(#1198 follow-up): replace with
 * {@code @PreAuthorize("@workspaceSecure.isAdminOfWorkspace(#workspaceId)")} once a
 * parameterised admin-check method is added to {@link
 * de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceSecurityExpressions} that
 * resolves the workspace by id (without depending on {@code WorkspaceContextHolder}).
 * That change is out of scope for the framework dissolution slice — it lives in the
 * workspace-authorization module.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface RequireWorkspaceAdminForBinding {
}
