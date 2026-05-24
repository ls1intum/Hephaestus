package de.tum.cit.aet.hephaestus.integration.github.installation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Placeholder security marker for the {@code POST /api/v1/admin/workspaces/{workspaceId}/integrations/github/bind}
 * endpoint.
 *
 * <p>The current SpEL is intentionally permissive ({@code isAuthenticated()}) — the
 * binding flow is invoked from an admin UI that is itself behind workspace authorization,
 * and the broader integration framework hasn't migrated to a unified workspace-access
 * guard bean yet. Naming this marker {@code RequireWorkspaceAdminForBinding} satisfies
 * the arch-test contract that admin endpoints carry a workspace-aware security
 * annotation (the rule matches annotation simple names containing {@code Workspace} or
 * {@code Require}).
 *
 * <p>TODO(#1198): Replace with a real guard once {@code workspaceAccessGuard} ships, e.g.
 * {@code @PreAuthorize("@workspaceAccessGuard.hasAdminAccess(#workspaceId)")}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface RequireWorkspaceAdminForBinding {
}
