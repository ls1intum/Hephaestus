package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing workspace memberships.
 * <p>
 * Handles CRUD operations for workspace memberships, including:
 * <ul>
 * <li>Creating and removing memberships</li>
 * <li>Updating member roles</li>
 * <li>Syncing GitHub organization members with workspace memberships</li>
 * </ul>
 */
@Service
public class WorkspaceMembershipService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMembershipService.class);

    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceRepository workspaceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public WorkspaceMembershipService(
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Additively ensures that every given user has a {@link WorkspaceMembership} in
     * the workspace, without touching existing members or roles.
     * <p>
     * Uses the race-safe native upsert in
     * {@link WorkspaceMembershipRepository#insertIfAbsent}: if a membership already
     * exists for the user, it is left untouched (role and hidden flag preserved).
     * Only missing memberships are created with role {@code MEMBER}.
     * <p>
     * Intended for reconciliation paths that discover users via the team graph or
     * other side channels and must never downgrade existing OWNER/ADMIN roles.
     *
     * @param workspace the workspace to ensure memberships in
     * @param userIds   the set of user IDs that should have a membership
     * @return the number of users processed (0 if workspace or userIds are null/empty)
     */
    @Transactional
    public int ensureMemberships(Workspace workspace, Set<Long> userIds) {
        if (workspace == null || workspace.getId() == null) {
            return 0;
        }
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        Long workspaceId = workspace.getId();
        int inserted = 0;
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            inserted += workspaceMembershipRepository.insertIfAbsent(
                workspaceId,
                userId,
                WorkspaceMembership.WorkspaceRole.MEMBER.name()
            );
        }

        if (inserted > 0) {
            log.info(
                "Ensured workspace memberships from team graph: workspaceId={}, considered={}, created={}",
                workspaceId,
                userIds.size(),
                inserted
            );
        }
        return inserted;
    }

    @Transactional
    public void syncWorkspaceMembers(Workspace workspace, Map<Long, WorkspaceMembership.WorkspaceRole> desiredRoles) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }

        Map<Long, WorkspaceMembership.WorkspaceRole> normalizedRoles =
            desiredRoles == null ? Collections.<Long, WorkspaceMembership.WorkspaceRole>emptyMap() : desiredRoles;

        List<WorkspaceMembership> existingMembers = workspaceMembershipRepository.findByWorkspace_Id(workspace.getId());
        Map<Long, WorkspaceMembership> existingByUserId = existingMembers
            .stream()
            .filter(member -> member.getUser() != null && member.getUser().getId() != null)
            .collect(Collectors.toMap(member -> member.getUser().getId(), Function.identity()));

        Set<Long> desiredUserIds = new HashSet<>(normalizedRoles.keySet());

        List<WorkspaceMembership> toCreate = new ArrayList<>();
        List<WorkspaceMembership> toUpdate = new ArrayList<>();
        List<WorkspaceMembership> toDelete = new ArrayList<>();

        for (Map.Entry<Long, WorkspaceMembership.WorkspaceRole> entry : normalizedRoles.entrySet()) {
            Long userId = entry.getKey();
            if (userId == null) {
                continue;
            }
            WorkspaceMembership.WorkspaceRole desiredRole = Optional.ofNullable(entry.getValue()).orElse(
                WorkspaceMembership.WorkspaceRole.MEMBER
            );
            WorkspaceMembership existing = existingByUserId.get(userId);
            if (existing == null) {
                // Use find() instead of getReference() to avoid lazy EntityNotFoundException
                User user = entityManager.find(User.class, userId);
                if (user == null) {
                    log.warn(
                        "Skipped workspace membership creation: reason=userNotFound, userId={}, workspaceId={}",
                        userId,
                        workspace.getId()
                    );
                    continue;
                }
                WorkspaceMembership member = createMembershipInternal(workspace, user, desiredRole);
                toCreate.add(member);
            } else if (existing.getRole() != desiredRole) {
                existing.setRole(desiredRole);
                toUpdate.add(existing);
            }
        }

        for (WorkspaceMembership member : existingMembers) {
            Long memberUserId = member.getUser() != null ? member.getUser().getId() : null;
            if (memberUserId == null || desiredUserIds.contains(memberUserId)) {
                continue;
            }
            // Preserve memberships an admin has explicitly hidden from the practice roster.
            // `hidden=true` is a sticky, admin-authored signal that must survive org-sync
            // churn (transient API gaps, webhook reorder, remove-then-re-add). Deleting
            // the row would lose that signal on re-creation and silently un-hide the user.
            if (member.isHidden()) {
                log.debug(
                    "Preserved hidden workspace membership during sync: workspaceId={}, userId={}",
                    workspace.getId(),
                    memberUserId
                );
                continue;
            }
            toDelete.add(member);
        }

        if (!toCreate.isEmpty()) {
            workspaceMembershipRepository.saveAll(toCreate);
        }
        if (!toUpdate.isEmpty()) {
            workspaceMembershipRepository.saveAll(toUpdate);
        }
        if (!toDelete.isEmpty()) {
            workspaceMembershipRepository.deleteAll(toDelete);
        }
    }

    @Transactional
    public WorkspaceMembership createMembership(
        Workspace workspace,
        Long userId,
        WorkspaceMembership.WorkspaceRole role
    ) {
        if (workspace == null || workspace.getId() == null) {
            throw new IllegalArgumentException("Workspace must not be null and must have an ID");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User userReference = entityManager.find(User.class, userId);
        if (userReference == null) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        // Check if membership already exists
        Optional<WorkspaceMembership> existing = workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(
            workspace.getId(),
            userId
        );
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "Membership already exists for workspace " + workspace.getId() + " and user " + userId
            );
        }

        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspace(workspace);
        membership.setUser(userReference);
        membership.setRole(role);
        membership.setId(new WorkspaceMembership.Id(workspace.getId(), userId));

        return workspaceMembershipRepository.save(membership);
    }

    /**
     * Assign or update a role for a workspace member.
     * Creates a new membership if the user is not yet a member.
     *
     * @param workspaceId Workspace ID
     * @param userId      User ID
     * @param role        Role to assign
     * @return Updated or created membership
     */
    @Transactional
    public WorkspaceMembership assignRole(Long workspaceId, Long userId, WorkspaceMembership.WorkspaceRole role) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        var membershipOpt = workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId);

        if (membershipOpt.isPresent()) {
            // Update existing membership
            WorkspaceMembership membership = membershipOpt.get();
            membership.setRole(role);
            log.info("Updated membership role: userId={}, workspaceId={}, role={}", userId, workspaceId, role);
            return workspaceMembershipRepository.save(membership);
        } else {
            // Create new membership
            User user = entityManager.find(User.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }

            WorkspaceMembership membership = createMembershipInternal(workspace, user, role);
            log.info("Created membership: userId={}, workspaceId={}, role={}", userId, workspaceId, role);
            return workspaceMembershipRepository.save(membership);
        }
    }

    /**
     * Remove a user's membership from a workspace.
     *
     * @param workspaceId Workspace ID
     * @param userId      User ID
     */
    @Transactional
    public void removeMembership(Long workspaceId, Long userId) {
        var membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found"));

        workspaceMembershipRepository.delete(membership);
        log.info("Removed membership: userId={}, workspaceId={}", userId, workspaceId);
    }

    private WorkspaceMembership createMembershipInternal(Workspace workspace, User user) {
        return createMembershipInternal(workspace, user, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    private WorkspaceMembership createMembershipInternal(
        Workspace workspace,
        User user,
        WorkspaceMembership.WorkspaceRole role
    ) {
        WorkspaceMembership member = new WorkspaceMembership();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        member.setId(new WorkspaceMembership.Id(workspace.getId(), user.getId()));
        return member;
    }

    // Hidden member methods

    /**
     * Toggle the hidden flag for a workspace member.
     *
     * @param workspaceId Workspace ID
     * @param userId      User ID
     * @param hidden      whether the member should be hidden
     * @return Updated membership
     */
    @Transactional
    public WorkspaceMembership updateMemberVisibility(Long workspaceId, Long userId, boolean hidden) {
        WorkspaceMembership membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found"));
        membership.setHidden(hidden);
        return workspaceMembershipRepository.save(membership);
    }

    /**
     * Returns the set of user IDs that are hidden in a workspace.
     *
     * @param workspaceId Workspace ID
     * @return Set of hidden user IDs
     */
    @Transactional(readOnly = true)
    public Set<Long> getHiddenMemberIds(Long workspaceId) {
        return workspaceMembershipRepository.findHiddenUserIdsByWorkspaceId(workspaceId);
    }

    // Query methods for controller

    /**
     * Gets a workspace membership by workspace and user ID.
     *
     * @param workspaceId Workspace ID
     * @param userId User ID
     * @return The membership
     * @throws IllegalArgumentException if membership not found
     */
    @Transactional(readOnly = true)
    public WorkspaceMembership getMembership(Long workspaceId, Long userId) {
        return workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace membership not found"));
    }

    /**
     * Gets a workspace membership by workspace and user ID, or empty if not found.
     *
     * @param workspaceId Workspace ID
     * @param userId User ID
     * @return Optional containing the membership if found
     */
    @Transactional(readOnly = true)
    public Optional<WorkspaceMembership> findMembership(Long workspaceId, Long userId) {
        return workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId);
    }

    /**
     * Lists all members of a workspace with pagination.
     *
     * @param workspaceId Workspace ID
     * @param pageable Pagination parameters
     * @return Page of workspace memberships
     */
    @Transactional(readOnly = true)
    public Page<WorkspaceMembership> listMembers(Long workspaceId, Pageable pageable) {
        return workspaceMembershipRepository.findAllByWorkspace_Id(workspaceId, pageable);
    }

    /**
     * Checks if removing a member would leave the workspace without owners.
     *
     * @param workspaceId Workspace ID
     * @param membership The membership to check
     * @return true if the membership is the last owner
     */
    @Transactional(readOnly = true)
    public boolean isLastOwner(Long workspaceId, WorkspaceMembership membership) {
        if (membership.getRole() != WorkspaceMembership.WorkspaceRole.OWNER) {
            return false;
        }
        long ownerCount = workspaceMembershipRepository.countByWorkspace_IdAndRole(
            workspaceId,
            WorkspaceMembership.WorkspaceRole.OWNER
        );
        return ownerCount <= 1;
    }
}
