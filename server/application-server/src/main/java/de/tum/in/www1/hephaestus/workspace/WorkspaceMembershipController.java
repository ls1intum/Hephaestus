package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.authorization.WorkspaceAccessService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import de.tum.in.www1.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import de.tum.in.www1.hephaestus.workspace.exception.InsufficientWorkspacePermissionsException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing workspace memberships and roles.
 * Handles CRUD operations for workspace members and role assignments.
 */
@WorkspaceScopedController
@RequestMapping("/members")
public class WorkspaceMembershipController {

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceAccessService accessService;

    /**
     * Get the current user's membership in this workspace.
     */
    @GetMapping("/me")
    public ResponseEntity<WorkspaceMembershipDTO> getCurrentUserMembership(WorkspaceContext context) {
        var currentUser = userRepository.getCurrentUser().orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var userOpt = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(context.id(), currentUser.getId())
            .orElse(null);

        if (userOpt == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(WorkspaceMembershipDTO.from(userOpt));
    }

    /**
     * List all members of the workspace with pagination.
     * Accessible to all workspace members (MEMBER role and above).
     *
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
        // Limit page size to 100
        int pageSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").ascending());

        var memberships = workspaceMembershipRepository
            .findAllByWorkspace_Id(context.id(), pageable)
            .map(WorkspaceMembershipDTO::from)
            .getContent();

        return ResponseEntity.ok(memberships);
    }

    /**
     * Get a specific member's details.
     * Accessible to all workspace members.
     *
     * @param userId User ID
     * @return Workspace membership details
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getMember(WorkspaceContext context, @PathVariable Long userId) {
        var membership = workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(context.id(), userId);

        if (membership.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(WorkspaceMembershipDTO.from(membership.get()));
    }

    /**
     * Assign or update a role for a workspace member.
     * OWNER can assign any role. ADMIN can assign ADMIN or MEMBER roles.
     *
     * @param request Role assignment request
     * @return Updated membership
     */
    @PostMapping("/assign")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<?> assignRole(WorkspaceContext context, @Valid @RequestBody AssignRoleRequestDTO request) {
        // Check if user can manage this role
        if (!accessService.canManageRole(request.role())) {
            throw new InsufficientWorkspacePermissionsException(
                context.slug(),
                "You cannot assign the " + request.role() + " role"
            );
        }

        try {
            var membership = workspaceMembershipService.assignRole(context.id(), request.userId(), request.role());
            return ResponseEntity.ok(WorkspaceMembershipDTO.from(membership));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Revoke a user's membership (remove them from workspace).
     * OWNER can remove anyone except themselves if they are the last OWNER.
     * ADMIN can remove MEMBER and ADMIN roles.
     *
     * @param userId User ID to remove
     * @return 204 No Content on success
     */
    @DeleteMapping("/{userId}")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<?> removeMember(WorkspaceContext context, @PathVariable Long userId) {
        try {
            // Get current membership to check role
            var membership = workspaceMembershipRepository
                .findByWorkspace_IdAndUser_Id(context.id(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Workspace membership not found"));

            // Check if user can manage this role
            if (!accessService.canManageRole(membership.getRole())) {
                throw new InsufficientWorkspacePermissionsException(
                    context.slug(),
                    "You cannot remove a member with " + membership.getRole() + " role"
                );
            }

            // Prevent removing the last OWNER
            if (membership.getRole() == WorkspaceRole.OWNER) {
                long ownerCount = workspaceMembershipRepository.countByWorkspace_IdAndRole(
                    context.id(),
                    WorkspaceRole.OWNER
                );
                if (ownerCount <= 1) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                        Map.of("error", "Cannot remove the last OWNER from the workspace")
                    );
                }
            }

            workspaceMembershipService.removeMembership(context.id(), userId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
