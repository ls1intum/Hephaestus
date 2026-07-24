package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class AgentJobControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository agentJobRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Workspace setupWorkspace() {
        User owner = persistUser("job-owner");
        Workspace workspace = createWorkspace("job-ws", "Job Workspace", "job-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private AgentJob createJob(Workspace workspace, AgentJobStatus status) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(status);
        // Include upstreamModelId so the DTO's snapshotString extractor (AgentJobDTO.from) is exercised
        // on its present-value branch end-to-end, not just the absent-field null path.
        job.setConfigSnapshot(
            OBJECT_MAPPER.valueToTree(
                Map.of(
                    "agent_type",
                    "CLAUDE_CODE",
                    "model",
                    "claude-sonnet-4-20250514",
                    "upstreamModelId",
                    "gpt-5.4-mini"
                )
            )
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
        // The model is projected out of the JSONB configSnapshot by AgentJobDTO.from.
        assertThat(result.model()).isEqualTo("gpt-5.4-mini");
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
    @WithAdminUser
    void cancelQueuedJobReturns200() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.QUEUED);

        AgentJobDTO result = webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentJobDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AgentJobStatus.CANCELLED);
    }

    @Test
    @WithAdminUser
    void cancelRunningJobReturns200() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.RUNNING);

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("CANCELLED");
    }

    @Test
    @WithAdminUser
    void cancelCompletedJobReturns409() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.COMPLETED);

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(409);
    }

    @Test
    @WithAdminUser
    void cancelNonExistentJobReturns404() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), UUID.randomUUID())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void cancelJobInDifferentWorkspaceReturns404() {
        Workspace workspaceA = setupWorkspace();

        User ownerB = persistUser("cancel-owner-b");
        Workspace workspaceB = createWorkspace("cancel-ws-b", "Cancel B", "cancel-org-b", AccountType.ORG, ownerB);
        ensureAdminMembership(workspaceB);

        AgentJob jobInA = createJob(workspaceA, AgentJobStatus.QUEUED);

        // IDOR protection: cancel workspace A's job via workspace B → 404
        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspaceB.getWorkspaceSlug(), jobInA.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void cancelAlreadyCancelledJobIsIdempotent() {
        Workspace workspace = setupWorkspace();
        AgentJob job = createJob(workspace, AgentJobStatus.CANCELLED);

        AgentJobDTO result = webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), job.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AgentJobDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(AgentJobStatus.CANCELLED);
    }

    @Test
    void cancelJobRequiresAuthentication() {
        User owner = persistUser("unauth-cancel-owner");
        Workspace workspace = createWorkspace("unauth-cancel-ws", "Unauth", "unauth-cancel", AccountType.ORG, owner);

        // Pass CSRF (cookie-style write) but send no authentication, so the auth layer — not the CSRF
        // filter — answers: an unauthenticated caller gets 401 (ADR 0017; see CsrfProtectionIntegrationTest
        // for the tokenless-write 403 case).
        String csrf = TestAuthUtils.fetchCsrfToken(webTestClient);
        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-jobs/{id}/cancel", workspace.getWorkspaceSlug(), UUID.randomUUID())
            .headers(TestAuthUtils.withCsrf(csrf))
            .exchange()
            .expectStatus()
            .isUnauthorized();
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
