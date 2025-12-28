package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
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
    private TeamRepository teamRepo;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("org created -> team persisted with basics")
    void orgCreated_persistsTeamBasics(@GitHubPayload("team.org.created") GHEventPayload.Team payload) {
        assertThat(payload.getAction()).isEqualTo("created");
        long teamId = payload.getTeam().getId();

        teamHandler.handleEvent(payload);

        var teamOpt = teamRepo.findById(teamId);
        assertThat(teamOpt).isPresent();
        var team = teamOpt.get();
        assertThat(team.getName()).isEqualTo("HephaestusSubTeam");
        assertThat(team.getOrganization()).isEqualTo("HephaestusTest");
        assertThat(team.getPrivacy()).isEqualTo(Team.Privacy.CLOSED);
        assertThat(team.getHtmlUrl()).isEqualTo("https://github.com/orgs/HephaestusTest/teams/hephaestussubteam");
    }

    @Test
    @DisplayName("org edited -> team name updated")
    void orgEdited_updatesTeamName(
        @GitHubPayload("team.org.created") GHEventPayload.Team createPayload,
        @GitHubPayload("team.org.edited") GHEventPayload.Team editedPayload
    ) {
        teamHandler.handleEvent(createPayload);
        teamHandler.handleEvent(editedPayload);

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
        teamHandler.handleEvent(createPayload);
        assertThat(teamRepo.findById(deletePayload.getTeam().getId())).isPresent();

        teamHandler.handleEvent(deletePayload);

        assertThat(teamRepo.findById(deletePayload.getTeam().getId())).isNotPresent();
    }

    @Test
    @DisplayName("repo added -> repository persisted")
    void repoAdded_persistsRepository(@GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team payload) {
        long repoId = payload.getRepository().getId();

        teamHandler.handleEvent(payload);

        var repo = repositoryRepository.findById(repoId).orElseThrow();
        assertThat(repo.getNameWithOwner()).isEqualTo("HephaestusTest/TestRepository");
        assertThat(repo.getDefaultBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("repo added -> ADMIN permission granted")
    void repoAdded_grantsAdminPermission(@GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team payload) {
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        teamHandler.handleEvent(payload);

        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team
            .getRepoPermissions()
            .stream()
            .filter(p -> p.getRepository().getId().equals(repoId))
            .findFirst();
        assertThat(perm).isPresent();
        assertThat(perm.get().getPermission()).isEqualTo(TeamRepositoryPermission.PermissionLevel.ADMIN);
    }

    @Test
    @DisplayName("repo edited -> permission updated to WRITE")
    void repoEdited_updatesPermissionToWrite(
        @GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team added,
        @GitHubPayload("team.repo.edited") GHEventPayload.Team payload
    ) {
        teamHandler.handleEvent(added);
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        teamHandler.handleEvent(payload);

        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team
            .getRepoPermissions()
            .stream()
            .filter(p -> p.getRepository().getId().equals(repoId))
            .findFirst();
        assertThat(perm).isPresent();
        assertThat(perm.get().getPermission()).isEqualTo(TeamRepositoryPermission.PermissionLevel.WRITE);
    }

    @Test
    @DisplayName("repo removed -> permission removed")
    void repoRemoved_removesPermission(
        @GitHubPayload("team.repo.added_to_repository") GHEventPayload.Team added,
        @GitHubPayload("team.repo.removed_from_repository") GHEventPayload.Team payload
    ) {
        teamHandler.handleEvent(added);
        long teamId = payload.getTeam().getId();
        long repoId = payload.getRepository().getId();

        teamHandler.handleEvent(payload);

        var team = teamRepo.findById(teamId).orElseThrow();
        var perm = team
            .getRepoPermissions()
            .stream()
            .filter(p -> p.getRepository().getId().equals(repoId))
            .findFirst();
        assertThat(perm).isNotPresent();
    }
}
