package de.tum.in.www1.hephaestus.workspace.authorization;

import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for evaluating workspace-level access permissions based on user roles.
 * Uses role hierarchy: OWNER > ADMIN > MEMBER
 */
@Service
public class WorkspaceAccessService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAccessService.class);

    // Role hierarchy levels (higher = more permissions)
    private static final int ROLE_LEVEL_MEMBER = 1;
    private static final int ROLE_LEVEL_ADMIN = 2;
    private static final int ROLE_LEVEL_OWNER = 3;

    /**
     * Check if the current user has at least the specified role in the current workspace.
     * Uses role hierarchy: if user has OWNER, they also satisfy ADMIN and MEMBER checks.
     *
     * @param requiredRole Minimum required role
     * @return true if user has the required role or higher
     */
    public boolean hasRole(WorkspaceRole requiredRole) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            log.warn("Denied role check: reason=noWorkspaceContext, requiredRole={}", requiredRole);
            return false;
        }

        Set<WorkspaceRole> userRoles = context.roles();
        if (userRoles == null || userRoles.isEmpty()) {
            log.debug("Denied role check: reason=noRoles, workspaceSlug={}", context.slug());
            return false;
        }

        // Check role hierarchy
        for (WorkspaceRole userRole : userRoles) {
            if (satisfiesRoleRequirement(userRole, requiredRole)) {
                return true;
            }
        }

        log.debug(
            "Denied role check: reason=insufficientRole, userRoles={}, requiredRole={}, workspaceSlug={}",
            userRoles,
            requiredRole,
            context.slug()
        );
        return false;
    }

    /**
     * Check if user is an OWNER of the current workspace.
     *
     * @return true if user has OWNER role
     */
    public boolean isOwner() {
        return hasRole(WorkspaceRole.OWNER);
    }

    /**
     * Check if user is an ADMIN or higher (OWNER) of the current workspace.
     *
     * @return true if user has ADMIN or OWNER role
     */
    public boolean isAdmin() {
        return hasRole(WorkspaceRole.ADMIN);
    }

    /**
     * Check if user has any membership (any role) in the current workspace.
     *
     * @return true if user has at least MEMBER role
     */
    public boolean isMember() {
        return hasRole(WorkspaceRole.MEMBER);
    }

    /**
     * Check if user has permission to perform an action requiring the specified role.
     * Alias for hasRole() for clearer intent in permission checks.
     *
     * @param requiredRole Minimum required role for the action
     * @return true if user has sufficient permissions
     */
    public boolean hasPermission(WorkspaceRole requiredRole) {
        return hasRole(requiredRole);
    }

    /**
     * Check if user can assign or revoke the specified role.
     * OWNER can manage all roles.
     * ADMIN can manage ADMIN and MEMBER roles (but not OWNER).
     *
     * @param targetRole Role to assign/revoke
     * @return true if user has permission to manage this role
     */
    public boolean canManageRole(WorkspaceRole targetRole) {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            return false;
        }

        Set<WorkspaceRole> userRoles = context.roles();
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        // OWNER can manage all roles
        if (userRoles.contains(WorkspaceRole.OWNER)) {
            return true;
        }

        // ADMIN can manage ADMIN and MEMBER, but not OWNER
        if (userRoles.contains(WorkspaceRole.ADMIN)) {
            return targetRole != WorkspaceRole.OWNER;
        }

        return false;
    }

    /**
     * Check if a user role satisfies a required role based on hierarchy.
     * Hierarchy: OWNER (3) > ADMIN (2) > MEMBER (1)
     *
     * @param userRole User's actual role
     * @param requiredRole Required role
     * @return true if userRole is equal to or higher than requiredRole
     */
    private boolean satisfiesRoleRequirement(WorkspaceRole userRole, WorkspaceRole requiredRole) {
        int userLevel = getRoleLevel(userRole);
        int requiredLevel = getRoleLevel(requiredRole);
        return userLevel >= requiredLevel;
    }

    /**
     * Get numeric level for role hierarchy comparison.
     *
     * @param role Workspace role
     * @return Numeric level (OWNER=3, ADMIN=2, MEMBER=1)
     */
    private int getRoleLevel(WorkspaceRole role) {
        return switch (role) {
            case OWNER -> ROLE_LEVEL_OWNER;
            case ADMIN -> ROLE_LEVEL_ADMIN;
            case MEMBER -> ROLE_LEVEL_MEMBER;
        };
    }
}
