package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositoryConverter;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2Repository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.permission.TeamRepositoryPermission;
import java.util.List;
import java.util.Objects;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubTeamMessageHandler extends GitHubMessageHandler<GHEventPayload.Team> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamMessageHandler.class);

    private final TeamV2Repository teamRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubTeamConverter teamConverter;
    private final GitHubRepositoryConverter repositoryConverter;

    public GitHubTeamMessageHandler(
        TeamV2Repository teamRepository,
        RepositoryRepository repositoryRepository,
        GitHubTeamConverter teamConverter,
        GitHubRepositoryConverter repositoryConverter
    ) {
        super(GHEventPayload.Team.class);
        this.teamRepository = teamRepository;
        this.repositoryRepository = repositoryRepository;
        this.teamConverter = teamConverter;
        this.repositoryConverter = repositoryConverter;
    }

    @Override
    protected void handleEvent(GHEventPayload.Team eventPayload) {
        String action = eventPayload.getAction();
        long teamId = eventPayload.getTeam() != null ? eventPayload.getTeam().getId() : -1L;
        String orgLogin = eventPayload.getOrganization() != null ? eventPayload.getOrganization().getLogin() : null;

        logger.info("org={} action={} teamId={}", orgLogin, action, teamId);

        if ("deleted".equals(action)) {
            teamRepository.deleteById(teamId);
            return;
        }

        // Upsert team basics from payload for any non-deleted action
        TeamV2 team = upsertTeamFromPayload(eventPayload, orgLogin);

        // If repository present, handle repo-level permission updates offline
        if (eventPayload.getRepository() != null) {
            Repository repo = upsertRepositoryFromPayload(eventPayload.getRepository());
            var level = extractPermissionLevel(eventPayload.getRepository());

            if ("removed_from_repository".equals(action) || level == null) {
                // remove permission if present
                team.getRepoPermissions().removeIf(p -> Objects.equals(p.getRepository().getId(), repo.getId()));
            } else {
                // upsert permission
                var existing = team
                    .getRepoPermissions()
                    .stream()
                    .filter(p -> Objects.equals(p.getRepository().getId(), repo.getId()))
                    .findFirst()
                    .orElse(null);
                if (existing == null) {
                    team.addRepoPermission(new TeamRepositoryPermission(team, repo, level));
                } else {
                    existing.setPermission(level);
                }
            }
            teamRepository.save(team);
        }
    }

    private TeamV2 upsertTeamFromPayload(GHEventPayload.Team payload, String orgLogin) {
        if (payload.getTeam() == null) {
            return null;
        }
        GHTeam t = payload.getTeam();
        TeamV2 team = teamRepository.findById(t.getId()).orElseGet(TeamV2::new);
        team.setId(t.getId());
        team = teamConverter.update(t, team);
        // Ensure organization is taken from event payload (converter may attempt API call)
        if (orgLogin != null) {
            team.setOrganization(orgLogin);
        }
        return teamRepository.save(team);
    }

    private Repository upsertRepositoryFromPayload(GHRepository ghRepo) {
        Repository repo = repositoryRepository.findById(ghRepo.getId()).orElseGet(Repository::new);
        repo.setId(ghRepo.getId());
        repo = repositoryConverter.update(ghRepo, repo);
        return repositoryRepository.save(repo);
    }

    private TeamRepositoryPermission.PermissionLevel extractPermissionLevel(GHRepository ghRepo) {
        // map using permissions booleans only (role name is not available in this GHRepository version)
        try {
            if (ghRepo.hasAdminAccess()) {
                return TeamRepositoryPermission.PermissionLevel.ADMIN;
            }
            if (ghRepo.hasPushAccess()) {
                return TeamRepositoryPermission.PermissionLevel.WRITE;
            }
            if (ghRepo.hasPullAccess()) {
                return TeamRepositoryPermission.PermissionLevel.READ;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.TEAM;
    }

    @Override
    public GitHubMessageDomain getDomain() {
        return GitHubMessageDomain.ORGANIZATION;
    }

    @Override
    public List<GitHubMessageDomain> getAdditionalDomains() {
        return List.of(GitHubMessageDomain.REPOSITORY);
    }
}
