package de.tum.in.www1.hephaestus.workspace.authorization;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import org.springframework.stereotype.Component;

/**
 * Custom Spring Security expressions for workspace authorization.
 * Used in @PreAuthorize annotations on controller methods.
 */
@Component("workspaceSecure")
public class WorkspaceSecurityExpressions {

    private final WorkspaceAccessService accessService;

    public WorkspaceSecurityExpressions(WorkspaceAccessService accessService) {
        this.accessService = accessService;
    }

    /**
     * Check if user is authenticated and has ADMIN role or higher in the workspace.
     * Use: @RequireAtLeastWorkspaceAdmin
     *
     * @return true if user is an admin or owner
     */
    public boolean isAdmin() {
        return accessService.isAdmin();
    }

    /**
     * Check if user is authenticated and has OWNER role in the workspace.
     * Use: @RequireWorkspaceOwner
     *
     * @return true if user is the owner
     */
    public boolean isOwner() {
        return accessService.isOwner();
    }

    /**
     * Check if user has permission to manage the specified role.
     * OWNER can manage all roles, ADMIN can manage ADMIN and MEMBER.
     * Can be used in SpEL: @PreAuthorize("@workspaceSecure.canManageRole(#role)")
     *
     * @param role Role to manage
     * @return true if user can manage this role
     */
    public boolean canManageRole(WorkspaceRole role) {
        return accessService.canManageRole(role);
    }
}
