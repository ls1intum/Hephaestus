package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.github.GitHubTeamSyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubLiveTeamSyncIntegrationTest extends AbstractGitHubLiveSyncIntegrationTest {

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubTeamSyncService teamSyncService;

    @Autowired
    private TeamRepositoryPermissionRepository teamRepositoryPermissionRepository;

    @Test
    void syncsTeamsAndRepositoryPermissions() throws Exception {
        var repository = createEphemeralRepository("team-sync");
        var organization = fetchOrganization();
        var members = seedOrganizationMembers();
        Assumptions.assumeFalse(members.isEmpty(), "Organization must have members to validate team sync.");
        var maintainer = members.getFirst();
        addRepositoryCollaborator(repository, maintainer);
        var createdTeam = createEphemeralTeam(repository, organization, maintainer);

        repositorySyncService.syncRepository(workspace.getId(), repository.getFullName()).orElseThrow();

        teamSyncService.syncAndSaveTeams(installationClient, organization.getLogin());

        var permission = teamRepositoryPermissionRepository
            .findByTeam_IdAndRepository_Id(createdTeam.id(), repository.getId())
            .orElseThrow();
        assertThat(permission.getPermission()).isIn(
            de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission.PermissionLevel.MAINTAIN,
            de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission.PermissionLevel.WRITE
        );

        teamSyncService.syncAndSaveTeams(installationClient, organization.getLogin());

        var permissionsAfterSecondSync = teamRepositoryPermissionRepository
            .findByTeam_IdAndRepository_Id(createdTeam.id(), repository.getId())
            .stream()
            .collect(Collectors.toSet());
        assertThat(permissionsAfterSecondSync).hasSize(1);

        organization.getTeam(createdTeam.id()).remove(repository);
        awaitCondition("team repository permission removed remotely", () -> {
            try {
                return organization
                    .getTeam(createdTeam.id())
                    .listRepositories()
                    .toList()
                    .stream()
                    .noneMatch(remoteRepo -> remoteRepo.getId() == repository.getId());
            } catch (IOException listingError) {
                return false;
            }
        });

        teamSyncService.syncAndSaveTeams(installationClient, organization.getLogin());

        assertThat(
            teamRepositoryPermissionRepository.findByTeam_IdAndRepository_Id(createdTeam.id(), repository.getId())
        ).isEmpty();
    }
}
