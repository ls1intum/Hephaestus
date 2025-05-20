package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.*;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.permission.*;
import de.tum.in.www1.hephaestus.gitprovider.user.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubTeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamSyncService.class);

    private final GitHub gitHub;
    private final TeamV2Repository teamRepo;
    private final UserRepository userRepo;
    private final RepositoryRepository repositoryRepo;
    private final GitHubTeamConverter teamConverter;

    public GitHubTeamSyncService(
            GitHub gitHub,
            TeamV2Repository teamRepo,
            UserRepository userRepo,
            RepositoryRepository repoRepo,
            GitHubTeamConverter teamConverter
    ) {
        this.gitHub = gitHub;
        this.teamRepo = teamRepo;
        this.userRepo = userRepo;
        this.repositoryRepo = repoRepo;
        this.teamConverter = teamConverter;
    }

    @Transactional
    public void syncAndSaveTeams(String orgName) throws IOException {
        GHOrganization org = gitHub.getOrganization(orgName);
        for (GHTeam ghTeam : org.listTeams()) {
            TeamV2 team = teamRepo.findById(ghTeam.getId())
                    .orElseGet(() -> teamConverter.create(ghTeam, org.getLogin()));

            teamConverter.update(ghTeam, org.getLogin(), team);
            syncTeamState(ghTeam, team);

            log.info("Synced team {} having {} members with {} repository permissions",
                    team.getSlug(),
                    team.getMemberships().size(),
                    team.getRepoPermissions().size());
        }
    }

    private void addMembers(
            PagedIterable<GHUser> ghUsers,
            TeamMembership.Role role,
            TeamV2 team,
            Set<TeamMembership> target
    ) {
        for (GHUser ghUser : ghUsers) {
            long id = ghUser.getId();
            if (!userRepo.existsById(id)) continue;

            // already present?  -> update role if needed
            TeamMembership existing = team.getMemberships().stream()
                    .filter(tm -> tm.getUser().getId().equals(id))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                // keep the higher role if it was MAINTAINER
                if (role == TeamMembership.Role.MAINTAINER)
                    existing.setRole(TeamMembership.Role.MAINTAINER);
                continue;
            }

            User ref = userRepo.getReferenceById(id);
            target.add(new TeamMembership(team, ref, role));
        }
    }

    private void syncRepoPermissions(TeamV2 team, GHTeam ghTeam) {

        Set<TeamRepositoryPermission> fresh = new HashSet<>();

        for (GHRepository ghRepo : ghTeam.listRepositories()) {
            long repoId = ghRepo.getId();

            // skip unknown repos
            if (!repositoryRepo.existsById(repoId)) {
                continue;
            }

            Repository repoRef = repositoryRepo.getReferenceById(repoId);
            boolean admin = ghRepo.hasAdminAccess();
            boolean push  = ghRepo.hasPushAccess();

            TeamRepositoryPermission.PermissionLevel level =
                    admin ? TeamRepositoryPermission.PermissionLevel.ADMIN
                            : push  ? TeamRepositoryPermission.PermissionLevel.WRITE
                            : TeamRepositoryPermission.PermissionLevel.READ;

            fresh.add(new TeamRepositoryPermission(team, repoRef, level));
        }

        team.clearAndAddRepoPermissions(fresh);
    }

    @Transactional
    public void processTeam(GHTeam ghTeam) {
        try {
            String orgName = ghTeam.getOrganization().getLogin();
            TeamV2 team = teamRepo.findById(ghTeam.getId())
                    .orElseGet(() -> teamConverter.create(ghTeam, orgName));

            teamConverter.update(ghTeam, orgName, team);

            syncTeamState(ghTeam, team);

            log.info("Processed team={}  members={}  repoPerms={}",
                    team.getSlug(),
                    team.getMemberships().size(),
                    team.getRepoPermissions().size());
        } catch (IOException e) {
            log.error("Failed to process team {}: {}", ghTeam.getId(), e.getMessage());
        }
    }

    private void syncTeamState(GHTeam ghTeam, TeamV2 team) throws IOException {
        Set<TeamMembership> fresh = new HashSet<>();
        addMembers(ghTeam.listMembers(), TeamMembership.Role.MEMBER, team, fresh);
        addMembers(ghTeam.listMembers(GHTeam.Role.MAINTAINER), TeamMembership.Role.MAINTAINER, team, fresh);

        team.getMemberships().clear();
        fresh.forEach(team::addMembership);

        syncRepoPermissions(team, ghTeam);
        teamRepo.save(team);
    }
}
