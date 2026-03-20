package de.tum.in.www1.hephaestus.feature;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Separate integration test class for CONFIG feature flags.
 * <p>
 * {@code @TestPropertySource} only works on top-level test classes (not {@code @Nested}),
 * so CONFIG overrides need their own class to get a dedicated application context.
 */
@AutoConfigureWebTestClient
@TestPropertySource(properties = "hephaestus.features.flags.gitlab-workspace-creation=true")
@DisplayName("Feature flag controller — CONFIG flags integration")
class FeatureFlagConfigIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithUser
    @DisplayName("CONFIG flag set to true is reflected in the response")
    void configFlagEnabledReflectedInResponse() {
        webTestClient
            .get()
            .uri("/user/features")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.GITLAB_WORKSPACE_CREATION")
            .isEqualTo(true)
            .jsonPath("$.ADMIN")
            .isEqualTo(false);
    }
}
