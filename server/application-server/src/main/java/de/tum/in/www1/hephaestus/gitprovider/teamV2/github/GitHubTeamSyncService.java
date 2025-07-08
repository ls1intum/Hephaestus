package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.*;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.permission.*;
import de.tum.in.www1.hephaestus.gitprovider.user.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubTeamSyncService {

    private GitHubTeamSyncService self;

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamSyncService.class);

    private final GitHub gitHub;
    private final TeamV2Repository teamRepository;
    private final UserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubTeamConverter teamConverter;

    public GitHubTeamSyncService(
        GitHub gitHub,
        TeamV2Repository teamRepository,
        UserRepository userRepository,
        RepositoryRepository repositoryRepository,
        GitHubTeamConverter teamConverter
    ) {
        this.gitHub = gitHub;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.teamConverter = teamConverter;
    }

    public void syncAndSaveTeams(String orgName) throws IOException {
        GHOrganization org = gitHub.getOrganization(orgName);
        List<GHTeam> teams = org.listTeams().withPageSize(100).toList();

        teams
            .parallelStream()
            .forEach(ghTeam -> {
                // must call via the proxy (self) to trigger @Transactional on processTeam()
                TeamV2 saved = self.processTeam(ghTeam);
                if (saved == null) {
                    log.warn(
                        "Skipped team {} with following id: {} due to an error:",
                        ghTeam.getName(),
                        ghTeam.getId()
                    );
                }
            });
        // parent relationships
        teams.parallelStream().forEach(this::applyParentLinks);
    }

    @Transactional
    public TeamV2 processTeam(GHTeam ghTeam) {
        try {
            TeamV2 team = teamRepository
                .findById(ghTeam.getId())
                .map(existing -> {
                    teamConverter.update(ghTeam, existing);
                    return existing;
                })
                .orElseGet(() -> teamConverter.convert(ghTeam));

            syncMemberships(ghTeam, Objects.requireNonNull(team));
            syncRepoPermissions(ghTeam, team);
            TeamV2 saved = teamRepository.save(team);
            log.info(
                "Processed team={}, having {} members with {} repository permissions",
                team.getName(),
                team.getMemberships().size(),
                team.getRepoPermissions().size()
            );

            return saved;
        } catch (IOException e) {
            log.error("Failed to process team {}: {}", ghTeam.getId(), e.getMessage());
            return null;
        }
    }

    private void applyParentLinks(GHTeam parent) {
        try {
            Long parentId = parent.getId();

            for (GHTeam ghChild : parent.listChildTeams()) {
                long childId = ghChild.getId();

                teamRepository
                    .findById(childId)
                    .ifPresent(child -> {
                        if (!Objects.equals(child.getParentId(), parentId)) {
                            child.setParentId(parentId);
                            teamRepository.save(child);
                            log.info("Linked parent team '{}' → child team '{}'", parent.getName(), child.getName());
                        }
                    });
            }
        } catch (IOException e) {
            log.warn("Could not list child teams for {}: {}", parent.getName(), e.getMessage());
        }
    }

    private void syncMemberships(GHTeam ghTeam, TeamV2 team) throws IOException {
        Set<Long> maintainerIds = ghTeam
            .listMembers(GHTeam.Role.MAINTAINER)
            .toList()
            .stream()
            .map(GHUser::getId)
            .collect(Collectors.toSet());

        Set<Long> memberIds = ghTeam
            .listMembers(GHTeam.Role.MEMBER)
            .toList()
            .stream()
            .map(GHUser::getId)
            .collect(Collectors.toSet());

        Map<Long, TeamMembership> existing = team
            .getMemberships()
            .stream()
            .collect(Collectors.toMap(tm -> tm.getUser().getId(), tm -> tm));

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(memberIds);
        allIds.addAll(maintainerIds);

        for (Long id : allIds) {
            if (!userRepository.existsById(id)) {
                continue; //skip unknown users
            }
            TeamMembership teamMembership = existing.remove(id);
            TeamMembership.Role newRole = maintainerIds.contains(id)
                ? TeamMembership.Role.MAINTAINER
                : TeamMembership.Role.MEMBER;

            if (teamMembership != null) {
                // update the role if promoted
                if (teamMembership.getRole() != newRole) {
                    teamMembership.setRole(newRole);
                }
            } else {
                // fresh membership
                User ref = userRepository.getReferenceById(id);
                team.addMembership(new TeamMembership(team, ref, newRole));
            }
        }

        // remove any memberships that no longer exist on GitHub
        existing.values().forEach(team::removeMembership);
    }

    private void syncRepoPermissions(GHTeam ghTeam, TeamV2 team) {
        Set<TeamRepositoryPermission> fresh = new HashSet<>();
        for (GHRepository ghRepo : ghTeam.listRepositories()) {
            long repoId = ghRepo.getId();

            // skip unknown repos
            if (!repositoryRepository.existsById(repoId)) {
                continue;
            }
            Repository repoRef = repositoryRepository.getReferenceById(repoId);
            boolean admin = ghRepo.hasAdminAccess();
            boolean push = ghRepo.hasPushAccess();
            TeamRepositoryPermission.PermissionLevel level = admin
                ? TeamRepositoryPermission.PermissionLevel.ADMIN
                : push ? TeamRepositoryPermission.PermissionLevel.WRITE : TeamRepositoryPermission.PermissionLevel.READ;

            fresh.add(new TeamRepositoryPermission(team, repoRef, level));
        }
        team.clearAndAddRepoPermissions(fresh);
    }

    @Autowired
    @Lazy
    public void setSelf(GitHubTeamSyncService self) {
        this.self = self;
    }
}
