package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigDTO;
import de.tum.cit.aet.hephaestus.agent.config.CreateAgentConfigRequestDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * HTTP-boundary coverage for the AI-settings surface: binding (200 / cross-tenant 404), the
 * practice-review PATCH (400 on out-of-range cooldown), and the bound-config delete 409.
 */
class AiSettingsControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;

    @Autowired
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "AI Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private AgentConfigDTO createConfig(Workspace workspace, String name) {
        var request = CreateAgentConfigRequestDTO.builder()
            .name(name)
            .enabled(false)
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

    private AgentConfigDTO createExecutableConfig(Workspace workspace, String name) {
        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug("connection-" + name);
        connection.setDisplayName("Test connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = workspaceLlmConnectionRepository.save(connection);

        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setWorkspace(workspace);
        model.setConnection(connection);
        model.setSlug("model-" + name);
        model.setDisplayName("Test model");
        model.setUpstreamModelId("gpt-5");
        model.setEnabled(true);
        model = workspaceLlmModelRepository.save(model);

        var request = CreateAgentConfigRequestDTO.builder()
            .name(name)
            .enabled(true)
            .timeoutSeconds(300)
            .maxConcurrentJobs(2)
            .allowInternet(false)
            .workspaceModelId(model.getId())
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
    void bindsPracticeConfigAndReflectsItInTheAggregateRead() {
        Workspace workspace = setupWorkspace("ai-bind");
        AgentConfigDTO config = createExecutableConfig(workspace, "reviewer");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/ai-settings/practice-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("configId", config.id()))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.practiceConfigId")
            .isEqualTo(config.id());

        webTestClient
            .get()
            .uri("/workspaces/{slug}/ai-settings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.practiceConfigId")
            .isEqualTo(config.id());
    }

    @Test
    @WithAdminUser
    void rejectsBindingAConfigFromAnotherWorkspaceWith404() {
        Workspace workspaceA = setupWorkspace("ai-a");
        Workspace workspaceB = setupWorkspace("ai-b");
        AgentConfigDTO configInB = createConfig(workspaceB, "foreign");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/ai-settings/mentor-config", workspaceA.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("configId", configInB.id()))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void rejectsBindingDisabledConfigToMentorWith409() {
        Workspace workspace = setupWorkspace("ai-disabled-mentor");
        AgentConfigDTO config = createConfig(workspace, "disabled");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/ai-settings/mentor-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("configId", config.id()))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("The selected agent configuration is disabled or its model is not available.");
    }

    @Test
    @WithAdminUser
    void rejectsOutOfRangeCooldownWith400() {
        Workspace workspace = setupWorkspace("ai-cooldown");

        webTestClient
            .patch()
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("cooldownMinutes", 5000))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @WithAdminUser
    void overridesAndResetsPracticeReviewPolicy() {
        Workspace workspace = setupWorkspace("ai-reset");

        // Override skipDrafts to false (fleet default is true).
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("skipDrafts", false))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skipDraftsOverride")
            .isEqualTo(false)
            .jsonPath("$.skipDrafts")
            .isEqualTo(false);

        // Reset it back to inherit → override null, effective falls back to the fleet default (true).
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/ai-settings/practice-review", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("reset", List.of("SKIP_DRAFTS")))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skipDraftsOverride")
            .doesNotExist()
            .jsonPath("$.skipDrafts")
            .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    void rejectsDeletingABoundConfigWith409() {
        Workspace workspace = setupWorkspace("ai-delete");
        AgentConfigDTO config = createExecutableConfig(workspace, "bound");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/ai-settings/practice-config", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("configId", config.id()))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-configs/{id}", workspace.getWorkspaceSlug(), config.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
