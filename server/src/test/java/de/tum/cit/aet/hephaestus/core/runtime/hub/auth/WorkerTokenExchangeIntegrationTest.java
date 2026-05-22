package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Status-code-only assertions are unit-level; this IT proves end-to-end sig/verify round-trip through the full context. */
@DisplayName("Worker token exchange — integration")
class WorkerTokenExchangeIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void registration(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.worker.hub.token.registration-token", () -> "integration-secret");
    }

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    WorkerJwtVerifier verifier;

    @Test
    void validRegistrationTokenReturnsVerifiableJwt() throws Exception {
        WorkerTokenExchangeController.ExchangeResponse response = webTestClient
            .post()
            .uri("/api/workers/exchange")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new WorkerTokenExchangeController.ExchangeRequest("worker-it", "integration-secret"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkerTokenExchangeController.ExchangeResponse.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.expiresAt()).isNotNull();
        WorkerJwt jwt = verifier.verify(response.token());
        assertThat(jwt.workerId()).isEqualTo("worker-it");
        assertThat(jwt.jti()).isNotBlank();
    }
}
