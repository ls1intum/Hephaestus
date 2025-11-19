package de.tum.in.www1.hephaestus.workspace.exception;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;

/**
 * Exception thrown when a user lacks sufficient permissions to perform an action in a workspace.
 */
public class InsufficientWorkspacePermissionsException extends RuntimeException {

    private final String workspaceSlug;
    private final WorkspaceRole requiredRole;

    public InsufficientWorkspacePermissionsException(String workspaceSlug, WorkspaceRole requiredRole) {
        super(
            String.format(
                "Insufficient permissions to access workspace '%s'. Required role: %s",
                workspaceSlug,
                requiredRole
            )
        );
        this.workspaceSlug = workspaceSlug;
        this.requiredRole = requiredRole;
    }

    public InsufficientWorkspacePermissionsException(String workspaceSlug, String message) {
        super(String.format("Insufficient permissions to access workspace '%s': %s", workspaceSlug, message));
        this.workspaceSlug = workspaceSlug;
        this.requiredRole = null;
    }

    public String getWorkspaceSlug() {
        return workspaceSlug;
    }

    public WorkspaceRole getRequiredRole() {
        return requiredRole;
    }
}
