package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Workspace-admin CRUD for models on "your AI provider" plus the available-models union projection
 * ({@code /workspaces/{slug}/llm/**}, #1368): tenancy isolation, and — the sharpest edge of this
 * endpoint — that a shared-catalog entry in the available-models response never leaks the instance's
 * upstream model id, base URL, or credential shape.
 */
class WorkspaceLlmModelControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private LlmModelWorkspaceGrantRepository llmModelWorkspaceGrantRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Workspace " + slug, slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private WorkspaceLlmConnectionDTO createWorkspaceConnection(Workspace workspace, String slug) {
        var request = new CreateWorkspaceLlmConnectionRequestDTO(
            slug,
            "My Provider",
            "https://api.openai.com",
            "openai-completions",
            LlmAuthMode.BEARER,
            "sk-workspace-secret",
            true
        );
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/llm/connections", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(WorkspaceLlmConnectionDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private WorkspaceLlmModelDTO createWorkspaceModel(Workspace workspace, Long connectionId, String slug) {
        var request = new CreateWorkspaceLlmModelRequestDTO(
            slug,
            "My Model",
            "gpt-5-secret-upstream-id",
            null,
            null,
            null,
            true,
            PricingMode.NO_CHARGE,
            null,
            null,
            null,
            null,
            "Test-owned model has no per-token charge"
        );
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/llm/connections/{connectionId}/models", workspace.getWorkspaceSlug(), connectionId)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(WorkspaceLlmModelDTO.class)
            .returnResult()
            .getResponseBody();
    }

    private void saveGrant(Long modelId, Long workspaceId) {
        LlmModelWorkspaceGrant grant = new LlmModelWorkspaceGrant(modelId, workspaceId);
        grant.setGrantedAt(Instant.now());
        llmModelWorkspaceGrantRepository.save(grant);
    }

    private LlmModel seedInstanceModel(String upstreamId, ModelVisibility visibility, boolean enabled) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug("instance-conn-" + System.nanoTime());
        connection.setDisplayName("Instance Connection");
        connection.setBaseUrl("https://instance.example.com/v1");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = llmConnectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug("instance-model-" + System.nanoTime());
        model.setDisplayName("Shared Model");
        model.setUpstreamModelId(upstreamId);
        model.setVisibility(visibility);
        model.setEnabled(enabled);
        return llmModelRepository.save(model);
    }

    @Test
    @WithAdminUser
    void workspaceAdminCanCreateGetListUpdateAndDeleteAModel() {
        Workspace workspace = setupWorkspace("wsmodel-crud-ws");
        WorkspaceLlmConnectionDTO connection = createWorkspaceConnection(workspace, "conn-1");
        WorkspaceLlmModelDTO created = createWorkspaceModel(workspace, connection.id(), "model-1");

        assertThat(created).isNotNull();
        assertThat(created.slug()).isEqualTo("model-1");

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/models/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.slug")
            .isEqualTo("model-1");

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1);

        var updateRequest = new UpdateWorkspaceLlmModelRequestDTO(
            "Renamed Model",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/llm/models/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.displayName")
            .isEqualTo("Renamed Model");

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/llm/models/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/models/{id}", workspace.getWorkspaceSlug(), created.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void anAdminOfAnotherWorkspaceCannotReachThisWorkspacesModels() {
        Workspace workspaceA = setupWorkspace("wsmodel-tenancy-a");
        WorkspaceLlmConnectionDTO connectionA = createWorkspaceConnection(workspaceA, "conn-a");
        WorkspaceLlmModelDTO modelInA = createWorkspaceModel(workspaceA, connectionA.id(), "model-a");

        User ownerB = persistUser("wsmodel-tenancy-owner-b");
        Workspace workspaceB = createWorkspace(
            "wsmodel-tenancy-b",
            "Tenancy B",
            "wsmodel-tenancy-org-b",
            AccountType.ORG,
            ownerB
        );
        ensureAdminMembership(workspaceB);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/models/{id}", workspaceB.getWorkspaceSlug(), modelInA.id())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void availableModelsShowsPublicInstanceModelAndOwnBYOModel() {
        Workspace workspace = setupWorkspace("avail-basic-ws");
        seedInstanceModel("gpt-5-public-upstream", ModelVisibility.PUBLIC, true);
        WorkspaceLlmConnectionDTO connection = createWorkspaceConnection(workspace, "conn-1");
        createWorkspaceModel(workspace, connection.id(), "my-model");

        List<AvailableLlmModelDTO> available = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AvailableLlmModelDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(available).isNotNull();
        assertThat(available).hasSize(2);
        assertThat(available)
            .extracting(AvailableLlmModelDTO::scope)
            .containsExactlyInAnyOrder(LlmModelScope.SHARED, LlmModelScope.WORKSPACE);
    }

    @Test
    @WithAdminUser
    void availableModelsHidesAGrantedInstanceModelNotGrantedToThisWorkspace() {
        Workspace workspace = setupWorkspace("avail-grant-ws");
        LlmModel grantedElsewhere = seedInstanceModel("gpt-5-granted-upstream", ModelVisibility.GRANTED, true);
        // Grant it to a DIFFERENT workspace only.
        User otherOwner = persistUser("avail-grant-other-owner");
        Workspace otherWorkspace = createWorkspace(
            "avail-grant-other-ws",
            "Other",
            "avail-grant-other-org",
            AccountType.ORG,
            otherOwner
        );
        saveGrant(grantedElsewhere.getId(), otherWorkspace.getId());

        List<AvailableLlmModelDTO> available = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AvailableLlmModelDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(available).isNotNull();
        assertThat(available).isEmpty();
    }

    @Test
    @WithAdminUser
    void availableModelsShowsAGrantedInstanceModelOnceGrantedToThisWorkspace() {
        Workspace workspace = setupWorkspace("avail-grant-yes-ws");
        LlmModel granted = seedInstanceModel("gpt-5-granted-yes-upstream", ModelVisibility.GRANTED, true);
        saveGrant(granted.getId(), workspace.getId());

        List<AvailableLlmModelDTO> available = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AvailableLlmModelDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(available).isNotNull();
        assertThat(available).hasSize(1);
        assertThat(available.get(0).id()).isEqualTo(granted.getId());
    }

    @Test
    @WithAdminUser
    void availableModelsHidesADisabledInstanceModelAndADisabledOwnModel() {
        Workspace workspace = setupWorkspace("avail-disabled-ws");
        seedInstanceModel("gpt-5-disabled-upstream", ModelVisibility.PUBLIC, false);
        WorkspaceLlmConnectionDTO connection = createWorkspaceConnection(workspace, "conn-1");
        WorkspaceLlmModelDTO ownModel = createWorkspaceModel(workspace, connection.id(), "own-model");
        // Disable the just-created workspace model through the real update endpoint (also exercises
        // the enabled toggle, not just seeding).
        webTestClient
            .patch()
            .uri("/workspaces/{slug}/llm/models/{id}", workspace.getWorkspaceSlug(), ownModel.id())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                new UpdateWorkspaceLlmModelRequestDTO(null, null, null, null, false, null, null, null, null, null, null)
            )
            .exchange()
            .expectStatus()
            .isOk();

        List<AvailableLlmModelDTO> available = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<AvailableLlmModelDTO>>() {})
            .returnResult()
            .getResponseBody();

        assertThat(available).isNotNull();
        assertThat(available).isEmpty();
    }

    @Test
    @WithAdminUser
    void availableModelsResponseNeverContainsUpstreamModelIdBaseUrlOrKeyFields() {
        Workspace workspace = setupWorkspace("avail-redact-ws");
        seedInstanceModel("gpt-5-must-not-leak-upstream", ModelVisibility.PUBLIC, true);
        WorkspaceLlmConnectionDTO connection = createWorkspaceConnection(workspace, "conn-1");
        createWorkspaceModel(workspace, connection.id(), "my-model");

        String body = webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("upstreamModelId");
        assertThat(body).doesNotContain("baseUrl");
        assertThat(body).doesNotContain("apiKey");
        assertThat(body).doesNotContain("gpt-5-must-not-leak-upstream");
        assertThat(body).doesNotContain("gpt-5-secret-upstream-id");
        assertThat(body).doesNotContain("sk-workspace-secret");
    }

    @Test
    void anonymousIsUnauthorized() {
        User owner = persistUser("wsmodel-anon-owner");
        Workspace workspace = createWorkspace("wsmodel-anon-ws", "Anon", "wsmodel-anon-org", AccountType.ORG, owner);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/llm/available-models", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
