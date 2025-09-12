package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2Repository;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("GitHub Team MessageHandler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubTeamMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubTeamMessageHandler teamHandler;

    @Autowired
    private TeamV2Repository teamRepo;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @BeforeEach
    void setup() {
        // Arrange (common): ensure clean state for independent tests
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("org created -> team persisted with basics")
    void orgCreated_persistsTeamBasics(@GitHubPayload("team.org.created") GHEventPayload.Team payload) {
        // Arrange
        assertThat(payload.getAction()).isEqualTo("created");
        long teamId = payload.getTeam().getId();

        // Act
        teamHandler.handleEvent(payload);

        // Assert
        var teamOpt = teamRepo.findById(teamId);
        assertThat(teamOpt).isPresent();
        var team = teamOpt.get();
        assertThat(team.getName()).isEqualTo("HephaestusSubTeam");
        assertThat(team.getOrganization()).isEqualTo("HephaestusTest");
        assertThat(team.getPrivacy()).isEqualTo(TeamV2.Privacy.CLOSED);
        assertThat(team.getHtmlUrl()).isEqualTo("https://github.com/orgs/HephaestusTest/teams/hephaestussubteam");
    }

    @Test
    @DisplayName("org edited -> team name updated")
    void orgEdited_updatesTeamName(
        @GitHubPayload("team.org.created") GHEventPayload.Team createPayload,
        @GitHubPayload("team.org.edited") GHEventPayload.Team editedPayload
    ) {
        // Arrange
        teamHandler.handleEvent(createPayload);

        // Act
        teamHandler.handleEvent(editedPayload);

        // Assert
        var teamAfter = teamRepo.findById(createPayload.getTeam().getId()).orElseThrow();
        assertThat(teamAfter.getName()).isEqualTo("HephaestusSubTeamRenamed");
        assertThat(teamAfter.getOrganization()).isEqualTo("HephaestusTest");
    }

    @Test
    @DisplayName("org deleted -> team removed")
    void orgDeleted_removesTeam(
        @GitHubPayload("team.org.deleted") GHEventPayload.Team deletePayload,
        @GitHubPayload("team.org.created") GHEventPayload.Team createPayload
    ) {
        // Arrange
        teamHandler.handleEvent(createPayload);
        assertThat(teamRepo.findById(deletePayload.getTeam().getId())).isPresent();

        // Act
        teamHandler.handleEvent(deletePayload);

        // Assert
        assertThat(teamRepo.findById(deletePayload.getTeam().getId())).isNotPresent();
    }

    @Test
    @DisplayName("repo added -> repository persisted")
    void repoAdded_persistsRepository(@GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team payload) {
        // Arrange
        long repoId = payload.getRepository().getId();

        // Act
        teamHandler.handleEvent(payload);

        // Assert
        var repo = repositoryRepository.findById(repoId).orElseThrow();
        assertThat(repo.getNameWithOwner()).isEqualTo("HephaestusTest/TestRepository");
        assertThat(repo.getDefaultBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("repo added -> ADMIN permission granted")
    void repoAdded_grantsAdminPermission(@GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team payload) {
        // Arrange
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        // Act
        teamHandler.handleEvent(payload);

        // Assert
        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team.getRepoPermissions().stream().filter(p -> p.getRepository().getId().equals(repoId)).findFirst();
        assertThat(perm).isPresent();
        assertThat(perm.get().getPermission()).isEqualTo(TeamRepositoryPermission.PermissionLevel.ADMIN);
    }

    @Test
    @DisplayName("repo edited -> permission updated to WRITE")
    void repoEdited_updatesPermissionToWrite(
        @GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team added,
        @GitHubPayload("team.repo.edited") GHEventPayload.Team payload
    ) {
        // Arrange
        teamHandler.handleEvent(added);
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        // Act
        teamHandler.handleEvent(payload);

        // Assert
        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team.getRepoPermissions().stream().filter(p -> p.getRepository().getId().equals(repoId)).findFirst();
        assertThat(perm).isPresent();
        assertThat(perm.get().getPermission()).isEqualTo(TeamRepositoryPermission.PermissionLevel.WRITE);
    }

    @Test
    @DisplayName("repo removed -> permission removed")
    void repoRemoved_removesPermission(
        @GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team added,
        @GitHubPayload("team.repo.removed_from_repository") GHEventPayload.Team payload
    ) {
        // Arrange
        teamHandler.handleEvent(added);
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        // Act
        teamHandler.handleEvent(payload);

        // Assert
        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team.getRepoPermissions().stream().filter(p -> p.getRepository().getId().equals(repoId)).findFirst();
        assertThat(perm).isNotPresent();
    }
}
