package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceMembershipService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceMembershipService.class);

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

    public Optional<Workspace> resolveSingleWorkspace(String context) {
        List<Workspace> all = workspaceRepository.findAll();
        if (all.isEmpty()) {
            logger.debug("Skipping workspace resolution for {} because no workspaces are configured.", context);
            return Optional.empty();
        }
        if (all.size() == 1) {
            return Optional.of(all.get(0));
        }
        throw new IllegalStateException(
            "Multiple workspaces are configured; " + context + " must specify the target workspace explicitly."
        );
    }

    @Transactional
    public Map<Long, Integer> getLeaguePointsSnapshot(Collection<User> users, String context) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> userIds = users
            .stream()
            .map(User::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Optional<Workspace> workspaceOptional = resolveSingleWorkspace(context);
        if (workspaceOptional.isEmpty()) {
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> LeaguePointsCalculationService.POINTS_DEFAULT));
        }

        Workspace workspace = workspaceOptional.get();
        Map<Long, WorkspaceMembership> existing = workspaceMembershipRepository
            .findAllByWorkspace_IdAndUser_IdIn(workspace.getId(), userIds)
            .stream()
            .collect(Collectors.toMap(member -> member.getUser().getId(), member -> member));

        List<WorkspaceMembership> toPersist = new ArrayList<>();
        for (User user : users) {
            Long userId = user.getId();
            if (userId == null || existing.containsKey(userId)) {
                continue;
            }
            WorkspaceMembership member = createMembershipInternal(workspace, user);
            member.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
            existing.put(userId, member);
            toPersist.add(member);
        }

        if (!toPersist.isEmpty()) {
            workspaceMembershipRepository.saveAll(toPersist);
        }

        Map<Long, Integer> leaguePointsByUserId = new HashMap<>();
        existing.forEach((userId, member) -> leaguePointsByUserId.put(userId, member.getLeaguePoints()));
        return leaguePointsByUserId;
    }

    @Transactional
    public int getCurrentLeaguePoints(Optional<Workspace> workspaceOptional, User user) {
        if (user == null || user.getId() == null) {
            return LeaguePointsCalculationService.POINTS_DEFAULT;
        }
        if (workspaceOptional.isEmpty()) {
            return LeaguePointsCalculationService.POINTS_DEFAULT;
        }
        WorkspaceMembership member = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspaceOptional.get().getId(), user.getId())
            .orElseGet(() -> {
                WorkspaceMembership created = createMembershipInternal(workspaceOptional.get(), user);
                created.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
                return workspaceMembershipRepository.save(created);
            });
        return member.getLeaguePoints();
    }

    @Transactional
    public void updateLeaguePoints(Optional<Workspace> workspaceOptional, User user, int newPoints) {
        if (user == null || user.getId() == null) {
            return;
        }
        if (workspaceOptional.isEmpty()) {
            logger.debug("Skipping league point update for user {} because no workspace is configured.", user.getLogin());
            return;
        }
        Workspace workspace = workspaceOptional.get();
        WorkspaceMembership member = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())
            .orElseGet(() -> createMembershipInternal(workspace, user));
        member.setLeaguePoints(newPoints);
        workspaceMembershipRepository.save(member);
    }

    @Transactional
    public void resetLeaguePoints(Optional<Workspace> workspaceOptional, int points) {
        if (workspaceOptional.isEmpty()) {
            logger.debug("Skipping league point reset because no workspace is configured.");
            return;
        }
        Workspace workspace = workspaceOptional.get();
        List<WorkspaceMembership> members = workspaceMembershipRepository.findByWorkspace_Id(workspace.getId());
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
            WorkspaceMembership.WorkspaceRole desiredRole = Optional
                .ofNullable(entry.getValue())
                .orElse(WorkspaceMembership.WorkspaceRole.MEMBER);
            WorkspaceMembership existing = existingByUserId.get(userId);
            if (existing == null) {
                User userReference = entityManager.getReference(User.class, userId);
                WorkspaceMembership member = createMembershipInternal(workspace, userReference, desiredRole);
                member.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
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
        Optional<WorkspaceMembership> existing = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), userId);
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

    private WorkspaceMembership createMembershipInternal(Workspace workspace, User user) {
        return createMembershipInternal(workspace, user, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    private WorkspaceMembership createMembershipInternal(Workspace workspace, User user, WorkspaceMembership.WorkspaceRole role) {
        WorkspaceMembership member = new WorkspaceMembership();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        member.setId(new WorkspaceMembership.Id(workspace.getId(), user.getId()));
        return member;
    }
}
