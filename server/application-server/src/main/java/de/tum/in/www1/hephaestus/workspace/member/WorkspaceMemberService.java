package de.tum.in.www1.hephaestus.workspace.member;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
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
public class WorkspaceMemberService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceMemberService.class);

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EntityManager entityManager;

    public WorkspaceMemberService(
        WorkspaceMemberRepository workspaceMemberRepository,
        WorkspaceRepository workspaceRepository,
        EntityManager entityManager
    ) {
        this.workspaceMemberRepository = workspaceMemberRepository;
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
        Map<Long, WorkspaceMember> existing = workspaceMemberRepository
            .findAllByWorkspace_IdAndUser_IdIn(workspace.getId(), userIds)
            .stream()
            .collect(Collectors.toMap(member -> member.getUser().getId(), member -> member));

        List<WorkspaceMember> toPersist = new ArrayList<>();
        for (User user : users) {
            Long userId = user.getId();
            if (userId == null || existing.containsKey(userId)) {
                continue;
            }
            WorkspaceMember member = createMembership(workspace, user);
            member.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
            existing.put(userId, member);
            toPersist.add(member);
        }

        if (!toPersist.isEmpty()) {
            workspaceMemberRepository.saveAll(toPersist);
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
        WorkspaceMember member = workspaceMemberRepository
            .findByWorkspace_IdAndUser_Id(workspaceOptional.get().getId(), user.getId())
            .orElseGet(() -> {
                WorkspaceMember created = createMembership(workspaceOptional.get(), user);
                created.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
                return workspaceMemberRepository.save(created);
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
        WorkspaceMember member = workspaceMemberRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())
            .orElseGet(() -> createMembership(workspace, user));
        member.setLeaguePoints(newPoints);
        workspaceMemberRepository.save(member);
    }

    @Transactional
    public void resetLeaguePoints(Optional<Workspace> workspaceOptional, int points) {
        if (workspaceOptional.isEmpty()) {
            logger.debug("Skipping league point reset because no workspace is configured.");
            return;
        }
        Workspace workspace = workspaceOptional.get();
        List<WorkspaceMember> members = workspaceMemberRepository.findAllByWorkspace_Id(workspace.getId());
        if (members.isEmpty()) {
            return;
        }
        members.forEach(member -> member.setLeaguePoints(points));
        workspaceMemberRepository.saveAll(members);
    }

    @Transactional
    public void syncWorkspaceMembers(Workspace workspace, Map<Long, WorkspaceMember.Role> desiredRoles) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }

        Map<Long, WorkspaceMember.Role> normalizedRoles =
            desiredRoles == null ? Collections.<Long, WorkspaceMember.Role>emptyMap() : desiredRoles;

        List<WorkspaceMember> existingMembers = workspaceMemberRepository.findAllByWorkspace_Id(workspace.getId());
        Map<Long, WorkspaceMember> existingByUserId = existingMembers
            .stream()
            .filter(member -> member.getUser() != null && member.getUser().getId() != null)
            .collect(Collectors.toMap(member -> member.getUser().getId(), Function.identity()));

        Set<Long> desiredUserIds = new HashSet<>(normalizedRoles.keySet());

        List<WorkspaceMember> toCreate = new ArrayList<>();
        List<WorkspaceMember> toUpdate = new ArrayList<>();
        List<WorkspaceMember> toDelete = new ArrayList<>();

        for (Map.Entry<Long, WorkspaceMember.Role> entry : normalizedRoles.entrySet()) {
            Long userId = entry.getKey();
            if (userId == null) {
                continue;
            }
            WorkspaceMember.Role desiredRole = Optional
                .ofNullable(entry.getValue())
                .orElse(WorkspaceMember.Role.MEMBER);
            WorkspaceMember existing = existingByUserId.get(userId);
            if (existing == null) {
                User userReference = entityManager.getReference(User.class, userId);
                WorkspaceMember member = createMembership(workspace, userReference, desiredRole);
                member.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
                toCreate.add(member);
            } else if (existing.getRole() != desiredRole) {
                existing.setRole(desiredRole);
                toUpdate.add(existing);
            }
        }

        for (WorkspaceMember member : existingMembers) {
            Long memberUserId = member.getUser() != null ? member.getUser().getId() : null;
            if (memberUserId != null && !desiredUserIds.contains(memberUserId)) {
                toDelete.add(member);
            }
        }

        if (!toCreate.isEmpty()) {
            workspaceMemberRepository.saveAll(toCreate);
        }
        if (!toUpdate.isEmpty()) {
            workspaceMemberRepository.saveAll(toUpdate);
        }
        if (!toDelete.isEmpty()) {
            workspaceMemberRepository.deleteAll(toDelete);
        }
    }

    private WorkspaceMember createMembership(Workspace workspace, User user) {
        return createMembership(workspace, user, WorkspaceMember.Role.MEMBER);
    }

    private WorkspaceMember createMembership(Workspace workspace, User user, WorkspaceMember.Role role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        member.setId(new WorkspaceMember.Id(workspace.getId(), user.getId()));
        return member;
    }
}
