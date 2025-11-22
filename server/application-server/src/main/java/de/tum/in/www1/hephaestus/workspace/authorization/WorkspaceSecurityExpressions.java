package de.tum.in.www1.hephaestus.workspace.authorization;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import org.springframework.stereotype.Component;

/**
 * Custom Spring Security expressions for workspace authorization.
 * Used in @PreAuthorize annotations on controller methods.
 */
@Component("workspaceSecure")
public class WorkspaceSecurityExpressions {

    private final WorkspaceAccessEvaluator accessEvaluator;

    public WorkspaceSecurityExpressions(WorkspaceAccessEvaluator accessEvaluator) {
        this.accessEvaluator = accessEvaluator;
    }

    /**
     * Check if user is authenticated and has MEMBER role or higher in the workspace.
     * Use: @PreAuthorize("isAuthenticated() and @workspaceSecure.isMember()")
     *
     * @return true if user is a member
     */
    public boolean isMember() {
        return accessEvaluator.isMember();
    }

    /**
     * Check if user is authenticated and has ADMIN role or higher in the workspace.
     * Use: @PreAuthorize("isAuthenticated() and @workspaceSecure.isAdmin()")
     *
     * @return true if user is an admin or owner
     */
    public boolean isAdmin() {
        return accessEvaluator.isAdmin();
    }

    /**
     * Check if user is authenticated and has OWNER role in the workspace.
     * Use: @PreAuthorize("isAuthenticated() and @workspaceSecure.isOwner()")
     *
     * @return true if user is the owner
     */
    public boolean isOwner() {
        return accessEvaluator.isOwner();
    }

    /**
     * Check if user has permission to manage the specified role.
     * OWNER can manage all roles, ADMIN can manage ADMIN and MEMBER.
     * Use: @PreAuthorize("isAuthenticated() and @workspaceSecure.canManageRole(#role)")
     *
     * @param role Role to manage
     * @return true if user can manage this role
     */
    public boolean canManageRole(WorkspaceRole role) {
        return accessEvaluator.canManageRole(role);
    }
}
