package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceNotificationsRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceListItemDTO;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Workspace controller integration")
class WorkspaceControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Test
    void createWorkspaceRequiresAuthentication() {
        User owner = persistUser("unauthenticated-owner");

        var request = new CreateWorkspaceRequestDTO(
            "unauthenticated",
            "Unauthenticated",
            "unauthenticated",
            AccountType.ORG,
            owner.getId()
        );

        webTestClient
            .post()
            .uri("/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithUser(authorities = { "mentor_access" })
    void nonAdminUserCannotCreateWorkspace() {
        User owner = persistUser("non-admin-owner");

        var request = new CreateWorkspaceRequestDTO(
            "non-admin",
            "Non Admin",
            "non-admin",
            AccountType.ORG,
            owner.getId()
        );

        webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithAdminUser
    void createWorkspaceEndpointAssignsOwnerMembershipAndListsWorkspace() {
        User owner = persistUser("controller-owner");

        var request = new CreateWorkspaceRequestDTO(
            "controller-space",
            "Controller Space",
            "controller",
            AccountType.ORG,
            owner.getId()
        );

        WorkspaceDTO created = webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();

        WorkspaceDTO workspace = Objects.requireNonNull(created, "Workspace creation response was null");
        assertThat(workspace.slug()).isEqualTo("controller-space");
        assertThat(workspace.status()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE.name());

        WorkspaceDTO fetched = webTestClient
            .get()
            .uri("/workspaces/{slug}", workspace.slug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.slug()).isEqualTo(workspace.slug());

        List<WorkspaceListItemDTO> workspaces = webTestClient
            .get()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(WorkspaceListItemDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(workspaces).isNotNull();
        assertThat(workspaces).extracting(WorkspaceListItemDTO::slug).contains(workspace.slug());

        var membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.id(), owner.getId())
            .orElseThrow(() -> new AssertionError("Owner membership not created"));
        assertThat(membership.getRole()).isEqualTo(WorkspaceMembership.WorkspaceRole.OWNER);
    }

    @Test
    @WithAdminUser
    void repositoryListingAndDeletionAreScopedByWorkspaceSlug() {
        User ownerAlpha = persistUser("alpha-owner");
        User ownerBeta = persistUser("beta-owner");

        Workspace workspaceAlpha = createWorkspace("alpha-space", "Alpha", "alpha", AccountType.ORG, ownerAlpha);
        Workspace workspaceBeta = createWorkspace("beta-space", "Beta", "beta", AccountType.ORG, ownerBeta);

        RepositoryToMonitor repository = new RepositoryToMonitor();
        repository.setNameWithOwner("acme/demo-repo");
        repository.setWorkspace(workspaceAlpha);
        repository = repositoryToMonitorRepository.save(repository);
        workspaceAlpha.getRepositoriesToMonitor().add(repository);
        workspaceRepository.save(workspaceAlpha);

        String[] alphaRepositories = webTestClient
            .get()
            .uri("/workspaces/{slug}/repositories", workspaceAlpha.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String[].class)
            .returnResult()
            .getResponseBody();
        assertThat(alphaRepositories).isNotNull();
        assertThat(alphaRepositories).containsExactly("acme/demo-repo");

        String[] betaRepositories = webTestClient
            .get()
            .uri("/workspaces/{slug}/repositories", workspaceBeta.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String[].class)
            .returnResult()
            .getResponseBody();
        assertThat(betaRepositories).isNotNull();
        assertThat(betaRepositories).isEmpty();

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/repositories/{owner}/{name}", workspaceBeta.getSlug(), "acme", "demo-repo")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/repositories/{owner}/{name}", workspaceAlpha.getSlug(), "acme", "demo-repo")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();

        assertThat(repositoryToMonitorRepository.findById(repository.getId())).isEmpty();
    }

    @Test
    @WithAdminUser
    void resetLeagueEndpointRequiresExistingWorkspaceAndResetsPoints() {
        User user = persistUser("league-user");
        user.setLeaguePoints(1_500);
        userRepository.save(user);

        Workspace workspace = createWorkspace("league-space", "League", "league", AccountType.ORG, user);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/league/reset", "unknown-space")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isBadRequest();

        webTestClient
            .put()
            .uri("/workspaces/{slug}/league/reset", workspace.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getLeaguePoints()).isEqualTo(LeaguePointsCalculationService.POINTS_DEFAULT);
    }

    @Test
    @WithAdminUser
    void updateNotificationsEndpointValidatesSlackChannelPattern() {
        User owner = persistUser("notifications-owner");
        Workspace workspace = createWorkspace(
            "notifications-space",
            "Notifications",
            "notifications",
            AccountType.ORG,
            owner
        );

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/notifications", workspace.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceNotificationsRequestDTO(true, "core-team", "invalid"))
            .exchange()
            .expectStatus()
            .isBadRequest();

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/notifications", workspace.getSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceNotificationsRequestDTO(true, "core-team", "C12345678"))
            .exchange()
            .expectStatus()
            .isOk();

        Workspace updated = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(updated.getLeaderboardNotificationEnabled()).isTrue();
        assertThat(updated.getLeaderboardNotificationTeam()).isEqualTo("core-team");
        assertThat(updated.getLeaderboardNotificationChannelId()).isEqualTo("C12345678");
    }
}
