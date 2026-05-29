package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.security.ImpersonationGuard;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the {@link ImpersonationGuard} is actually wired onto the resource-server chain (it was
 * dead code — an unregistered filter — so impersonation granted full write access as the target).
 *
 * <p>This test FAILS if the guard is not registered: an {@code act}-bearing token would reach the
 * {@code POST /auth/logout} controller and return 204 instead of the guard's 403.
 */
class ImpersonationGuardIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void writeUnderImpersonationTokenIsBlockedWith403ProblemDetail() {
        webTestClient
            .post()
            .uri("/auth/logout")
            .headers(headers -> headers.setBearerAuth(TestSecurityConfig.IMPERSONATION_TOKEN))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(403)
            .jsonPath("$.code")
            .isEqualTo("impersonation_read_only");
    }

    @Test
    void writeUnderImpersonationTokenIsAllowedWithConfirmationHeader() {
        // With the confirmation header the guard MUST let the write through to the controller. We
        // assert only that the guard did not produce its own 403 — the downstream controller outcome
        // (here logout) is irrelevant to the guard's contract.
        webTestClient
            .post()
            .uri("/auth/logout")
            .headers(headers -> {
                headers.setBearerAuth(TestSecurityConfig.IMPERSONATION_TOKEN);
                headers.set(ImpersonationGuard.ALLOW_WRITES_HEADER, "true");
            })
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403));
    }

    @Test
    void normalWriteWithoutActClaimIsAllowed() {
        // A normal (non-act) token must never be blocked by the impersonation guard.
        webTestClient
            .post()
            .uri("/auth/logout")
            .headers(headers -> headers.setBearerAuth(TestSecurityConfig.NUMERIC_SUBJECT_TOKEN))
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403));
    }

    @Test
    void safeMethodUnderImpersonationTokenIsAllowed() {
        // GET is always allowed even under impersonation ("see what they see"). /user requires auth;
        // the impersonated account (id 1) need not exist for the guard to pass it through — the guard
        // only blocks non-safe methods. Any non-403 outcome proves the guard did not block the GET.
        webTestClient
            .get()
            .uri("/auth/error")
            .headers(headers -> headers.setBearerAuth(TestSecurityConfig.IMPERSONATION_TOKEN))
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(403));
    }
}
