package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceListItemDTO;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
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
    @WithMentorUser(gitlabId = 18024L)
    void createGitLabWorkspacePersistsCorrectProviderModeAndServerUrl() {
        User owner = persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-space",
            "My GitLab Workspace",
            "my-group/my-project",
            AccountType.ORG,
            Workspace.GitProviderMode.GITLAB_PAT,
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
        assertThat(workspace.gitProviderMode()).isEqualTo("GITLAB_PAT");
        assertThat(workspace.providerType()).isEqualTo(GitProviderType.GITLAB);
        assertThat(workspace.serverUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(workspace.status()).isEqualTo("ACTIVE");
        assertThat(workspace.hasPersonalAccessToken()).isTrue();

        // Verify persisted entity
        Workspace persisted = workspaceRepository.findById(workspace.id()).orElseThrow();
        assertThat(persisted.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITLAB_PAT);
        assertThat(persisted.getServerUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(persisted.getPersonalAccessToken()).isNotNull();
        assertThat(persisted.getPersonalAccessToken()).isNotBlank();
    }

    @Test
    @WithMentorUser(gitlabId = 18024L)
    void createGitLabWorkspaceWithDefaultServerUrlOmitsServerUrl() {
        User owner = persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-default",
            "Default GitLab",
            "my-group",
            AccountType.ORG,
            Workspace.GitProviderMode.GITLAB_PAT,
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
            Workspace.GitProviderMode.GITLAB_PAT,
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
            .containsKey("tokenProvidedForGitLab");

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
            Workspace.GitProviderMode.GITLAB_PAT,
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
    @WithMentorUser(gitlabId = 18024L)
    void createGitLabWorkspaceAssignsOwnerMembership() {
        persistUser("mentor");

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-ownership",
            "Owner Test",
            "owner-group",
            AccountType.ORG,
            Workspace.GitProviderMode.GITLAB_PAT,
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

        User gitLabOwner = userRepository
            .findAllByProviderTypeAndNativeId(GitProviderType.GITLAB, 18024L)
            .stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("GitLab owner user not created"));

        var membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.id(), gitLabOwner.getId())
            .orElseThrow(() -> new AssertionError("Owner membership not created for linked GitLab user"));
        assertThat(membership.getRole()).isEqualTo(WorkspaceMembership.WorkspaceRole.OWNER);
    }

    @Test
    @WithMentorUser(gitlabId = 18024L)
    void createGitLabWorkspaceResponseNeverContainsRawToken() {
        User owner = persistUser("mentor");
        String secretToken = "test-token-placeholder";

        var request = new CreateWorkspaceRequestDTO(
            "gitlab-secret",
            "Secret Test",
            "secret-group",
            AccountType.ORG,
            Workspace.GitProviderMode.GITLAB_PAT,
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
    @WithMentorUser(gitlabId = 18024L)
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
            Workspace.GitProviderMode.GITLAB_PAT,
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
        assertThat(workspaces)
            .extracting(WorkspaceListItemDTO::providerType)
            .contains(GitProviderType.GITHUB, GitProviderType.GITLAB);
    }

    @Test
    @WithAdminUser
    void gitLabWorkspaceLifecycleSuspendAndPurgeWorkCorrectly() {
        User owner = persistUser("lifecycle-owner");
        Workspace workspace = createWorkspace(
            "gitlab-lifecycle",
            "Lifecycle Test",
            "lifecycle-group",
            AccountType.ORG,
            owner
        );
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        workspace.setPersonalAccessToken("glpat-lifecycle-token");
        workspace = workspaceRepository.save(workspace);
        ensureOwnerMembership(workspace);

        // Verify it's ACTIVE and GITLAB
        assertThat(workspace.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITLAB_PAT);
        assertThat(workspace.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);

        // Suspend
        workspaceLifecycleService.suspendWorkspace(workspace.getWorkspaceSlug());
        Workspace suspended = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED);
        // GitLab mode preserved after suspend
        assertThat(suspended.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITLAB_PAT);

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
