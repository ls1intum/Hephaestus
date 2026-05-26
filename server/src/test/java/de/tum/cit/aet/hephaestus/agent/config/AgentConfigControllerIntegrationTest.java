package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.scm.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Agent config controller integration")
class AgentConfigControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private Workspace setupWorkspace() {
        User owner = persistUser("agent-config-owner");
        Workspace workspace = createWorkspace("agent-ws", "Agent Workspace", "agent-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private AgentConfigDTO createConfig(Workspace workspace, String name) {
        var request = new CreateAgentConfigRequestDTO(
            name,
            true,
            "claude-sonnet-4-20250514",
            "sk-test-secret-key-123",
            null,
            LlmProvider.ANTHROPIC,
            300,
            2,
            false,
            null
        );

        return webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();
    }

    @Test
    @WithAdminUser
    void listConfigsReturnsEmptyListWhenNoneExist() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void postCreatesConfigAndGetReturnsIt() {
        Workspace workspace = setupWorkspace();

        AgentConfigDTO created = createConfig(workspace, "my-agent");

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("my-agent");
        assertThat(created.llmProvider()).isEqualTo(LlmProvider.ANTHROPIC);
        assertThat(created.modelName()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(created.hasLlmApiKey()).isTrue();
        assertThat(created.timeoutSeconds()).isEqualTo(300);
        assertThat(created.maxConcurrentJobs()).isEqualTo(2);
        assertThat(created.allowInternet()).isFalse();
        assertThat(created.enabled()).isTrue();

        // GET by ID should return the same config
        AgentConfigDTO fetched = webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo("my-agent");
    }

    @Test
    @WithAdminUser
    void listConfigsReturnsMultiple() {
        Workspace workspace = setupWorkspace();

        createConfig(workspace, "agent-one");
        createConfig(workspace, "agent-two");

        List<AgentConfigDTO> configs = webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AgentConfigDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(configs).hasSize(2);
        assertThat(configs).extracting(AgentConfigDTO::name).containsExactlyInAnyOrder("agent-one", "agent-two");
    }

    @Test
    @WithAdminUser
    void postWithDuplicateNameReturns409() {
        Workspace workspace = setupWorkspace();

        createConfig(workspace, "duplicate-name");

        var duplicateRequest = new CreateAgentConfigRequestDTO(
            "duplicate-name",
            true,
            null,
            null,
            null,
            LlmProvider.OPENAI,
            null,
            null,
            null,
            null
        );

        ProblemDetail problem = webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(duplicateRequest)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getDetail()).contains("duplicate-name");
    }

    @Test
    @WithAdminUser
    void patchUpdatesExistingConfigAndPreservesApiKey() {
        Workspace workspace = setupWorkspace();
        AgentConfigDTO created = createConfig(workspace, "update-test");

        // Update — omit llmApiKey (null = keep existing)
        var updateRequest = new UpdateAgentConfigRequestDTO(
            null,
            "gpt-4o",
            null,
            null,
            LlmProvider.OPENAI,
            120,
            1,
            true,
            null
        );

        AgentConfigDTO updated = webTestClient
            .patch()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.name()).isEqualTo("update-test"); // name unchanged
        assertThat(updated.llmProvider()).isEqualTo(LlmProvider.OPENAI);
        assertThat(updated.modelName()).isEqualTo("gpt-4o");
        assertThat(updated.hasLlmApiKey()).isTrue(); // preserved from create
        assertThat(updated.timeoutSeconds()).isEqualTo(120);
        assertThat(updated.maxConcurrentJobs()).isEqualTo(1);
        assertThat(updated.allowInternet()).isTrue();
    }

    @Test
    @WithAdminUser
    void deleteRemovesConfigAndSubsequentGetReturns404() {
        Workspace workspace = setupWorkspace();
        AgentConfigDTO created = createConfig(workspace, "delete-test");

        // Delete
        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        // GET should return 404
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void deleteWithActiveJobsReturns409() {
        Workspace workspace = setupWorkspace();
        AgentConfigDTO created = createConfig(workspace, "active-jobs-test");

        // Create an active job linked to this config
        AgentConfig config = agentConfigRepository
            .findByIdAndWorkspaceId(created.id(), workspace.getId())
            .orElseThrow();
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(new ObjectMapper().valueToTree(Map.of("agent_type", "CLAUDE_CODE")));
        agentJobRepository.save(job);

        // Delete should fail with 409
        ProblemDetail problem = webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getDetail()).contains("active job(s)");
    }

    @Test
    @WithAdminUser
    void getConfigReturns404ForConfigInDifferentWorkspace() {
        Workspace workspaceA = setupWorkspace();
        AgentConfigDTO configInA = createConfig(workspaceA, "idor-test");

        User ownerB = persistUser("idor-owner-b");
        Workspace workspaceB = createWorkspace("idor-ws-b", "IDOR B", "idor-org-b", AccountType.ORG, ownerB);
        ensureAdminMembership(workspaceB);

        // Try to access workspace A's config via workspace B — should be 404 (IDOR protection)
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspaceB.getWorkspaceSlug(), configInA.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void postWithMissingRequiredFieldsReturns400() {
        Workspace workspace = setupWorkspace();

        // Missing llmProvider (@NotNull)
        var request = Map.of("name", "incomplete-config");

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void postWithBlankNameReturns400() {
        Workspace workspace = setupWorkspace();

        var request = new CreateAgentConfigRequestDTO(
            "",
            true,
            null,
            null,
            null,
            LlmProvider.ANTHROPIC,
            null,
            null,
            null,
            null
        );

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void postWithTimeoutOutOfRangeReturns400() {
        Workspace workspace = setupWorkspace();

        var request = new CreateAgentConfigRequestDTO(
            "bad-timeout",
            true,
            null,
            null,
            null,
            LlmProvider.ANTHROPIC,
            5, // below minimum of 30
            null,
            null,
            null
        );

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void postReturnsLocationHeader() {
        Workspace workspace = setupWorkspace();

        var request = new CreateAgentConfigRequestDTO(
            "location-test",
            true,
            null,
            null,
            null,
            LlmProvider.ANTHROPIC,
            null,
            null,
            null,
            null
        );

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .exists("Location");
    }

    @Test
    void getConfigsRequiresAuthentication() {
        User owner = persistUser("unauth-owner");
        Workspace workspace = createWorkspace("unauth-ws", "Unauth", "unauth", AccountType.ORG, owner);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithAdminUser
    void apiKeyNeverExposedInResponse() {
        Workspace workspace = setupWorkspace();

        var request = new CreateAgentConfigRequestDTO(
            "secret-test",
            true,
            null,
            "sk-super-secret-key",
            null,
            LlmProvider.ANTHROPIC,
            null,
            null,
            null,
            null
        );

        String responseBody = webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
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
        assertThat(responseBody).doesNotContain("sk-super-secret-key");
        assertThat(responseBody).doesNotContain("llmApiKey");
        assertThat(responseBody).contains("hasLlmApiKey");
    }
}
