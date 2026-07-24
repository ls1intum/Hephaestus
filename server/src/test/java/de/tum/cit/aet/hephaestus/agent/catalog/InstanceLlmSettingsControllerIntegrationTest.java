package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Instance-admin governance settings ({@code /admin/llm/settings}, #1368): app_admin gate, partial
 * update semantics, and the defaults returned before the singleton row is ever written (ddl-auto test
 * schema has no Liquibase-seeded row — {@link InstanceLlmSettingsService#get} must fall back cleanly).
 */
class InstanceLlmSettingsControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String ADMIN_TOKEN = "mock-jwt-token-for-admin-user";
    private static final String MENTOR_TOKEN = "mock-jwt-token-for-mentor-user";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void getReturnsDefaultsBeforeAnyUpdate() {
        InstanceLlmSettingsDTO settings = webTestClient
            .get()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceLlmSettingsDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(settings).isNotNull();
        assertThat(settings.allowWorkspaceConnections()).isTrue();
    }

    @Test
    void appAdminCanUpdateSettingsAndGetReflectsThePatch() {
        var request = new UpdateInstanceLlmSettingsRequestDTO("api.openai.com\napi.anthropic.com", false);

        InstanceLlmSettingsDTO updated = webTestClient
            .put()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceLlmSettingsDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.allowWorkspaceConnections()).isFalse();
        assertThat(updated.allowedEgressHosts()).contains("api.openai.com");

        InstanceLlmSettingsDTO fetched = webTestClient
            .get()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceLlmSettingsDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.allowWorkspaceConnections()).isFalse();
    }

    @Test
    void anAbsentFieldOnPatchLeavesItsCurrentValueUntouched() {
        webTestClient
            .put()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new UpdateInstanceLlmSettingsRequestDTO("api.openai.com", false))
            .exchange()
            .expectStatus()
            .isOk();

        InstanceLlmSettingsDTO fetched = webTestClient
            .put()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(ADMIN_TOKEN))
            .contentType(MediaType.APPLICATION_JSON)
            // Only touch the egress hosts this time — allowWorkspaceConnections=false must survive.
            .bodyValue(new UpdateInstanceLlmSettingsRequestDTO("api.openai.com\napi.anthropic.com", null))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceLlmSettingsDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(fetched).isNotNull();
        assertThat(fetched.allowWorkspaceConnections()).isFalse();
        assertThat(fetched.allowedEgressHosts()).contains("api.anthropic.com");
    }

    @Test
    void nonAdminIsForbidden() {
        webTestClient
            .get()
            .uri("/admin/llm/settings")
            .headers(h -> h.setBearerAuth(MENTOR_TOKEN))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void anonymousIsUnauthorized() {
        webTestClient.get().uri("/admin/llm/settings").exchange().expectStatus().isUnauthorized();
    }
}
