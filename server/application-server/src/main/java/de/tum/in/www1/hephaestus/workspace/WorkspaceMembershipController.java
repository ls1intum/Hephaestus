package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.authorization.WorkspaceAccessService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import de.tum.in.www1.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import de.tum.in.www1.hephaestus.workspace.exception.InsufficientWorkspacePermissionsException;
import de.tum.in.www1.hephaestus.workspace.exception.LastOwnerRemovalException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing workspace memberships and roles.
 * Handles CRUD operations for workspace members and role assignments.
 */
@WorkspaceScopedController
@RequestMapping("/members")
@RequiredArgsConstructor
public class WorkspaceMembershipController {

    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService accessService;

    /**
     * Get the current user's membership in this workspace.
     *
     * @param context the workspace context
     * @return the current user's membership details
     */
    @GetMapping("/me")
    public ResponseEntity<WorkspaceMembershipDTO> getCurrentUserMembership(WorkspaceContext context) {
        User currentUser = requireCurrentUser();
        WorkspaceMembership membership = requireMembership(context.id(), currentUser.getId());
        return ResponseEntity.ok(WorkspaceMembershipDTO.from(membership));
    }

    /**
     * List all members of the workspace with pagination.
     * Accessible to all workspace members (MEMBER role and above).
     *
     * @param context the workspace context
     * @param page Page number (0-indexed)
     * @param size Page size (default 50, max 100)
     * @return List of workspace memberships
     */
    @GetMapping
    public ResponseEntity<List<WorkspaceMembershipDTO>> listMembers(
        WorkspaceContext context,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        int pageSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").ascending());

        List<WorkspaceMembershipDTO> memberships = workspaceMembershipRepository
            .findAllByWorkspace_Id(context.id(), pageable)
            .map(WorkspaceMembershipDTO::from)
            .getContent();

        return ResponseEntity.ok(memberships);
    }

    /**
     * Get a specific member's details.
     * Accessible to all workspace members.
     *
     * @param context the workspace context
     * @param userId User ID
     * @return Workspace membership details
     */
    @GetMapping("/{userId}")
    public ResponseEntity<WorkspaceMembershipDTO> getMember(WorkspaceContext context, @PathVariable Long userId) {
        WorkspaceMembership membership = requireMembership(context.id(), userId);
        return ResponseEntity.ok(WorkspaceMembershipDTO.from(membership));
    }

    /**
     * Assign or update a role for a workspace member.
     * OWNER can assign any role. ADMIN can assign ADMIN or MEMBER roles.
     *
     * @param context the workspace context
     * @param request Role assignment request
     * @return Updated membership
     */
    @PostMapping("/assign")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceMembershipDTO> assignRole(
        WorkspaceContext context,
        @Valid @RequestBody AssignRoleRequestDTO request
    ) {
        requireCanManageRole(context, request.role());
        WorkspaceMembership membership = workspaceMembershipService.assignRole(
            context.id(),
            request.userId(),
            request.role()
        );
        return ResponseEntity.ok(WorkspaceMembershipDTO.from(membership));
    }

    /**
     * Revoke a user's membership (remove them from workspace).
     * OWNER can remove anyone except themselves if they are the last OWNER.
     * ADMIN can remove MEMBER and ADMIN roles.
     *
     * @param context the workspace context
     * @param userId User ID to remove
     * @return 204 No Content on success
     */
    @DeleteMapping("/{userId}")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> removeMember(WorkspaceContext context, @PathVariable Long userId) {
        WorkspaceMembership membership = requireMembership(context.id(), userId);
        requireCanManageRole(context, membership.getRole());
        requireNotLastOwner(context, membership);

        workspaceMembershipService.removeMembership(context.id(), userId);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper methods - throw proper exceptions for consistent RFC-7807 responses
    // ══════════════════════════════════════════════════════════════════════════

    private User requireCurrentUser() {
        return userRepository
            .getCurrentUser()
            .orElseThrow(() -> new AccessForbiddenException("User not authenticated"));
    }

    private WorkspaceMembership requireMembership(Long workspaceId, Long userId) {
        return workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, userId)
            .orElseThrow(() -> new EntityNotFoundException("WorkspaceMembership", userId));
    }

    private void requireCanManageRole(WorkspaceContext context, WorkspaceRole role) {
        if (!accessService.canManageRole(role)) {
            throw new InsufficientWorkspacePermissionsException(
                context.slug(),
                "You cannot manage the " + role + " role"
            );
        }
    }

    private void requireNotLastOwner(WorkspaceContext context, WorkspaceMembership membership) {
        if (membership.getRole() == WorkspaceRole.OWNER) {
            long ownerCount = workspaceMembershipRepository.countByWorkspace_IdAndRole(
                context.id(),
                WorkspaceRole.OWNER
            );
            if (ownerCount <= 1) {
                throw new LastOwnerRemovalException(context.slug());
            }
        }
    }
}
