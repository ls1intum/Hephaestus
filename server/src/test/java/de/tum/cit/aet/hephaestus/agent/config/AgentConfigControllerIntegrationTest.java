package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelWorkspaceGrant;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelWorkspaceGrantRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

class AgentConfigControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private LlmModelResolver llmModelResolver;

    @Autowired
    private LlmModelWorkspaceGrantRepository llmModelWorkspaceGrantRepository;

    @Autowired
    private WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;

    @Autowired
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    private LlmModel seedInstanceModel(ModelVisibility visibility, boolean modelEnabled, boolean connectionEnabled) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug("conn-" + System.nanoTime());
        connection.setDisplayName("Instance Connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(connectionEnabled);
        connection = llmConnectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug("model-" + System.nanoTime());
        model.setDisplayName("Instance Model");
        model.setUpstreamModelId("gpt-5");
        model.setVisibility(visibility);
        model.setEnabled(modelEnabled);
        return llmModelRepository.save(model);
    }

    private WorkspaceLlmModel seedWorkspaceModel(Workspace workspace) {
        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug("byo-conn-" + System.nanoTime());
        connection.setDisplayName("BYO Connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = workspaceLlmConnectionRepository.save(connection);

        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setWorkspace(workspace);
        model.setConnection(connection);
        model.setSlug("byo-model-" + System.nanoTime());
        model.setDisplayName("BYO Model");
        model.setUpstreamModelId("gpt-5");
        model.setEnabled(true);
        return workspaceLlmModelRepository.save(model);
    }

    private Workspace setupWorkspace() {
        User owner = persistUser("agent-config-owner");
        Workspace workspace = createWorkspace("agent-ws", "Agent Workspace", "agent-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private AgentConfigDTO createConfig(Workspace workspace, String name) {
        var request = CreateAgentConfigRequestDTO.builder()
            .name(name)
            .enabled(true)
            .modelName("claude-sonnet-4-20250514")
            .llmApiKey("sk-test-secret-key-123")
            .llmProvider(LlmProvider.ANTHROPIC)
            .timeoutSeconds(300)
            .maxConcurrentJobs(2)
            .allowInternet(false)
            .build();

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

        var duplicateRequest = CreateAgentConfigRequestDTO.builder()
            .name("duplicate-name")
            .enabled(true)
            .llmProvider(LlmProvider.OPENAI)
            .build();

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
        var updateRequest = UpdateAgentConfigRequestDTO.builder()
            .modelName("gpt-4o")
            .llmProvider(LlmProvider.OPENAI)
            .timeoutSeconds(120)
            .maxConcurrentJobs(1)
            .allowInternet(true)
            .build();

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

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

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

        // Neither llmProvider nor a model binding (instanceModelId/workspaceModelId) — #1368 relaxed
        // llmProvider to optional at the DTO level, but the service still requires ONE of the two.
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

        var request = CreateAgentConfigRequestDTO.builder()
            .name("")
            .enabled(true)
            .llmProvider(LlmProvider.ANTHROPIC)
            .build();

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

        var request = CreateAgentConfigRequestDTO.builder()
            .name("bad-timeout")
            .enabled(true)
            .llmProvider(LlmProvider.ANTHROPIC)
            .timeoutSeconds(5) // below minimum of 30
            .build();

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

        var request = CreateAgentConfigRequestDTO.builder()
            .name("location-test")
            .enabled(true)
            .llmProvider(LlmProvider.ANTHROPIC)
            .build();

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

        var request = CreateAgentConfigRequestDTO.builder()
            .name("secret-test")
            .enabled(true)
            .llmApiKey("sk-super-secret-key")
            .llmProvider(LlmProvider.ANTHROPIC)
            .build();

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

    // --- Model-binding create path (#1368: llmProvider relaxed to optional when a binding is given) ---

    @Test
    @WithAdminUser
    void postWithoutLlmProviderButWithInstanceModelBindingSucceeds() {
        Workspace workspace = setupWorkspace();
        LlmModel model = seedInstanceModel(ModelVisibility.PUBLIC, true, true);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("bound-instance-model")
            .enabled(true)
            .instanceModelId(model.getId())
            .build();

        AgentConfigDTO created = webTestClient
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

        assertThat(created).isNotNull();
        assertThat(created.instanceModelId()).isEqualTo(model.getId());
        assertThat(created.workspaceModelId()).isNull();
        // llmProvider is @NonNull on the DTO even for a bound config — the service fills a harmless
        // placeholder on the entity (the NOT NULL legacy column), never read by a bound config.
        assertThat(created.llmProvider()).isNotNull();
    }

    @Test
    @WithAdminUser
    void workspaceScopedLookupFetchesBoundModelRuntimeOutsideRepositoryTransaction() {
        Workspace workspace = setupWorkspace();
        LlmModel model = seedInstanceModel(ModelVisibility.PUBLIC, true, true);
        AgentConfigDTO created = webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                CreateAgentConfigRequestDTO.builder()
                    .name("detached-runtime-model")
                    .enabled(true)
                    .instanceModelId(model.getId())
                    .build()
            )
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AgentConfigDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(created).isNotNull();
        AgentConfig detached = agentConfigRepository
            .findByIdAndWorkspaceId(created.id(), workspace.getId())
            .orElseThrow();

        assertThat(llmModelResolver.resolve(detached).baseUrl()).isEqualTo("https://api.openai.com");
    }

    @Test
    @WithAdminUser
    void postWithoutLlmProviderButWithWorkspaceModelBindingSucceeds() {
        Workspace workspace = setupWorkspace();
        WorkspaceLlmModel model = seedWorkspaceModel(workspace);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("bound-workspace-model")
            .enabled(true)
            .workspaceModelId(model.getId())
            .build();

        AgentConfigDTO created = webTestClient
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

        assertThat(created).isNotNull();
        assertThat(created.workspaceModelId()).isEqualTo(model.getId());
        assertThat(created.instanceModelId()).isNull();
    }

    @Test
    @WithAdminUser
    void postWithBothInstanceAndWorkspaceModelIdsReturns400() {
        Workspace workspace = setupWorkspace();
        LlmModel instanceModel = seedInstanceModel(ModelVisibility.PUBLIC, true, true);
        WorkspaceLlmModel workspaceModel = seedWorkspaceModel(workspace);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("both-bindings")
            .enabled(true)
            .instanceModelId(instanceModel.getId())
            .workspaceModelId(workspaceModel.getId())
            .build();

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
    void postBindingANonVisibleInstanceModelReturns400() {
        Workspace workspace = setupWorkspace();
        // GRANTED but no grant recorded for this workspace — not visible to it.
        LlmModel model = seedInstanceModel(ModelVisibility.GRANTED, true, true);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("non-visible-model")
            .enabled(true)
            .instanceModelId(model.getId())
            .build();

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
    void postBindingAGrantedInstanceModelWithAGrantSucceeds() {
        Workspace workspace = setupWorkspace();
        LlmModel model = seedInstanceModel(ModelVisibility.GRANTED, true, true);
        LlmModelWorkspaceGrant grant = new LlmModelWorkspaceGrant(model.getId(), workspace.getId());
        grant.setGrantedAt(Instant.now());
        llmModelWorkspaceGrantRepository.save(grant);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("granted-model")
            .enabled(true)
            .instanceModelId(model.getId())
            .build();

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated();
    }

    @Test
    @WithAdminUser
    void postBindingOtherWorkspacesBYOModelReturns404() {
        Workspace workspaceA = setupWorkspace();

        User ownerB = persistUser("bind-owner-b");
        Workspace workspaceB = createWorkspace("bind-ws-b", "Bind B", "bind-org-b", AccountType.ORG, ownerB);
        WorkspaceLlmModel modelInB = seedWorkspaceModel(workspaceB);

        var request = CreateAgentConfigRequestDTO.builder()
            .name("cross-workspace-model")
            .enabled(true)
            .workspaceModelId(modelInB.getId())
            .build();

        webTestClient
            .post()
            .uri("/workspaces/{slug}/agent-configs", workspaceA.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isNotFound();
    }
}
