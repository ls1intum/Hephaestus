package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Real HTTP proof of the proxy's narrow OpenAI-compatible trust boundary. */
class LlmProxyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static MockWebServer upstream;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private AgentConfigRepository configRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private LlmModelRepository modelRepository;

    private Workspace workspace;

    @BeforeAll
    static void startUpstream() throws Exception {
        upstream = new MockWebServer();
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() throws Exception {
        upstream.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.runtime.webhook.enabled", () -> "false");
        registry.add("hephaestus.sandbox.docker-host", () -> "unix:///nonexistent/llm-proxy-test.sock");
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
        registry.add("hephaestus.llm.egress.allow-loopback", () -> "true");
    }

    @BeforeEach
    void setUp() throws Exception {
        User owner = persistUser("proxy-owner");
        workspace = createWorkspace("proxy-ws", "Proxy Workspace", "proxy-org", AccountType.ORG, owner);
        while (upstream.takeRequest(0, TimeUnit.MILLISECONDS) != null) {
            // Drain requests from prior tests.
        }
    }

    @Test
    void shouldRequireProxyBearerToken() {
        webTestClient
            .post()
            .uri("/internal/llm/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void shouldForwardChatCompletionsWithCatalogModelAndBearerCredential() throws Exception {
        upstream.enqueue(jsonResponse("{\"id\":\"chatcmpl-test\"}"));
        AgentJob job = runningJob("openai-completions", LlmAuthMode.BEARER, "catalog-chat-model", true);

        webTestClient
            .post()
            .uri("/internal/llm/chat/completions")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"caller-model\",\"service_tier\":\"priority\",\"messages\":[]}")
            .exchange()
            .expectStatus()
            .isOk();

        RecordedRequest request = upstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getTarget()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer upstream-secret");
        assertThat(request.getBody().utf8()).contains("\"model\":\"catalog-chat-model\"");
        assertThat(request.getBody().utf8()).doesNotContain("caller-model").doesNotContain("service_tier");
    }

    @Test
    void shouldForwardResponsesWithRawApiKeyCredential() throws Exception {
        upstream.enqueue(jsonResponse("{\"id\":\"response-test\"}"));
        AgentJob job = runningJob("openai-responses", LlmAuthMode.API_KEY, "catalog-response-model", true);

        webTestClient
            .post()
            .uri("/internal/llm/responses")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"caller-model\",\"input\":\"hello\"}")
            .exchange()
            .expectStatus()
            .isOk();

        RecordedRequest request = upstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getTarget()).isEqualTo("/v1/responses");
        assertThat(request.getHeaders().get("api-key")).isEqualTo("upstream-secret");
        assertThat(request.getHeaders().get("Authorization")).isNull();
        assertThat(request.getBody().utf8()).contains("\"model\":\"catalog-response-model\"");
    }

    @Test
    void shouldRejectQueryAndWrongPathWithoutCallingUpstream() {
        AgentJob job = runningJob("openai-completions", LlmAuthMode.BEARER, "model", true);
        int requestsBefore = upstream.getRequestCount();

        webTestClient
            .post()
            .uri("/internal/llm/chat/completions?api-version=unsafe")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isBadRequest();
        webTestClient
            .post()
            .uri("/internal/llm/responses")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isNotFound();

        assertThat(upstream.getRequestCount()).isEqualTo(requestsBefore);
    }

    @Test
    void shouldRejectHostedProviderToolsWithoutCallingUpstream() {
        AgentJob job = runningJob("openai-responses", LlmAuthMode.BEARER, "model", true);
        int requestsBefore = upstream.getRequestCount();

        webTestClient
            .post()
            .uri("/internal/llm/responses")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"tools\":[{\"type\":\"web_search_preview\"}]}")
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertThat(upstream.getRequestCount()).isEqualTo(requestsBefore);
    }

    @Test
    void shouldFailClosedWhenCatalogModelIsDisabled() {
        AgentJob job = runningJob("openai-completions", LlmAuthMode.BEARER, "model", false);
        int requestsBefore = upstream.getRequestCount();

        webTestClient
            .post()
            .uri("/internal/llm/chat/completions")
            .header("Authorization", "Bearer " + job.getJobToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .isEqualTo(502);

        assertThat(upstream.getRequestCount()).isEqualTo(requestsBefore);
    }

    private AgentJob runningJob(String protocol, LlmAuthMode authMode, String upstreamModelId, boolean modelEnabled) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug("connection-" + System.nanoTime());
        connection.setDisplayName("Test connection");
        connection.setBaseUrl(upstream.url("/v1").toString().replaceAll("/$", ""));
        connection.setApiProtocol(protocol);
        connection.setAuthMode(authMode);
        connection.setApiKey("upstream-secret");
        connection.setEnabled(true);
        connection = connectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug("model-" + System.nanoTime());
        model.setDisplayName("Test model");
        model.setUpstreamModelId(upstreamModelId);
        model.setVisibility(ModelVisibility.PUBLIC);
        model.setEnabled(modelEnabled);
        model = modelRepository.save(model);

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("config-" + System.nanoTime());
        config = configRepository.save(config);

        ObjectNode snapshot = OBJECT_MAPPER.createObjectNode();
        snapshot.put("schemaVersion", ConfigSnapshot.SCHEMA_VERSION);
        snapshot.put("configId", config.getId());
        snapshot.put("configName", config.getName());
        snapshot.put("apiProtocol", protocol);
        snapshot.put("baseUrl", connection.getBaseUrl());
        snapshot.put("upstreamModelId", upstreamModelId);
        snapshot.put("supportsReasoning", false);
        snapshot.put("connectionScope", "INSTANCE");
        snapshot.put("connectionId", connection.getId());
        snapshot.put("modelId", model.getId());
        snapshot.put("workspaceId", workspace.getId());
        snapshot.put("timeoutSeconds", 600);
        snapshot.put("allowInternet", false);

        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setConfig(config);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(snapshot);
        return jobRepository.save(job);
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }
}
