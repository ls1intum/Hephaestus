package de.tum.in.www1.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Agent job controller integration")
class AgentJobControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Workspace setupWorkspace() {
        User owner = persistUser("job-owner");
        Workspace workspace = createWorkspace("job-ws", "Job Workspace", "job-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private AgentConfig createConfig(Workspace workspace, String name) {
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName(name);
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        return agentConfigRepository.save(config);
    }

    private AgentJob createJob(Workspace workspace, AgentJobStatus status) {
        return createJob(workspace, status, null);
    }

    private AgentJob createJob(Workspace workspace, AgentJobStatus status, AgentConfig config) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(status);
        job.setConfigSnapshot(
            OBJECT_MAPPER.valueToTree(Map.of("agent_type", "CLAUDE_CODE", "model", "claude-sonnet-4-20250514"))
        );
        return agentJobRepository.save(job);
    }

    @Test
    @WithAdminUser
    void listJobsReturnsEmptyPageWhenNoJobs() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(0)
            .jsonPath("$.content")
            .isArray()
            .jsonPath("$.content.length()")
            .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    void listJobsReturnsPaginatedResults() {
        Workspace workspace = setupWorkspace();

        createJob(workspace, AgentJobStatus.COMPLETED);
        createJob(workspace, AgentJobStatus.RUNNING);
        createJob(workspace, AgentJobStatus.QUEUED);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs?size=2", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(3)
            .jsonPath("$.content.length()")
            .isEqualTo(2)
            .jsonPath("$.totalPages")
            .isEqualTo(2);
    }

    @Test
    @WithAdminUser
    void listJobsFiltersByStatus() {
        Workspace workspace = setupWorkspace();

        createJob(workspace, AgentJobStatus.COMPLETED);
        createJob(workspace, AgentJobStatus.RUNNING);
        createJob(workspace, AgentJobStatus.QUEUED);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs?status=RUNNING", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1)
            .jsonPath("$.content[0].status")
            .isEqualTo("RUNNING");
    }

    @Test
    @WithAdminUser
    void listJobsFiltersByConfigId() {
        Workspace workspace = setupWorkspace();
        AgentConfig configA = createConfig(workspace, "config-a");
        AgentConfig configB = createConfig(workspace, "config-b");

        createJob(workspace, AgentJobStatus.COMPLETED, configA);
        createJob(workspace, AgentJobStatus.RUNNING, configA);
        createJob(workspace, AgentJobStatus.QUEUED, configB);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs?configId={configId}", workspace.getWorkspaceSlug(), configA.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(2);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs?configId={configId}", workspace.getWorkspaceSlug(), configB.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalElements")
            .isEqualTo(1);
    }

    @Test
    @WithAdminUser
    void getJobReturnsJobDetail() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.COMPLETED);

        AgentJobDTO result = webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs/{id}", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentJobDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(job.getId());
        assertThat(result.status()).isEqualTo(AgentJobStatus.COMPLETED);
        assertThat(result.jobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
    }

    @Test
    @WithAdminUser
    void getJobReturns404ForNonExistentId() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs/{id}", workspace.getWorkspaceSlug(), UUID.randomUUID())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void getJobReturns404ForJobInDifferentWorkspace() {
        Workspace workspaceA = setupWorkspace();

        User ownerB = persistUser("job-owner-b");
        Workspace workspaceB = createWorkspace("job-ws-b", "Job B", "job-org-b", AccountType.ORG, ownerB);
        ensureAdminMembership(workspaceB);

        AgentJob jobInA = createJob(workspaceA, AgentJobStatus.COMPLETED);

        // Try to access workspace A's job via workspace B — should be 404 (IDOR protection)
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs/{id}", workspaceB.getWorkspaceSlug(), jobInA.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void jobTokenNeverExposedInResponse() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.QUEUED);

        String responseBody = webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs/{id}", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody).doesNotContain("jobToken");
        assertThat(responseBody).doesNotContain("job_token");
        // Also check the actual token value isn't leaked
        AgentJob freshJob = agentJobRepository.findById(job.getId()).orElseThrow();
        if (freshJob.getJobToken() != null) {
            assertThat(responseBody).doesNotContain(freshJob.getJobToken());
        }
    }

    @Test
    void listJobsRequiresAuthentication() {
        User owner = persistUser("unauth-job-owner");
        Workspace workspace = createWorkspace("unauth-job-ws", "Unauth", "unauth-job", AccountType.ORG, owner);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-jobs", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
