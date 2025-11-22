package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.meta.ContributorDTO;
import de.tum.in.www1.hephaestus.meta.MetaDataDTO;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
class WorkspaceMetaControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    @WithAdminUser
    void metaEndpointReturnsWorkspaceScopedTeams() {
        teamRepository.deleteAll();
        User owner = persistUser("meta-owner");
        Workspace workspace = createWorkspace("meta-space", "Meta Space", "meta-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        workspace.setLeaderboardScheduleDay(3);
        workspace.setLeaderboardScheduleTime("11:45");
        workspaceRepository.save(workspace);

        teamRepository.save(buildTeam(101L, "Workspace Team", workspace.getAccountLogin()));
        teamRepository.save(buildTeam(202L, "Other Team", "another-org"));

        MetaDataDTO response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/meta", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(MetaDataDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.teams()).extracting(TeamInfoDTO::name).containsExactly("Workspace Team");
        assertThat(response.scheduledDay()).isEqualTo("3");
        assertThat(response.scheduledTime()).isEqualTo("11:45");
    }

    @Test
    @WithAdminUser
    void workspaceContributorsEndpointRespectsWorkspaceScope() {
        User owner = persistUser("meta-contrib-owner");
        Workspace workspace = createWorkspace(
            "contributors-space",
            "Contrib Space",
            "contrib-org",
            AccountType.ORG,
            owner
        );
        ensureAdminMembership(workspace);

        var response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/meta/contributors", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(ContributorDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response).isEmpty();
    }

    private Team buildTeam(Long id, String name, String organization) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setOrganization(organization);
        team.setCreatedAt(Instant.now());
        team.setUpdatedAt(Instant.now());
        return team;
    }
}
