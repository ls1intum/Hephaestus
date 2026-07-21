package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof of the #1368 slice-5 LLM proxy contract against a REAL Spring context: the
 * proxy is identified by the AUTHENTICATED token (an {@code AgentJob} job token or a mentor
 * session token), never by a {@code {provider}} path segment, and the connection funding the
 * call is re-resolved LIVE — from a real catalog row (instance or workspace) or the legacy
 * {@code AgentConfig} fallback — on every request via {@link
 * de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver#resolveProxyCredential}.
 *
 * <h2>Why a full Spring context, and how it avoids Docker flakiness</h2>
 *
 * <p>{@link LlmProxyController} and {@link LlmProxySecurityConfig} are gated on {@code
 * hephaestus.agent.enabled AND hephaestus.runtime.worker.enabled} — deliberately the SAME
 * expression {@code AgentJobExecutor} wires on, so "jobs on, proxy off" is unexpressible. Exercising
 * the proxy over HTTP therefore means booting the whole job-execution capability, not just the two
 * proxy beans in isolation. This test does that for real (mirroring the precedent set by {@code
 * AgentOrphanRecoveryIntegrationTest}), while keeping every other side effect of that capability
 * inert:
 *
 * <ul>
 *   <li>{@code hephaestus.agent.poll-interval} is set to an hour so {@code AgentJobExecutor}'s
 *       background poll loop stays quiescent for the duration of the test — this test only exercises
 *       the proxy HTTP surface, never the poll-claim pipeline, so there is nothing for it to contend
 *       with (the #1368 NATS→Postgres cutover replaced the old separate-stream-per-test trick with
 *       simply not polling).
 *   <li>{@code hephaestus.sandbox.docker-host} points at a socket path that can never exist, so the
 *       worker-role Docker beans ({@code DockerSandboxConfiguration}, {@code
 *       AgentImagePullBootstrapper}) wire (they're plain lazy clients / config beans — no I/O at
 *       construction) but every Docker call they might make at boot fails instantly and is caught +
 *       logged, never thrown (see {@code DockerClientOperations} / {@code
 *       ImagePullBootstrapperSupport}) — deterministic regardless of whether the host actually has a
 *       Docker daemon.
 *   <li>{@code hephaestus.llm.egress.allow-loopback=true} lets {@link EgressPolicy} accept the
 *       {@link MockWebServer}'s {@code http://localhost:PORT} target, mirroring local dev.
 * </ul>
 */
class LlmProxyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static MockWebServer mockUpstream;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;

    @Autowired
    private MentorProxyCredentialRegistry mentorProxyCredentialRegistry;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Workspace workspace;

    @BeforeAll
    static void startMockUpstream() throws java.io.IOException {
        mockUpstream = new MockWebServer();
        mockUpstream.start();
    }

    @AfterAll
    static void stopMockUpstream() throws java.io.IOException {
        mockUpstream.close();
    }

    @DynamicPropertySource
    static void configureJobExecutionCapability(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        // See the class Javadoc: keeps AgentJobExecutor's background poll loop quiescent.
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        // Webhook role off: WebhookConfiguration would otherwise need the unrelated sync
        // "natsConnection" bean, unrelated to this test.
        registry.add("hephaestus.runtime.webhook.enabled", () -> "false");
        registry.add("hephaestus.sandbox.docker-host", () -> "unix:///nonexistent/hephaestus-test-llm-proxy.sock");
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
        registry.add("hephaestus.llm.egress.allow-loopback", () -> "true");
    }

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("proxy-owner");
        workspace = createWorkspace("proxy-ws", "Proxy Workspace", "proxy-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
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

    // ── fixture builders ──

    /** A bare config used only as the job's FK — no credential material of its own. */
    private AgentConfig persistBareConfig() {
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("config-" + System.nanoTime());
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        return agentConfigRepository.save(config);
    }

    /** Legacy (pre-catalog) config carrying its own credential — the proxy's fallback path. */
    private AgentConfig persistLegacyConfig(String apiKey, String baseUrl) {
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("legacy-config-" + System.nanoTime());
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setLlmApiKey(apiKey);
        config.setLlmBaseUrl(baseUrl);
        return agentConfigRepository.save(config);
    }

    private LlmConnection persistInstanceConnection(
        String baseUrl,
        String apiProtocol,
        String headerName,
        String valuePrefix,
        String apiKey,
        boolean enabled
    ) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug("instance-conn-" + System.nanoTime());
        connection.setDisplayName("Instance connection");
        connection.setBaseUrl(baseUrl);
        connection.setApiProtocol(apiProtocol);
        connection.setAuthHeaderName(headerName);
        connection.setAuthValuePrefix(valuePrefix);
        connection.setApiKey(apiKey);
        connection.setEnabled(enabled);
        return llmConnectionRepository.save(connection);
    }

    private WorkspaceLlmConnection persistWorkspaceConnection(
        String baseUrl,
        String apiProtocol,
        String headerName,
        String valuePrefix,
        String apiKey
    ) {
        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug("ws-conn-" + System.nanoTime());
        connection.setDisplayName("Workspace BYO connection");
        connection.setBaseUrl(baseUrl);
        connection.setApiProtocol(apiProtocol);
        connection.setAuthHeaderName(headerName);
        connection.setAuthValuePrefix(valuePrefix);
        connection.setApiKey(apiKey);
        connection.setEnabled(true);
        return workspaceLlmConnectionRepository.save(connection);
    }

    private ConfigSnapshot legacySnapshot(AgentConfig config, String apiProtocol) {
        return new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            config.getId(),
            config.getName(),
            apiProtocol,
            "https://frozen-at-dispatch.invalid", // frozen placeholder — legacy resolution never reads it
            "claude-sonnet-4-20250514",
            null,
            null,
            null,
            false,
            null,
            null, // connectionScope — null selects the legacy AgentConfig fallback
            null, // connectionId
            600,
            false
        );
    }

    private ConfigSnapshot connectionSnapshot(FundingSource scope, Long connectionId, String apiProtocol) {
        return new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            0L,
            "connection-bound-config",
            apiProtocol,
            "https://frozen-at-dispatch.invalid", // frozen placeholder — the proxy re-resolves live instead
            "gpt-4o",
            null,
            null,
            null,
            false,
            null,
            scope,
            connectionId,
            600,
            false
        );
    }

    private AgentJob persistRunningJob(AgentConfig config, ConfigSnapshot snapshot) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(snapshot.toJson(OBJECT_MAPPER));
        return agentJobRepository.save(job);
    }

    private AgentJob persistJobWithStatus(AgentJobStatus status) {
        AgentConfig config = persistBareConfig();
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(status);
        job.setConfigSnapshot(legacySnapshot(config, "anthropic-messages").toJson(OBJECT_MAPPER));
        return agentJobRepository.save(job);
    }

    private String mockUpstreamBaseUrl() {
        return mockUpstream.url("/").toString().replaceAll("/$", "");
    }

    @Nested
    class SecurityIsolation {

        @Test
        void shouldRejectNoAuth() {
            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithAdminUser
        void shouldRejectJwtOnInternalEndpoints() {
            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .headers(TestAuthUtils.withCurrentUser())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectNonRunningJobToken() {
            AgentJob job = persistJobWithStatus(AgentJobStatus.COMPLETED);

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectQueuedJobToken() {
            AgentJob job = persistJobWithStatus(AgentJobStatus.QUEUED);

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectFailedJobToken() {
            AgentJob job = persistJobWithStatus(AgentJobStatus.FAILED);

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectFabricatedToken() {
            String fakeToken = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY";

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", fakeToken)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectEmptyApiKeyHeader() {
            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", "")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        void shouldRejectMalformedToken() {
            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", "invalid token with spaces!!!")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("a mentor token revoked (e.g. sandbox teardown) is refused, not resolved")
        void shouldRejectRevokedMentorToken() {
            UUID sessionId = UUID.randomUUID();
            String token = mentorProxyCredentialRegistry.mint(
                sessionId,
                "anthropic-messages",
                mockUpstreamBaseUrl(),
                null,
                null,
                null
            );
            mentorProxyCredentialRegistry.revoke(sessionId);

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", token)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    @Nested
    class ProxyForwarding {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "legacy (pre-catalog) job token: falls back to AgentConfig.llmApiKey, injecting the REAL key"
        )
        void shouldForwardLegacyConfigWithRealApiKey() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"id\":\"msg_test\",\"content\":[]}")
                    .build()
            );

            AgentConfig config = persistLegacyConfig("sk-ant-real-key-123", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
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
            assertThat(upstream.getTarget()).isEqualTo("/v1/messages");
            // Real API key injected — NOT the job token
            assertThat(upstream.getHeaders().get("x-api-key")).isEqualTo("sk-ant-real-key-123");
            assertThat(upstream.getHeaders().get("x-api-key")).isNotEqualTo(job.getJobToken());
            assertThat(upstream.getHeaders().get("Authorization")).isNull();
            assertThat(upstream.getBody().utf8()).contains("claude-sonnet-4-20250514");
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "INSTANCE-scoped catalog connection: the job routes to its own connection, live-resolved"
        )
        void shouldForwardInstanceConnectionWithRealApiKey() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"id\":\"chatcmpl-test\",\"choices\":[]}")
                    .build()
            );

            LlmConnection connection = persistInstanceConnection(
                mockUpstreamBaseUrl(),
                "openai-completions",
                "Authorization",
                "Bearer ",
                "sk-instance-real-key-456",
                true
            );
            AgentConfig config = persistBareConfig();
            AgentJob job = persistRunningJob(
                config,
                connectionSnapshot(FundingSource.INSTANCE, connection.getId(), "openai-completions")
            );

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
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
            assertThat(upstream.getTarget()).isEqualTo("/v1/chat/completions");
            assertThat(upstream.getHeaders().get("Authorization")).isEqualTo("Bearer sk-instance-real-key-456");
            // The proxy-scoped job token must NOT reach the upstream
            assertThat(upstream.getHeaders().get("Authorization")).doesNotContain(job.getJobToken());
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "WORKSPACE-scoped BYO connection: the job routes to the workspace's own connection"
        )
        void shouldForwardWorkspaceConnectionWithRealApiKey() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"id\":\"msg_byo\",\"content\":[]}")
                    .build()
            );

            WorkspaceLlmConnection connection = persistWorkspaceConnection(
                mockUpstreamBaseUrl(),
                "anthropic-messages",
                "x-api-key",
                "",
                "sk-byo-real-key-789"
            );
            AgentConfig config = persistBareConfig();
            AgentJob job = persistRunningJob(
                config,
                connectionSnapshot(FundingSource.WORKSPACE, connection.getId(), "anthropic-messages")
            );

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
                .header("x-api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"claude-sonnet-4-20250514\",\"max_tokens\":1}")
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getHeaders().get("x-api-key")).isEqualTo("sk-byo-real-key-789");
            assertThat(upstream.getHeaders().get("x-api-key")).isNotEqualTo(job.getJobToken());
        }

        @Test
        void shouldAuthenticateViaApiKeyForAzure() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"id\":\"chatcmpl-azure\"}")
                    .build()
            );

            LlmConnection connection = persistInstanceConnection(
                mockUpstreamBaseUrl(),
                "azure-openai-responses",
                "api-key",
                "",
                "sk-azure-real-key",
                true
            );
            AgentConfig config = persistBareConfig();
            AgentJob job = persistRunningJob(
                config,
                connectionSnapshot(FundingSource.INSTANCE, connection.getId(), "azure-openai-responses")
            );

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
                .header("api-key", job.getJobToken())
                .header("Content-Type", "application/json")
                .bodyValue("{\"model\":\"gpt-4\"}")
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getHeaders().get("api-key")).isEqualTo("sk-azure-real-key");
            assertThat(upstream.getHeaders().get("api-key")).isNotEqualTo(job.getJobToken());
        }

        @Test
        void shouldSupportGetMethod() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"data\":[{\"id\":\"gpt-4\"}]}")
                    .build()
            );

            AgentConfig config = persistLegacyConfig("sk-list-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "openai-completions"));

            webTestClient
                .get()
                .uri("/internal/llm/v1/models")
                .header("Authorization", "Bearer " + job.getJobToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"data\":[{\"id\":\"gpt-4\"}]}");

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getMethod()).isEqualTo("GET");
            assertThat(upstream.getTarget()).isEqualTo("/v1/models");
            assertThat(upstream.getHeaders().get("Authorization")).isEqualTo("Bearer sk-list-key");
        }

        @Test
        void shouldStreamSseResponse() throws Exception {
            String ssePayload = "data: {\"id\":\"msg_stream\"}\n\ndata: [DONE]\n\n";
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(ssePayload)
                    .build()
            );

            AgentConfig config = persistLegacyConfig("sk-ant-sse-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            // SSE body content is verified by ProxyStreamingUtilsTest.
            // WebTestClient cannot capture bytes written directly to HttpServletResponse
            // (the controller bypasses Spring MVC for SSE streaming and returns null),
            // so we verify status, content-type, and upstream auth header injection here.
            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
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
            assertThat(upstream.getHeaders().get("x-api-key")).isEqualTo("sk-ant-sse-key");
        }

        @Test
        void shouldForwardQueryParameters() throws Exception {
            mockUpstream.enqueue(
                new MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"data\":[]}")
                    .build()
            );

            AgentConfig config = persistLegacyConfig("sk-query-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "openai-completions"));

            webTestClient
                .get()
                .uri("/internal/llm/v1/models?limit=10&order=desc")
                .header("Authorization", "Bearer " + job.getJobToken())
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getTarget()).contains("limit=10");
            assertThat(upstream.getTarget()).contains("order=desc");
        }

        @Test
        void shouldForwardUpstreamErrors() throws Exception {
            mockUpstream.enqueue(new MockResponse.Builder().code(429).body("{\"error\":\"rate_limited\"}").build());

            AgentConfig config = persistLegacyConfig("sk-test-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            webTestClient
                .post()
                .uri("/internal/llm/v1/messages")
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
    class ConnectionResolutionSecurity {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "a connection disabled after the job's snapshot was frozen is refused with 502, never forwarded"
        )
        void shouldRefuseDisabledConnection() {
            int requestsBefore = mockUpstream.getRequestCount();

            LlmConnection connection = persistInstanceConnection(
                mockUpstreamBaseUrl(),
                "openai-completions",
                "Authorization",
                "Bearer ",
                "sk-should-never-be-used",
                false // disabled — e.g. an admin revoked it after this job's snapshot was frozen
            );
            AgentConfig config = persistBareConfig();
            AgentJob job = persistRunningJob(
                config,
                connectionSnapshot(FundingSource.INSTANCE, connection.getId(), "openai-completions")
            );

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(502)
                .expectBody(String.class)
                .isEqualTo("No credential configured for this connection");

            assertThat(mockUpstream.getRequestCount())
                .as("a disabled connection must never be forwarded to")
                .isEqualTo(requestsBefore);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "a connection deleted after the job's snapshot was frozen is refused with 502, never forwarded"
        )
        void shouldRefuseDeletedConnection() {
            int requestsBefore = mockUpstream.getRequestCount();

            AgentConfig config = persistBareConfig();
            // 999_999 never exists — simulates a connection deleted after dispatch.
            AgentJob job = persistRunningJob(
                config,
                connectionSnapshot(FundingSource.INSTANCE, 999_999L, "openai-completions")
            );

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(502)
                .expectBody(String.class)
                .isEqualTo("No credential configured for this connection");

            assertThat(mockUpstream.getRequestCount()).isEqualTo(requestsBefore);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "an egress-rejected live base URL refuses forwarding with 502, never reaching upstream"
        )
        void shouldRefuseEgressRejectedBaseUrl() {
            int requestsBefore = mockUpstream.getRequestCount();

            // A private, non-loopback host is rejected by EgressPolicy regardless of allow-loopback.
            AgentConfig config = persistLegacyConfig("sk-should-never-be-used", "http://10.0.0.5:9999");
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "openai-completions"));

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(502)
                .expectBody(String.class)
                .isEqualTo("Upstream target not permitted");

            assertThat(mockUpstream.getRequestCount())
                .as("an egress-rejected host must never be forwarded to")
                .isEqualTo(requestsBefore);
        }
    }

    @Nested
    class HeaderHandling {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
            "the inbound Authorization header (carrying the proxy-scoped job token) is never forwarded verbatim"
        )
        void shouldStripInboundAuthorizationHeader() throws Exception {
            mockUpstream.enqueue(new MockResponse.Builder().code(200).body("{}").build());

            AgentConfig config = persistLegacyConfig("sk-real-openai-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "openai-completions"));

            webTestClient
                .post()
                .uri("/internal/llm/v1/chat/completions")
                .header("Authorization", "Bearer " + job.getJobToken())
                .header("X-Forwarded-For", "203.0.113.7") // an arbitrary extra inbound header should pass through
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk();

            RecordedRequest upstream = mockUpstream.takeRequest(5, TimeUnit.SECONDS);
            assertThat(upstream).isNotNull();
            assertThat(upstream.getHeaders().get("Authorization")).isEqualTo("Bearer sk-real-openai-key");
            assertThat(upstream.getHeaders().get("Authorization")).doesNotContain(job.getJobToken());
        }
    }

    @Nested
    class InputValidation {

        @BeforeEach
        void setUp() {
            drainMockUpstream();
        }

        @Test
        void shouldRejectPathTraversal() {
            int requestsBefore = mockUpstream.getRequestCount();

            AgentConfig config = persistLegacyConfig("sk-test-key", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            webTestClient
                .post()
                .uri("/internal/llm/v1/../../../admin")
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

            assertThat(mockUpstream.getRequestCount())
                .as("Path traversal must NOT reach the upstream")
                .isEqualTo(requestsBefore);
        }
    }

    @Nested
    class TokenHashIntegrity {

        @Test
        void shouldPersistTokenHash() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            AgentJob loaded = agentJobRepository.findById(job.getId()).orElseThrow();
            assertThat(loaded.getJobTokenHash()).isNotNull();
            assertThat(loaded.getJobTokenHash()).hasSize(64); // SHA-256 hex
            assertThat(loaded.getJobTokenHash()).isEqualTo(AgentJob.computeTokenHash(loaded.getJobToken()));
        }

        @Test
        void shouldLookUpByTokenHash() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            String hash = AgentJob.computeTokenHash(job.getJobToken());
            var found = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.RUNNING);
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(job.getId());
        }

        @Test
        void shouldNotFindJobWithWrongStatus() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            String hash = AgentJob.computeTokenHash(job.getJobToken());
            var found = agentJobRepository.findByJobTokenHashAndStatus(hash, AgentJobStatus.COMPLETED);
            assertThat(found).isEmpty();
        }

        @Test
        void jobTokenShouldBeUnique() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job1 = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));
            AgentJob job2 = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            assertThat(job1.getJobToken()).isNotEqualTo(job2.getJobToken());
            assertThat(job1.getJobTokenHash()).isNotEqualTo(job2.getJobTokenHash());
        }

        @Test
        void jobTokenShouldBe43Chars() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

            assertThat(job.getJobToken()).hasSize(43);
            assertThat(job.getJobToken()).matches("[A-Za-z0-9_-]+");
        }

        @Test
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
    class CrossChainSecurity {

        @Autowired
        private java.util.List<org.springframework.security.web.SecurityFilterChain> filterChains;

        /**
         * Lives here (not in SecurityFilterChainRuntimeIntegrationTest) because the proxy chain only
         * exists when the job-execution capability is on — this class's context enables it; the default
         * integration context does not boot the proxy at all.
         */
        @Test
        void llmProxyChainHasJobTokenFilterBeforeUpaf() {
            org.springframework.security.web.SecurityFilterChain llmProxy = filterChains
                .stream()
                .filter(c -> c.getFilters().stream().anyMatch(JobTokenAuthenticationFilter.class::isInstance))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LLM proxy chain missing JobTokenAuthenticationFilter"));

            var filters = llmProxy.getFilters();
            int jobToken = -1;
            int upaf = -1;
            for (int i = 0; i < filters.size(); i++) {
                if (filters.get(i) instanceof JobTokenAuthenticationFilter) jobToken = i;
                if (
                    filters.get(i) instanceof
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                ) upaf = i;
            }
            assertThat(jobToken).as("JobTokenAuthenticationFilter must be present").isGreaterThanOrEqualTo(0);
            if (upaf >= 0) {
                assertThat(jobToken).as("JobToken must precede UsernamePasswordAuthenticationFilter").isLessThan(upaf);
            }
        }

        @Test
        @WithAdminUser
        void jobTokenShouldNotAccessMainApi() {
            AgentConfig config = persistLegacyConfig("sk-test", mockUpstreamBaseUrl());
            AgentJob job = persistRunningJob(config, legacySnapshot(config, "anthropic-messages"));

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
