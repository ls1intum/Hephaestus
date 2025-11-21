package de.tum.in.www1.hephaestus.workspace.context;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Set;

/**
 * Immutable request-scoped context containing workspace metadata and user roles.
 * Used for isolation, authorization, and observability (MDC enrichment).
 *
 * @param id Workspace internal ID
 * @param slug Workspace URL-safe slug
 * @param displayName Workspace display name
 * @param accountType GitHub account type (ORG or USER)
 * @param installationId GitHub App installation ID (nullable)
 * @param roles Set of workspace roles for the current user
 */
public record WorkspaceContext(
    Long id,
    String slug,
    String displayName,
    AccountType accountType,
    Long installationId,
    Set<WorkspaceRole> roles
) {
    /**
     * Factory method to create WorkspaceContext from Workspace entity and user roles.
     *
     * @param workspace Workspace entity
     * @param roles User's roles in this workspace (null will be converted to empty set)
     * @return WorkspaceContext instance
     */
    public static WorkspaceContext fromWorkspace(Workspace workspace, Set<WorkspaceRole> roles) {
        return new WorkspaceContext(
            workspace.getId(),
            workspace.getWorkspaceSlug(),
            workspace.getDisplayName(),
            workspace.getAccountType(),
            workspace.getInstallationId(),
            roles != null ? roles : Set.of()
        );
    }

    /**
     * Check if the current user has a specific role in this workspace.
     *
     * @param role Role to check
     * @return true if user has the specified role
     */
    public boolean hasRole(WorkspaceRole role) {
        return roles.contains(role);
    }

    /**
     * Check if the current user has any membership (any role) in this workspace.
     *
     * @return true if user has at least one role
     */
    public boolean hasMembership() {
        return !roles.isEmpty();
    }
}
