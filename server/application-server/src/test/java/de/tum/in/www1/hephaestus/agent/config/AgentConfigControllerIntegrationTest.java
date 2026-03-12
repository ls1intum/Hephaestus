package de.tum.in.www1.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
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

    @Test
    @WithAdminUser
    void getConfigReturns404WhenNoneExists() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void putCreatesConfigAndGetReturnsIt() {
        Workspace workspace = setupWorkspace();

        var request = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.CLAUDE_CODE,
            "claude-sonnet-4-20250514",
            "sk-test-secret-key-123",
            LlmProvider.ANTHROPIC,
            300,
            2,
            false
        );

        AgentConfigDTO created = webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(created.llmProvider()).isEqualTo(LlmProvider.ANTHROPIC);
        assertThat(created.modelName()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(created.hasLlmApiKey()).isTrue();
        assertThat(created.timeoutSeconds()).isEqualTo(300);
        assertThat(created.maxConcurrentJobs()).isEqualTo(2);
        assertThat(created.allowInternet()).isFalse();
        assertThat(created.enabled()).isTrue();

        // GET should return the same config
        AgentConfigDTO fetched = webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.agentType()).isEqualTo(AgentType.CLAUDE_CODE);
    }

    @Test
    @WithAdminUser
    void putUpdatesExistingConfigAndPreservesApiKey() {
        Workspace workspace = setupWorkspace();

        // Create initial config
        var createRequest = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.CLAUDE_CODE,
            "claude-sonnet-4-20250514",
            "sk-original-secret",
            LlmProvider.ANTHROPIC,
            600,
            3,
            false
        );

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus()
            .isOk();

        // Update — omit llmApiKey (null = keep existing)
        var updateRequest = new UpdateAgentConfigRequestDTO(
            null,
            AgentType.OPENCODE,
            "gpt-4o",
            null,
            LlmProvider.OPENAI,
            120,
            1,
            true
        );

        AgentConfigDTO updated = webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
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
        assertThat(updated.agentType()).isEqualTo(AgentType.OPENCODE);
        assertThat(updated.llmProvider()).isEqualTo(LlmProvider.OPENAI);
        assertThat(updated.modelName()).isEqualTo("gpt-4o");
        assertThat(updated.hasLlmApiKey()).isTrue(); // preserved from create
        assertThat(updated.timeoutSeconds()).isEqualTo(120);
        assertThat(updated.maxConcurrentJobs()).isEqualTo(1);
        assertThat(updated.allowInternet()).isTrue();
    }

    @Test
    @WithAdminUser
    void putWithInvalidProviderReturns400() {
        Workspace workspace = setupWorkspace();

        var request = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.CLAUDE_CODE,
            null,
            null,
            LlmProvider.OPENAI,
            null,
            null,
            null
        );

        ProblemDetail problem = webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
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
        assertThat(problem.getDetail()).contains("CLAUDE_CODE");
        assertThat(problem.getDetail()).contains("ANTHROPIC");
    }

    @Test
    @WithAdminUser
    void deleteRemovesConfigAndSubsequentGetReturns404() {
        Workspace workspace = setupWorkspace();

        // Create config first
        var request = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.OPENCODE,
            null,
            "sk-key",
            LlmProvider.OPENAI,
            null,
            null,
            null
        );

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

        // Delete
        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        // GET should return 404
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void deleteWithActiveJobsReturns409() {
        Workspace workspace = setupWorkspace();

        // Create config
        var request = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.CLAUDE_CODE,
            null,
            "sk-key",
            LlmProvider.ANTHROPIC,
            null,
            null,
            null
        );

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

        // Create an active job linked to this config
        AgentConfig config = agentConfigRepository.findByWorkspaceId(workspace.getId()).orElseThrow();
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
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
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
    void getConfigRequiresAuthentication() {
        User owner = persistUser("unauth-owner");
        Workspace workspace = createWorkspace("unauth-ws", "Unauth", "unauth", AccountType.ORG, owner);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithAdminUser
    void apiKeyNeverExposedInResponse() {
        Workspace workspace = setupWorkspace();

        var request = new UpdateAgentConfigRequestDTO(
            true,
            AgentType.CLAUDE_CODE,
            null,
            "sk-super-secret-key",
            LlmProvider.ANTHROPIC,
            null,
            null,
            null
        );

        String responseBody = webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody).doesNotContain("sk-super-secret-key");
        assertThat(responseBody).doesNotContain("llmApiKey");
        assertThat(responseBody).contains("hasLlmApiKey");
    }
}
