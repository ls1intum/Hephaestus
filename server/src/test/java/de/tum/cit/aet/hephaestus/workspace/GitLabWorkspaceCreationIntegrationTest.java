package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceListItemDTO;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("GitLab workspace creation integration")
@org.springframework.test.context.TestPropertySource(
    properties = "hephaestus.features.flags.gitlab-workspace-creation=true"
)
class GitLabWorkspaceCreationIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Test
    @WithMentorUser
    void createGitLabWorkspacePersistsCorrectProviderModeAndServerUrl() {
        User owner = persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-space",
            "My GitLab Workspace",
            "my-group/my-project",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            "glpat-test-token-12345",
            "https://gitlab.example.com"
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

        WorkspaceDTO workspace = Objects.requireNonNull(created);
        assertThat(workspace.workspaceSlug()).isEqualTo("gitlab-space");
        // provider classification is derived from the active Connection
        // and surfaced via `kind` (the renamed field). The legacy gitProviderMode field
        // is gone.
        assertThat(workspace.kind()).isEqualTo("GITLAB");
        assertThat(workspace.providerType()).isEqualTo(GitProviderType.GITLAB);
        assertThat(workspace.serverUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(workspace.status()).isEqualTo("ACTIVE");
        assertThat(workspace.hasPersonalAccessToken()).isTrue();

        // Verify persisted entity exists; provider mode / PAT / server URL now live on
        // the workspace's GitLab Connection row rather than legacy Workspace columns,
        // which the WorkspaceDTO assertions above already cover end-to-end.
        Workspace persisted = workspaceRepository.findById(workspace.id()).orElseThrow();
        assertThat(persisted.getAccountLogin()).isEqualTo("my-group/my-project");
    }

    @Test
    @WithMentorUser
    void createGitLabWorkspaceWithDefaultServerUrlOmitsServerUrl() {
        User owner = persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-default",
            "Default GitLab",
            "my-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            "glpat-test-token-67890",
            null
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

        WorkspaceDTO workspace = Objects.requireNonNull(created);
        assertThat(workspace.providerType()).isEqualTo(GitProviderType.GITLAB);
        assertThat(workspace.serverUrl()).isNull();
    }

    @Test
    @WithAdminUser
    void createGitLabWorkspaceWithoutTokenReturnsValidationError() {
        User owner = persistUser("admin");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-notoken",
            "No Token",
            "my-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            null, // missing token
            null
        );

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
            .containsKey("tokenProvided");

        assertThat(workspaceRepository.findByWorkspaceSlug("gitlab-notoken")).isEmpty();
    }

    @Test
    @WithAdminUser
    void createGitLabWorkspaceWithHttpServerUrlReturnsValidationError() {
        User owner = persistUser("admin");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-http",
            "HTTP GitLab",
            "my-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            "glpat-test-token",
            "http://insecure.example.com" // not HTTPS
        );

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
            .containsKey("serverUrlSafe");

        assertThat(workspaceRepository.findByWorkspaceSlug("gitlab-http")).isEmpty();
    }

    @Test
    @WithMentorUser
    void createGitLabWorkspaceAssignsOwnerMembership() {
        User owner = persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-ownership",
            "Owner Test",
            "owner-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            "glpat-owner-token",
            null
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

        WorkspaceDTO workspace = Objects.requireNonNull(created);

        var membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.id(), owner.getId())
            .orElseThrow(() -> new AssertionError("Owner membership not created"));
        assertThat(membership.getRole()).isEqualTo(WorkspaceMembership.WorkspaceRole.OWNER);
    }

    @Test
    @WithMentorUser
    void createGitLabWorkspaceResponseNeverContainsRawToken() {
        User owner = persistUser("mentor");
        String secretToken = "test-token-placeholder";

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-secret",
            "Secret Test",
            "secret-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            secretToken,
            null
        );

        String responseBody = webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody).doesNotContain(secretToken);
        assertThat(responseBody).contains("\"hasPersonalAccessToken\":true");
    }

    @Test
    @WithMentorUser
    void gitLabWorkspaceAppearsInListWithCorrectProviderType() {
        User owner = persistUser("mentor");

        // Create a GitHub workspace
        Workspace githubWorkspace = createWorkspace("github-ws", "GitHub WS", "github-org", AccountType.ORG, owner);
        ensureAdminMembership(githubWorkspace);

        // Create a GitLab workspace
        var gitlabRequest = new CreateWorkspaceRequestDTO(
            "gitlab-ws",
            "GitLab WS",
            "gitlab-group",
            AccountType.ORG,
            owner.getId(),
            de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
            "glpat-list-token",
            null
        );

        webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(gitlabRequest)
            .exchange()
            .expectStatus()
            .isCreated();

        // Ensure admin membership for listing
        Workspace gitlabPersisted = workspaceRepository.findByWorkspaceSlug("gitlab-ws").orElseThrow();
        ensureAdminMembership(gitlabPersisted);

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
        // providerType is now derived from the active Connection. The
        // GitHub workspace created via the 5-arg path has no Connection (App
        // installations come in via GithubLifecycleListener), so its providerType is
        // null. The GitLab workspace created via the REST API path provisions a GitLab
        // Connection inline and surfaces as GITLAB.
        assertThat(workspaces).extracting(WorkspaceListItemDTO::providerType).contains(GitProviderType.GITLAB);
    }

    @Test
    @WithAdminUser
    void gitLabWorkspaceLifecycleSuspendAndPurgeWorkCorrectly() {
        User owner = persistUser("lifecycle-owner");
        Workspace workspace = workspaceService.createWorkspace(
            new CreateWorkspaceRequestDTO(
                "gitlab-lifecycle",
                "Lifecycle Test",
                "lifecycle-group",
                AccountType.ORG,
                owner.getId(),
                de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind.GITLAB,
                "glpat-lifecycle-token",
                null
            )
        );
        ensureOwnerMembership(workspace);

        // Verify it's ACTIVE
        assertThat(workspace.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);

        // Suspend
        workspaceLifecycleService.suspendWorkspace(workspace.getWorkspaceSlug());
        Workspace suspended = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED);

        // Resume
        workspaceLifecycleService.resumeWorkspace(workspace.getWorkspaceSlug());
        Workspace resumed = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(resumed.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);

        // Purge
        workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
        Workspace purged = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);
    }
}
