package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The per-purpose binding endpoints over the real stack (#1368).
 *
 * <p>Exists mainly for the listing: it reports each binding's {@code ready}, which resolves the bound
 * model → connection AFTER the loading transaction closed (readiness is judged outside a transaction
 * on purpose — resolve() signals a revoked model by throwing, and catching that inside a shared
 * transaction would still mark it rollback-only). A plain finder there returns rows whose LAZY model
 * blows up on the first touch, so the endpoint 500s with LazyInitializationException instead of
 * answering. Unit tests cannot see it — they mock the repository and never cross a session boundary.
 */
class AgentBindingControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Binding Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private LlmModel seedInstanceModel(String slug) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug(slug);
        connection.setDisplayName("Binding test");
        connection.setBaseUrl("https://api.openai.example/v1");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = llmConnectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug(slug + "-model");
        model.setDisplayName("Binding test model");
        model.setUpstreamModelId("gpt-binding-test");
        model.setVisibility(ModelVisibility.PUBLIC);
        model.setEnabled(true);
        return llmModelRepository.save(model);
    }

    @Test
    @WithAdminUser
    @DisplayName("listing bindings reports readiness without tripping over the bound model's lazy associations")
    void listingReportsReadinessForAFreshlyLoadedBinding() {
        Workspace workspace = setupWorkspace("binding-list");
        LlmModel model = seedInstanceModel("binding-list");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-bindings/{purpose}", workspace.getWorkspaceSlug(), "PRACTICE_DETECTION")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", true))
            .exchange()
            .expectStatus()
            .isOk();

        // The listing loads the binding fresh — the write above cannot have left it hydrated in a
        // session, which is exactly the path that regressed.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-bindings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].purpose")
            .isEqualTo("PRACTICE_DETECTION")
            .jsonPath("$[0].instanceModelId")
            .isEqualTo(model.getId())
            .jsonPath("$[0].ready")
            .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    @DisplayName("a disabled binding is listed but not ready, and deleting it turns the purpose off")
    void disabledBindingIsNotReadyAndCanBeDeleted() {
        Workspace workspace = setupWorkspace("binding-off");
        LlmModel model = seedInstanceModel("binding-off");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-bindings/{purpose}", workspace.getWorkspaceSlug(), "MENTOR")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", false))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-bindings", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].enabled")
            .isEqualTo(false)
            .jsonPath("$[0].ready")
            .isEqualTo(false);

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agent-bindings/{purpose}", workspace.getWorkspaceSlug(), "MENTOR")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-bindings", workspace.getWorkspaceSlug())
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
    @DisplayName("binding a model the workspace cannot use is rejected")
    void bindingAnUnavailableModelIsRejected() {
        Workspace workspace = setupWorkspace("binding-bad");
        LlmModel model = seedInstanceModel("binding-bad");
        model.setEnabled(false);
        llmModelRepository.save(model);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agent-bindings/{purpose}", workspace.getWorkspaceSlug(), "PRACTICE_DETECTION")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", true))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @DisplayName("the endpoints require authentication")
    void anonymousIsRejected() {
        // Against a REAL workspace: an unknown slug 404s during resolution, which would pass this
        // assertion without ever reaching the authentication check.
        User owner = persistUser("binding-anon-owner");
        Workspace workspace = createWorkspace(
            "binding-anon",
            "Binding Workspace",
            "binding-anon-org",
            AccountType.ORG,
            owner
        );

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agent-bindings", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
