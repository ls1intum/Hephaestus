package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Instance-admin CRUD for the LLM connection catalog ({@code /admin/llm/connections}, #1368): app_admin
 * access gate, write-only API key redaction, and the delete-in-use guard. {@code LlmConnectionServiceTest}
 * covers business-rule detail; this stays scoped to the HTTP contract.
 */
class LlmConnectionAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    private LlmConnectionDTO createConnection(String slug) {
        var request = new CreateLlmConnectionRequestDTO(
            slug,
            "Test Connection",
            "https://api.openai.com",
            "openai-completions",
            null,
            null,
            "sk-test-secret-1234",
            null,
            true
        );
        return webTestClient
            .post()
            .uri("/admin/llm/connections")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(LlmConnectionDTO.class)
            .returnResult()
            .getResponseBody();
    }

    @Test
    void appAdminCanCreateGetListUpdateAndDeleteAConnection() {
        LlmConnectionDTO created = createConnection("openai-prod");
        assertThat(created).isNotNull();
        assertThat(created.slug()).isEqualTo("openai-prod");
        assertThat(created.hasApiKey()).isTrue();
        assertThat(created.apiKeyLast4()).isEqualTo("1234");

        webTestClient
            .get()
            .uri("/admin/llm/connections/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.slug")
            .isEqualTo("openai-prod");

        webTestClient
            .get()
            .uri("/admin/llm/connections")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1);

        var updateRequest = new UpdateLlmConnectionRequestDTO(
            "Renamed Connection",
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
            .uri("/admin/llm/connections/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.displayName")
            .isEqualTo("Renamed Connection")
            // The key was never re-supplied on this update — it must survive untouched.
            .jsonPath("$.hasApiKey")
            .isEqualTo(true)
            .jsonPath("$.apiKeyLast4")
            .isEqualTo("1234");

        webTestClient
            .delete()
            .uri("/admin/llm/connections/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isNoContent();

        webTestClient
            .get()
            .uri("/admin/llm/connections/{id}", created.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void apiKeyIsNeverExposedInAnyResponse() {
        String body = webTestClient
            .post()
            .uri("/admin/llm/connections")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                new CreateLlmConnectionRequestDTO(
                    "redaction-test",
                    "Redaction Test",
                    "https://api.openai.com",
                    "openai-completions",
                    null,
                    null,
                    "sk-super-secret-value",
                    null,
                    true
                )
            )
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("sk-super-secret-value");
        assertThat(body).doesNotContain("\"apiKey\"");
        assertThat(body).contains("hasApiKey");
    }

    @Test
    void deletingAConnectionWithModelsReturns409() {
        LlmConnectionDTO connection = createConnection("in-use-connection");
        LlmModel model = new LlmModel();
        model.setConnection(llmConnectionRepository.findById(connection.id()).orElseThrow());
        model.setSlug("gpt-5-eu");
        model.setDisplayName("GPT-5 EU");
        model.setUpstreamModelId("gpt-5");
        llmModelRepository.save(model);

        webTestClient
            .delete()
            .uri("/admin/llm/connections/{id}", connection.id())
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isEqualTo(409);
    }

    @Test
    void nonAdminIsForbidden() {
        webTestClient
            .get()
            .uri("/admin/llm/connections")
            .headers(h -> h.setBearerAuth(MENTOR_TOKEN))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void anonymousIsUnauthorized() {
        webTestClient.get().uri("/admin/llm/connections").exchange().expectStatus().isUnauthorized();
    }
}
