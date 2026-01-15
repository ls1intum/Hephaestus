package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.shared.LeaguePointsConstants.POINTS_DEFAULT;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <li>Managing league points snapshots</li>
 * </ul>
 */
@Service
public class WorkspaceMembershipService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMembershipService.class);

    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EntityManager entityManager;

    public WorkspaceMembershipService(
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceRepository workspaceRepository,
        EntityManager entityManager
    ) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceRepository = workspaceRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Map<Long, Integer> getLeaguePointsSnapshot(Collection<User> users, Long workspaceId) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> userIds = users.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        if (workspaceId == null) {
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> POINTS_DEFAULT));
        }

        Set<Long> existingUserIds = workspaceMembershipRepository
            .findAllByWorkspace_IdAndUser_IdIn(workspaceId, userIds)
            .stream()
            .map(member -> member.getUser().getId())
            .collect(Collectors.toSet());

        for (User user : users) {
            Long userId = user.getId();
            if (userId == null || existingUserIds.contains(userId)) {
                continue;
            }
            workspaceMembershipRepository.insertIfAbsent(
                workspaceId,
                userId,
                WorkspaceMembership.WorkspaceRole.MEMBER.name(),
                POINTS_DEFAULT
            );
        }

        Map<Long, Integer> leaguePointsByUserId = workspaceMembershipRepository
            .findAllByWorkspace_IdAndUser_IdIn(workspaceId, userIds)
            .stream()
            .collect(Collectors.toMap(member -> member.getUser().getId(), WorkspaceMembership::getLeaguePoints));

        for (Long userId : userIds) {
            leaguePointsByUserId.putIfAbsent(userId, POINTS_DEFAULT);
        }

        return leaguePointsByUserId;
    }

    @Transactional
    public int getCurrentLeaguePoints(Long workspaceId, User user) {
        if (user == null || user.getId() == null) {
            return POINTS_DEFAULT;
        }
        if (workspaceId == null) {
            return POINTS_DEFAULT;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return POINTS_DEFAULT;
        }

        WorkspaceMembership member = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, user.getId())
            .orElseGet(() -> {
                WorkspaceMembership created = createMembershipInternal(workspace, user);
                created.setLeaguePoints(POINTS_DEFAULT);
                return workspaceMembershipRepository.save(created);
            });
        return member.getLeaguePoints();
    }

    @Transactional
    public void updateLeaguePoints(Long workspaceId, User user, int newPoints) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (workspaceId == null) {
            log.debug("Skipped league point update: reason=noWorkspaceConfigured, userLogin={}", user.getLogin());
            return;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            log.debug(
                "Skipped league point update: reason=workspaceNotFound, userLogin={}, workspaceId={}",
                user.getLogin(),
                workspaceId
            );
            return;
        }

        WorkspaceMembership member = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceId, user.getId())
            .orElseGet(() -> createMembershipInternal(workspace, user));
        member.setLeaguePoints(newPoints);
        workspaceMembershipRepository.save(member);
    }

    @Transactional
    public void resetLeaguePoints(Long workspaceId, int points) {
        if (workspaceId == null) {
            log.debug("Skipped league point reset: reason=noWorkspaceConfigured");
            return;
        }

        List<WorkspaceMembership> members = workspaceMembershipRepository.findByWorkspace_Id(workspaceId);
        if (members.isEmpty()) {
            return;
        }
        members.forEach(member -> member.setLeaguePoints(points));
        workspaceMembershipRepository.saveAll(members);
    }

    @Transactional
    public void syncWorkspaceMembers(Workspace workspace, Map<Long, WorkspaceMembership.WorkspaceRole> desiredRoles) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }

        Map<Long, WorkspaceMembership.WorkspaceRole> normalizedRoles = desiredRoles == null
            ? Collections.<Long, WorkspaceMembership.WorkspaceRole>emptyMap()
            : desiredRoles;

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
                member.setLeaguePoints(POINTS_DEFAULT);
                toCreate.add(member);
            } else if (existing.getRole() != desiredRole) {
                existing.setRole(desiredRole);
                toUpdate.add(existing);
            }
        }

        for (WorkspaceMembership member : existingMembers) {
            Long memberUserId = member.getUser() != null ? member.getUser().getId() : null;
            if (memberUserId != null && !desiredUserIds.contains(memberUserId)) {
                toDelete.add(member);
            }
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
        membership.setLeaguePoints(POINTS_DEFAULT);
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
            membership.setLeaguePoints(POINTS_DEFAULT);
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

    // ══════════════════════════════════════════════════════════════════════════
    // Query methods for controller
    // ══════════════════════════════════════════════════════════════════════════

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
