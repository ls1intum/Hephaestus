package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceNotificationsRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceScheduleRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.UpdateWorkspaceStatusRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceListItemDTO;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    @Test
    @WithAdminUser
    void createWorkspaceWithInvalidPayloadReturnsValidationProblemDetail() {
        var request = new CreateWorkspaceRequestDTO("INVALID SLUG", "", "", null, null);

        ProblemDetail problem = webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getProperties().get("errors"))
            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
            .containsKeys("workspaceSlug", "displayName", "accountLogin", "accountType", "ownerUserId");
    }

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

        assertThat(workspaceRepository.count()).isZero();
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

        assertThat(workspaceRepository.count()).isZero();
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
        assertThat(workspace.workspaceSlug()).isEqualTo("controller-space");
        assertThat(workspace.status()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE.name());

        WorkspaceDTO fetched = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}", workspace.workspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.workspaceSlug()).isEqualTo(workspace.workspaceSlug());

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
        assertThat(workspaces).extracting(WorkspaceListItemDTO::workspaceSlug).contains(workspace.workspaceSlug());

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
            .uri("/workspaces/{workspaceSlug}/repositories", workspaceAlpha.getWorkspaceSlug())
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
            .uri("/workspaces/{workspaceSlug}/repositories", workspaceBeta.getWorkspaceSlug())
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
            .uri(
                "/workspaces/{workspaceSlug}/repositories/{owner}/{name}",
                workspaceBeta.getWorkspaceSlug(),
                "acme",
                "demo-repo"
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();

        webTestClient
            .delete()
            .uri(
                "/workspaces/{workspaceSlug}/repositories/{owner}/{name}",
                workspaceAlpha.getWorkspaceSlug(),
                "acme",
                "demo-repo"
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        assertThat(repositoryToMonitorRepository.findById(repository.getId())).isEmpty();

        Workspace refreshedAlpha = workspaceRepository.findById(workspaceAlpha.getId()).orElseThrow();
        assertThat(refreshedAlpha.getRepositoriesToMonitor()).isEmpty();
    }

    @Test
    @WithAdminUser
    void resetLeagueEndpointRequiresExistingWorkspaceAndResetsPoints() {
        User user = persistUser("league-user");
        user.setLeaguePoints(1_500);
        userRepository.save(user);

        Workspace workspace = createWorkspace("league-space", "League", "league", AccountType.ORG, user);

        ProblemDetail missingWorkspace = webTestClient
            .put()
            .uri("/workspaces/{workspaceSlug}/league/reset", "unknown-space")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(missingWorkspace).isNotNull();
        assertThat(missingWorkspace.getTitle()).isEqualTo("Resource not found");
        assertThat(missingWorkspace.getDetail()).contains("unknown-space");

        webTestClient
            .put()
            .uri("/workspaces/{workspaceSlug}/league/reset", workspace.getWorkspaceSlug())
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
            .uri("/workspaces/{workspaceSlug}/notifications", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceNotificationsRequestDTO(true, "core-team", "invalid"))
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .value(problem -> {
                assertThat(problem.getTitle()).isEqualTo("Validation failed");
                assertThat(problem.getProperties()).containsKey("errors");
                assertThat(problem.getProperties().get("errors"))
                    .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                    .containsKey("channelId");
            });

        webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/notifications", workspace.getWorkspaceSlug())
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

    @Test
    @WithAdminUser
    void updateScheduleEndpointValidatesPayloadAndPersistsConfiguration() {
        User owner = persistUser("schedule-owner");
        Workspace workspace = createWorkspace("schedule-space", "Schedule", "schedule", AccountType.ORG, owner);

        ProblemDetail invalid = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/schedule", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceScheduleRequestDTO(9, "99:00"))
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(invalid).isNotNull();
        assertThat(invalid.getProperties().get("errors"))
            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
            .containsKeys("day", "time");

        WorkspaceDTO updated = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/schedule", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceScheduleRequestDTO(3, "08:30"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.leaderboardScheduleDay()).isEqualTo(3);
        assertThat(updated.leaderboardScheduleTime()).isEqualTo("08:30");

        Workspace reloaded = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(reloaded.getLeaderboardScheduleDay()).isEqualTo(3);
        assertThat(reloaded.getLeaderboardScheduleTime()).isEqualTo("08:30");
    }

    @Test
    @WithAdminUser
    void unknownWorkspaceReturnsProblemDetail() {
        ProblemDetail problem = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}", "missing-space")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).contains("missing-space");
    }

    @Test
    @WithAdminUser
    void duplicateWorkspaceSlugReturnsConflictProblemDetail() {
        User owner = persistUser("duplicate-owner");
        createWorkspace("duplicate-space", "Duplicate", "duplicate", AccountType.ORG, owner);

        var request = new CreateWorkspaceRequestDTO(
            "duplicate-space",
            "Duplicate",
            "duplicate",
            AccountType.ORG,
            owner.getId()
        );

        ProblemDetail problem = webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Workspace slug conflict");
        assertThat(problem.getDetail()).contains("duplicate-space");

        assertThat(workspaceRepository.count()).isEqualTo(1);
    }

    @Test
    @WithAdminUser
    void workspaceUsersEndpointIsScopedToRequestedWorkspace() {
        User ownerAlpha = persistUser("alpha-users-owner");
        User ownerBeta = persistUser("beta-users-owner");
        User contributor = persistUser("workspace-contributor");

        Workspace workspaceAlpha = createWorkspace(
            "alpha-users",
            "Alpha Users",
            "alpha-users",
            AccountType.ORG,
            ownerAlpha
        );
        Workspace workspaceBeta = createWorkspace("beta-users", "Beta Users", "beta-users", AccountType.ORG, ownerBeta);

        workspaceMembershipService.createMembership(
            workspaceAlpha,
            contributor.getId(),
            WorkspaceMembership.WorkspaceRole.MEMBER
        );

        List<UserTeamsDTO> alphaUsers = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/users", workspaceAlpha.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(UserTeamsDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(alphaUsers)
            .isNotNull()
            .extracting(UserTeamsDTO::login)
            .containsExactlyInAnyOrder(ownerAlpha.getLogin(), contributor.getLogin());

        List<UserTeamsDTO> betaUsers = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/users", workspaceBeta.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(UserTeamsDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(betaUsers).isNotNull().extracting(UserTeamsDTO::login).containsExactly(ownerBeta.getLogin());
    }

    @Test
    @WithAdminUser
    void repositoryAlreadyMonitoredReturnsProblemDetail() {
        User owner = persistUser("repo-conflict-owner");
        Workspace workspace = createWorkspace(
            "repo-conflict",
            "Repo Conflict",
            "repo-conflict",
            AccountType.ORG,
            owner
        );

        RepositoryToMonitor repository = new RepositoryToMonitor();
        repository.setNameWithOwner("acme/test-repo");
        repository.setWorkspace(workspace);
        repository = repositoryToMonitorRepository.save(repository);
        workspace.getRepositoriesToMonitor().add(repository);
        workspaceRepository.save(workspace);

        ProblemDetail problem = webTestClient
            .post()
            .uri(
                "/workspaces/{workspaceSlug}/repositories/{owner}/{name}",
                workspace.getWorkspaceSlug(),
                "acme",
                "test-repo"
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Repository already monitored");
        assertThat(problem.getDetail()).contains("acme/test-repo");
    }

    @Test
    @WithAdminUser
    void patchingStatusOnPurgedWorkspaceReturnsConflictProblemDetail() {
        User owner = persistUser("purged-owner");
        Workspace workspace = createWorkspace("purged-space", "Purged", "purged", AccountType.ORG, owner);
        workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());

        ProblemDetail problem = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceStatusRequestDTO(Workspace.WorkspaceStatus.ACTIVE))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Workspace lifecycle violation");
        assertThat(problem.getDetail()).contains("purged");
    }

    @Test
    @WithAdminUser
    void updateStatusEndpointTransitionsWorkspaceLifecycle() {
        User owner = persistUser("status-owner");
        Workspace workspace = createWorkspace("status-space", "Status", "status", AccountType.ORG, owner);

        WorkspaceDTO suspended = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceStatusRequestDTO(Workspace.WorkspaceStatus.SUSPENDED))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(suspended).isNotNull();
        assertThat(suspended.status()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED.name());

        Workspace suspendedEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(suspendedEntity.getStatus()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED);

        WorkspaceDTO resumed = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/status", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateWorkspaceStatusRequestDTO(Workspace.WorkspaceStatus.ACTIVE))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(resumed).isNotNull();
        assertThat(resumed.status()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE.name());

        Workspace resumedEntity = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(resumedEntity.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
    }

    @Test
    @WithAdminUser
    void invalidWorkspaceSlugPathVariableReturnsConstraintViolationProblemDetail() {
        ProblemDetail problem = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}", "INVALID SLUG")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getProperties().get("errors"))
            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
            .containsKey("workspaceSlug");
    }
}
