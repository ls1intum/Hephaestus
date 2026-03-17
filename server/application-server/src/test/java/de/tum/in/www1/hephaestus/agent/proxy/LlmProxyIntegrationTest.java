package de.tum.in.www1.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("LLM proxy integration")
class LlmProxyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("proxy-owner");
        workspace = createWorkspace("proxy-ws", "Proxy Workspace", "proxy-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
    }

    private AgentJob createRunningJobWithApiKey(String apiKey) {
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("test-config-" + System.nanoTime());
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config = agentConfigRepository.save(config);

        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setLlmApiKey(apiKey);
        job.setConfigSnapshot(
            OBJECT_MAPPER.valueToTree(Map.of("agent_type", "CLAUDE_CODE", "model", "claude-sonnet-4-20250514"))
        );
        return agentJobRepository.save(job);
    }

    @Nested
    @DisplayName("Security isolation")
    class SecurityIsolation {

        @Test
        @DisplayName("should reject request without any auth")
        void shouldRejectNoAuth() {
            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject JWT token on internal endpoints")
        @WithAdminUser
        void shouldRejectJwtOnInternalEndpoints() {
            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .headers(TestAuthUtils.withCurrentUser())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject expired/non-RUNNING job token")
        void shouldRejectNonRunningJobToken() {
            AgentConfig config = new AgentConfig();
            config.setWorkspace(workspace);
            config.setName("completed-config");
            config.setAgentType(AgentType.CLAUDE_CODE);
            config.setLlmProvider(LlmProvider.ANTHROPIC);
            config = agentConfigRepository.save(config);

            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setConfig(config);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setStatus(AgentJobStatus.COMPLETED);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("agent_type", "CLAUDE_CODE")));
            job = agentJobRepository.save(job);

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject QUEUED job token (not yet RUNNING)")
        void shouldRejectQueuedJobToken() {
            AgentConfig config = new AgentConfig();
            config.setWorkspace(workspace);
            config.setName("queued-config");
            config.setAgentType(AgentType.CLAUDE_CODE);
            config.setLlmProvider(LlmProvider.ANTHROPIC);
            config = agentConfigRepository.save(config);

            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setConfig(config);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setStatus(AgentJobStatus.QUEUED);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("agent_type", "CLAUDE_CODE")));
            job = agentJobRepository.save(job);

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject FAILED job token")
        void shouldRejectFailedJobToken() {
            AgentConfig config = new AgentConfig();
            config.setWorkspace(workspace);
            config.setName("failed-config");
            config.setAgentType(AgentType.CLAUDE_CODE);
            config.setLlmProvider(LlmProvider.ANTHROPIC);
            config = agentConfigRepository.save(config);

            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setConfig(config);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setStatus(AgentJobStatus.FAILED);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("agent_type", "CLAUDE_CODE")));
            job = agentJobRepository.save(job);

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject fabricated token not in database")
        void shouldRejectFabricatedToken() {
            // A random Base64-URL token that doesn't match any job
            String fakeToken = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY";

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", fakeToken)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject empty x-api-key header")
        void shouldRejectEmptyApiKeyHeader() {
            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", "")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @DisplayName("should reject malformed token (non-Base64-URL characters)")
        void shouldRejectMalformedToken() {
            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", "invalid token with spaces!!!")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Token validation and proxy forwarding")
    class ProxyForwarding {

        @Test
        @DisplayName("should accept valid RUNNING job token and forward to upstream")
        void shouldAcceptValidTokenAndProxy() {
            AgentJob job = createRunningJobWithApiKey("sk-test-key");

            var result = webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue(
                    "{\"model\":\"claude-sonnet-4-20250514\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":1}"
                )
                .exchange();

            result
                .expectStatus()
                .value(status -> {
                    assertThat(status)
                        .as("Proxy should authenticate the job token and forward (not return 401 or 403)")
                        .isNotIn(401, 403);
                    assertThat(status)
                        .as("Should be a forwarded upstream response or connection error")
                        .isIn(200, 400, 429, 500, 502);
                });
        }

        @Test
        @DisplayName("should return 404 for unknown provider")
        void shouldReturn404ForUnknownProvider() {
            AgentJob job = createRunningJobWithApiKey("sk-test-key");

            webTestClient
                .post()
                .uri("/internal/llm/unknownprovider/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @DisplayName("should authenticate via Bearer token for OpenAI endpoint")
        void shouldAuthenticateViaBearerForOpenAI() {
            AgentJob job = createRunningJobWithApiKey("sk-openai-test");

            var result = webTestClient
                .post()
                .uri("/internal/llm/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange();

            result
                .expectStatus()
                .value(status -> {
                    assertThat(status)
                        .as("Proxy should authenticate the Bearer job token (not return 401 or 403)")
                        .isNotIn(401, 403);
                    assertThat(status)
                        .as("Should be a forwarded upstream response or connection error")
                        .isIn(200, 400, 429, 500, 502);
                });
        }

        @Test
        @DisplayName("should authenticate via api-key header for Azure endpoint")
        void shouldAuthenticateViaApiKeyForAzure() {
            AgentJob job = createRunningJobWithApiKey("sk-azure-test");

            var result = webTestClient
                .post()
                .uri("/internal/llm/openai/v1/chat/completions")
                .header("api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange();

            result
                .expectStatus()
                .value(status -> {
                    assertThat(status)
                        .as("Proxy should authenticate the api-key job token (not return 401 or 403)")
                        .isNotIn(401, 403);
                    assertThat(status)
                        .as("Should be a forwarded upstream response or connection error")
                        .isIn(200, 400, 429, 500, 502);
                });
        }

        @Test
        @DisplayName("should support GET method (for model listing)")
        void shouldSupportGetMethod() {
            AgentJob job = createRunningJobWithApiKey("sk-test-key");

            var result = webTestClient
                .get()
                .uri("/internal/llm/openai/v1/models")
                .header("Authorization", "Bearer " + job.getJobToken())
                .exchange();

            result
                .expectStatus()
                .value(status -> {
                    assertThat(status).as("GET requests should pass authentication").isNotIn(401, 403);
                });
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should reject path traversal attempts")
        void shouldRejectPathTraversal() {
            AgentJob job = createRunningJobWithApiKey("sk-test-key");

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/../../../admin")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .value(status ->
                    assertThat(status)
                        .as(
                            "Path traversal should be rejected: 400 (our check), 404 (not found), or 401 (servlet normalizes path out of /internal/llm/** to main auth chain)"
                        )
                        .isIn(400, 401, 404)
                );
        }
    }

    @Nested
    @DisplayName("Job token hash integrity")
    class TokenHashIntegrity {

        @Test
        @DisplayName("should generate and persist job_token_hash on save")
        void shouldPersistTokenHash() {
            AgentJob job = createRunningJobWithApiKey("sk-test");

            AgentJob loaded = agentJobRepository.findById(job.getId()).orElseThrow();
            assertThat(loaded.getJobTokenHash()).isNotNull();
            assertThat(loaded.getJobTokenHash()).hasSize(64); // SHA-256 hex
            assertThat(loaded.getJobTokenHash()).isEqualTo(AgentJob.computeTokenHash(loaded.getJobToken()));
        }

        @Test
        @DisplayName("should look up job by token hash")
        void shouldLookUpByTokenHash() {
            AgentJob job = createRunningJobWithApiKey("sk-test");

            String hash = AgentJob.computeTokenHash(job.getJobToken());
            var found = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.RUNNING);
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(job.getId());
        }

        @Test
        @DisplayName("should not find job with wrong status via hash lookup")
        void shouldNotFindJobWithWrongStatus() {
            AgentJob job = createRunningJobWithApiKey("sk-test");

            String hash = AgentJob.computeTokenHash(job.getJobToken());
            var found = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.COMPLETED);
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("job token should be unique across jobs")
        void jobTokenShouldBeUnique() {
            AgentJob job1 = createRunningJobWithApiKey("sk-test-1");
            AgentJob job2 = createRunningJobWithApiKey("sk-test-2");

            assertThat(job1.getJobToken()).isNotEqualTo(job2.getJobToken());
            assertThat(job1.getJobTokenHash()).isNotEqualTo(job2.getJobTokenHash());
        }

        @Test
        @DisplayName("job token should be 43 chars (256-bit Base64-URL no padding)")
        void jobTokenShouldBe43Chars() {
            AgentJob job = createRunningJobWithApiKey("sk-test");

            assertThat(job.getJobToken()).hasSize(43);
            assertThat(job.getJobToken()).matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("computeTokenHash should be deterministic")
        void computeTokenHashShouldBeDeterministic() {
            String token = "test-token-abc";
            String hash1 = AgentJob.computeTokenHash(token);
            String hash2 = AgentJob.computeTokenHash(token);

            assertThat(hash1).isEqualTo(hash2);
            assertThat(hash1).hasSize(64);
            assertThat(hash1).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("Cross-chain security — LLM proxy endpoints vs main API")
    class CrossChainSecurity {

        @Test
        @DisplayName("job token should not grant access to main API endpoints")
        @WithAdminUser
        void jobTokenShouldNotAccessMainApi() {
            AgentJob job = createRunningJobWithApiKey("sk-test");

            // Job token should NOT work for workspace endpoints (different security chain)
            webTestClient
                .get()
                .uri("/workspaces/" + workspace.getWorkspaceSlug() + "/admin/members")
                .header("x-api-key", job.getJobToken())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }
}
