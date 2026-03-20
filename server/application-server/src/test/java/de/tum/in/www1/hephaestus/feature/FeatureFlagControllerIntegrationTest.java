package de.tum.in.www1.hephaestus.feature;

import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Feature flag controller integration")
class FeatureFlagControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Nested
    @DisplayName("GET /user/features")
    class GetUserFeatures {

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void requiresAuthentication() {
            webTestClient
                .get()
                .uri("/user/features")
                .headers(TestAuthUtils.withCurrentUserOrNone())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithAdminUser
        @DisplayName("admin user has admin and inherited role flags enabled")
        void adminUserHasAdminFlags() {
            webTestClient
                .get()
                .uri("/user/features")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.ADMIN")
                .isEqualTo(true)
                .jsonPath("$.MENTOR_ACCESS")
                .isEqualTo(false) // admin annotation only sets "admin" authority
                .jsonPath("$.GITLAB_WORKSPACE_CREATION")
                .isEqualTo(false); // CONFIG flags default to false
        }

        @Test
        @WithMentorUser
        @DisplayName("mentor user has mentor_access flag enabled")
        void mentorUserHasMentorFlags() {
            webTestClient
                .get()
                .uri("/user/features")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.MENTOR_ACCESS")
                .isEqualTo(true)
                .jsonPath("$.ADMIN")
                .isEqualTo(false)
                .jsonPath("$.NOTIFICATION_ACCESS")
                .isEqualTo(false);
        }

        @Test
        @WithUser
        @DisplayName("regular user has no role flags enabled")
        void regularUserHasNoRoleFlags() {
            webTestClient
                .get()
                .uri("/user/features")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.MENTOR_ACCESS")
                .isEqualTo(false)
                .jsonPath("$.ADMIN")
                .isEqualTo(false)
                .jsonPath("$.RUN_PRACTICE_REVIEW")
                .isEqualTo(false)
                .jsonPath("$.RUN_AUTOMATIC_DETECTION")
                .isEqualTo(false)
                .jsonPath("$.NOTIFICATION_ACCESS")
                .isEqualTo(false);
        }

        @Test
        @WithUser
        @DisplayName("response contains all expected flag fields")
        void responseContainsAllFlags() {
            webTestClient
                .get()
                .uri("/user/features")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(FeatureFlag.values().length)
                .jsonPath("$.MENTOR_ACCESS")
                .exists()
                .jsonPath("$.NOTIFICATION_ACCESS")
                .exists()
                .jsonPath("$.RUN_AUTOMATIC_DETECTION")
                .exists()
                .jsonPath("$.RUN_PRACTICE_REVIEW")
                .exists()
                .jsonPath("$.ADMIN")
                .exists()
                .jsonPath("$.PRACTICE_REVIEW_FOR_ALL")
                .exists()
                .jsonPath("$.DETECTION_FOR_ALL")
                .exists()
                .jsonPath("$.GITLAB_WORKSPACE_CREATION")
                .exists();
        }
    }
}
