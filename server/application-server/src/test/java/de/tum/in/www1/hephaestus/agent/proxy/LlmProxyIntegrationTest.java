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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("LLM proxy integration")
class LlmProxyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static MockWebServer mockUpstream;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Workspace workspace;

    @BeforeAll
    static void startMockUpstream() throws IOException {
        mockUpstream = new MockWebServer();
        mockUpstream.start();
    }

    @AfterAll
    static void stopMockUpstream() throws IOException {
        mockUpstream.shutdown();
    }

    @DynamicPropertySource
    static void configureLlmProxy(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.llm-proxy.anthropic-upstream-url", () ->
            mockUpstream.url("/").toString().replaceAll("/$", "")
        );
        registry.add("hephaestus.llm-proxy.openai-upstream-url", () ->
            mockUpstream.url("/").toString().replaceAll("/$", "")
        );
    }

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

    /** Drain any queued requests from prior tests so takeRequest() is accurate. */
    private void drainMockUpstream() {
        try {
            while (mockUpstream.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
                // keep draining until queue is empty (returns null)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    @DisplayName("Proxy forwarding with mock upstream")
    class ProxyForwarding {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        @DisplayName("should forward Anthropic request with real API key and correct path")
        void shouldForwardAnthropicWithRealApiKey() throws Exception {
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"msg_test\",\"content\":[]}")
            );

            AgentJob job = createRunningJobWithApiKey("sk-ant-real-key-123");

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":1}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"id\":\"msg_test\",\"content\":[]}");

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getPath()).isEqualTo("/v1/messages");
            // Real API key injected — NOT the job token
            assertThat(upstream.getHeader("x-api-key")).isEqualTo("sk-ant-real-key-123");
            assertThat(upstream.getHeader("x-api-key")).isNotEqualTo(job.getJobToken());
            // No Bearer auth for Anthropic
            assertThat(upstream.getHeader("Authorization")).isNull();
            // Body forwarded intact
            assertThat(upstream.getBody().readUtf8()).contains("claude-sonnet-4-20250514");
        }

        @Test
        @DisplayName("should forward OpenAI request with Bearer auth and correct path")
        void shouldForwardOpenAIWithBearerAuth() throws Exception {
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl-test\",\"choices\":[]}")
            );

            AgentJob job = createRunningJobWithApiKey("sk-openai-real-key-456");

            webTestClient
                .post()
                .uri("/internal/llm/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"id\":\"chatcmpl-test\",\"choices\":[]}");

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getPath()).isEqualTo("/v1/chat/completions");
            // Real API key injected with Bearer prefix
            assertThat(upstream.getHeader("Authorization")).isEqualTo("Bearer sk-openai-real-key-456");
            // Job token must NOT appear in upstream headers
            assertThat(upstream.getHeader("Authorization")).doesNotContain(job.getJobToken());
            // No x-api-key for OpenAI
            assertThat(upstream.getHeader("x-api-key")).isNull();
        }

        @Test
        @DisplayName("should authenticate via api-key header (Azure-style) for OpenAI endpoint")
        void shouldAuthenticateViaApiKeyForAzure() throws Exception {
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl-azure\"}")
            );

            AgentJob job = createRunningJobWithApiKey("sk-azure-real-key");

            webTestClient
                .post()
                .uri("/internal/llm/openai/v1/chat/completions")
                .header("api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"gpt-4\"}")
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            // Real API key injected, NOT the job token
            assertThat(upstream.getHeader("Authorization")).isEqualTo("Bearer sk-azure-real-key");
            assertThat(upstream.getHeader("api-key")).isNull();
        }

        @Test
        @DisplayName("should support GET method (for model listing)")
        void shouldSupportGetMethod() throws Exception {
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":[{\"id\":\"gpt-4\"}]}")
            );

            AgentJob job = createRunningJobWithApiKey("sk-list-key");

            webTestClient
                .get()
                .uri("/internal/llm/openai/v1/models")
                .header("Authorization", "Bearer " + job.getJobToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"data\":[{\"id\":\"gpt-4\"}]}");

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getMethod()).isEqualTo("GET");
            assertThat(upstream.getPath()).isEqualTo("/v1/models");
            assertThat(upstream.getHeader("Authorization")).isEqualTo("Bearer sk-list-key");
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
        @DisplayName("should stream SSE responses end-to-end")
        void shouldStreamSseResponse() throws Exception {
            String ssePayload = "data: {\"id\":\"msg_stream\"}\n\ndata: [DONE]\n\n";
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(ssePayload)
            );

            AgentJob job = createRunningJobWithApiKey("sk-ant-sse-key");

            // SSE body content is verified by ProxyStreamingUtilsTest.
            // WebTestClient cannot capture bytes written directly to HttpServletResponse
            // (the controller bypasses Spring MVC for SSE streaming and returns null),
            // so we verify status, content-type, and upstream auth header injection here.
            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":1,\"stream\":true}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/event-stream");

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getHeader("x-api-key")).isEqualTo("sk-ant-sse-key");
        }

        @Test
        @DisplayName("should forward query parameters to upstream")
        void shouldForwardQueryParameters() throws Exception {
            mockUpstream.enqueue(
                new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":[]}")
            );

            AgentJob job = createRunningJobWithApiKey("sk-query-key");

            webTestClient
                .get()
                .uri("/internal/llm/openai/v1/models?limit=10&order=desc")
                .header("Authorization", "Bearer " + job.getJobToken())
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getPath()).contains("limit=10");
            assertThat(upstream.getPath()).contains("order=desc");
        }

        @Test
        @DisplayName("should forward upstream error status codes transparently")
        void shouldForwardUpstreamErrors() throws Exception {
            mockUpstream.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate_limited\"}"));

            AgentJob job = createRunningJobWithApiKey("sk-test-key");

            webTestClient
                .post()
                .uri("/internal/llm/anthropic/v1/messages")
                .header("x-api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":1}")
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectBody()
                .json("{\"error\":\"rate_limited\"}");
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        @DisplayName("should reject path traversal attempts without forwarding upstream")
        void shouldRejectPathTraversal() {
            int requestsBefore = mockUpstream.getRequestCount();

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

            // Nothing should have been forwarded to the upstream
            assertThat(mockUpstream.getRequestCount())
                .as("Path traversal must NOT reach the upstream")
                .isEqualTo(requestsBefore);
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
