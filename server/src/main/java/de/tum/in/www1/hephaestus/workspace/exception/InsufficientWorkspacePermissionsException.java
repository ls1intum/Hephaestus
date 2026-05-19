package de.tum.in.www1.hephaestus.workspace.exception;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.io.Serial;
import org.springframework.lang.Nullable;

/**
 * Exception thrown when a user lacks sufficient permissions to perform an action in a workspace.
 * <p>
 * This exception provides context about which workspace and what role was required,
 * enabling proper RFC-7807 error responses.
 */
public class InsufficientWorkspacePermissionsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String workspaceSlug;

    @Nullable
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
