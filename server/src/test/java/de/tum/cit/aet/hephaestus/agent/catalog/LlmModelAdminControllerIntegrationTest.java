package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Instance-admin CRUD, pricing, and sharing for the LLM model catalog ({@code /admin/llm/models},
 * #1368): app_admin gate, price-supersede history, selected-workspace sharing roundtrip, and the
 * delete-in-use guard.
 */
class LlmModelAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    private LlmConnection seedConnection() {
        LlmConnection connection = new LlmConnection();
        connection.setSlug("conn-" + System.nanoTime());
        connection.setDisplayName("Test Connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        return llmConnectionRepository.save(connection);
    }

    private LlmModelDTO createModel(Long connectionId, String slug) {
        var request = new CreateLlmModelRequestDTO(
            slug,
            "Test Model",
            "gpt-5",
            null,
            null,
            null,
            null,
            null,
            null,
            true
        );
        return webTestClient
            .post()
            .uri("/admin/llm/connections/{connectionId}/models", connectionId)
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();
    }

    @Test
    void appAdminCanCreateGetListUpdateAndDeleteAModel() {
        LlmConnection connection = seedConnection();
        LlmModelDTO created = createModel(connection.getId(), "gpt-5-eu");
        assertThat(created).isNotNull();
        assertThat(created.slug()).isEqualTo("gpt-5-eu");
        assertThat(created.visibility()).isEqualTo(ModelVisibility.PUBLIC);
        assertThat(created.currentPrice()).isNull();

        webTestClient
            .get()
            .uri("/admin/llm/models/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.slug")
            .isEqualTo("gpt-5-eu");

        webTestClient
            .get()
            .uri("/admin/llm/models")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1);

        var updateRequest = new UpdateLlmModelRequestDTO(
            "Renamed Model",
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
            .put()
            .uri("/admin/llm/models/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
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
            .uri("/admin/llm/models/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isNoContent();

        webTestClient
            .get()
            .uri("/admin/llm/models/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void repricingTwiceSupersedesAndGetReturnsOnlyTheCurrentPrice() {
        LlmConnection connection = seedConnection();
        LlmModelDTO model = createModel(connection.getId(), "reprice-model");

        var firstPrice = new UpdateLlmModelPriceRequestDTO(
            PricingMode.PRICED,
            new BigDecimal("1.00"),
            new BigDecimal("2.00"),
            null,
            null,
            null,
            null
        );
        webTestClient
            .put()
            .uri("/admin/llm/models/{id}/price", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(firstPrice)
            .exchange()
            .expectStatus()
            .isOk();

        var secondPrice = new UpdateLlmModelPriceRequestDTO(
            PricingMode.PRICED,
            new BigDecimal("3.00"),
            new BigDecimal("4.00"),
            null,
            null,
            null,
            null
        );
        LlmModelDTO afterSecondPrice = webTestClient
            .put()
            .uri("/admin/llm/models/{id}/price", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(secondPrice)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(afterSecondPrice).isNotNull();
        assertThat(afterSecondPrice.currentPrice()).isNotNull();
        assertThat(afterSecondPrice.currentPrice().per1mInputUsd()).isEqualByComparingTo("3.00");
        assertThat(afterSecondPrice.currentPrice().per1mOutputUsd()).isEqualByComparingTo("4.00");

        // GET after two supersedes still reports exactly the current (second) price — the superseded
        // first price row is never surfaced through this endpoint.
        LlmModelDTO fetched = webTestClient
            .get()
            .uri("/admin/llm/models/{id}", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.currentPrice().per1mInputUsd()).isEqualByComparingTo("3.00");
    }

    @Test
    void sharingWithASelectedWorkspaceSetRoundTrips() {
        LlmConnection connection = seedConnection();
        LlmModelDTO model = createModel(connection.getId(), "sharing-model");

        User owner = persistUser("sharing-owner");
        Workspace workspaceA = createWorkspace("sharing-ws-a", "Sharing A", "sharing-org-a", AccountType.ORG, owner);
        User ownerB = persistUser("sharing-owner-b");
        Workspace workspaceB = createWorkspace("sharing-ws-b", "Sharing B", "sharing-org-b", AccountType.ORG, ownerB);

        var grantRequest = new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of(workspaceA.getId()));
        LlmModelDTO afterGrant = webTestClient
            .put()
            .uri("/admin/llm/models/{id}/sharing", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(grantRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(afterGrant).isNotNull();
        assertThat(afterGrant.visibility()).isEqualTo(ModelVisibility.GRANTED);
        assertThat(afterGrant.grantedWorkspaceIds()).containsExactly(workspaceA.getId());
        assertThat(afterGrant.grantedWorkspaceIds()).doesNotContain(workspaceB.getId());

        // Replacing the selected set drops the old grant and keeps only the new one.
        var replaceRequest = new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of(workspaceB.getId()));
        LlmModelDTO afterReplace = webTestClient
            .put()
            .uri("/admin/llm/models/{id}/sharing", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(replaceRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(afterReplace).isNotNull();
        assertThat(afterReplace.grantedWorkspaceIds()).containsExactly(workspaceB.getId());

        // Switching back to PUBLIC clears the grant set entirely.
        var publicRequest = new UpdateLlmModelSharingRequestDTO(ModelVisibility.PUBLIC, null);
        LlmModelDTO afterPublic = webTestClient
            .put()
            .uri("/admin/llm/models/{id}/sharing", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(publicRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(LlmModelDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(afterPublic).isNotNull();
        assertThat(afterPublic.visibility()).isEqualTo(ModelVisibility.PUBLIC);
        assertThat(afterPublic.grantedWorkspaceIds()).isEmpty();
    }

    @Test
    void deletingAModelBoundToAnAgentConfigReturns409() {
        LlmConnection connection = seedConnection();
        LlmModelDTO model = createModel(connection.getId(), "bound-model");

        User owner = persistUser("delete-guard-owner");
        Workspace workspace = createWorkspace(
            "delete-guard-ws",
            "Delete Guard",
            "delete-guard-org",
            AccountType.ORG,
            owner
        );
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("bound-config");
        config.setLlmProvider(LlmProvider.OPENAI);
        config.setInstanceModel(llmModelFromRepository(model.id()));
        agentConfigRepository.save(config);

        webTestClient
            .delete()
            .uri("/admin/llm/models/{id}", model.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isEqualTo(409);
    }

    @Test
    void nonAdminIsForbidden() {
        webTestClient
            .get()
            .uri("/admin/llm/models")
            .headers(h -> h.setBearerAuth(MENTOR_TOKEN))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void anonymousIsUnauthorized() {
        webTestClient.get().uri("/admin/llm/models").exchange().expectStatus().isUnauthorized();
    }

    private LlmModel llmModelFromRepository(Long id) {
        return llmModelRepository.findById(id).orElseThrow();
    }
}
