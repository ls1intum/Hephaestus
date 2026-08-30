package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class WorkerTokenExchangeIntegrationTest extends BaseIntegrationTest {

    private static final String REGISTRATION_TOKEN = "integration-secret";

    @Autowired
    private WorkerJwtIssuer issuer;

    @Autowired
    private WorkerJwtVerifier verifier;

    @Test
    void shouldReturnVerifiableJwtWhenRegistrationTokenIsValid() throws Exception {
        WorkerTokenProperties properties = new WorkerTokenProperties(
                "hephaestus-hub", "hephaestus-worker", Duration.ofHours(1), REGISTRATION_TOKEN, List.of(), null);
        WorkerTokenExchangeController controller =
                new WorkerTokenExchangeController(issuer, properties, new SimpleMeterRegistry());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<?> response = controller.exchange(
                new WorkerTokenExchangeController.ExchangeRequest("worker-it", REGISTRATION_TOKEN), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .isInstanceOfSatisfying(WorkerTokenExchangeController.ExchangeResponse.class, body -> {
                    WorkerSessionJwt jwt = (WorkerSessionJwt) verifier.verify(body.token());
                    assertThat(jwt.workerId()).isEqualTo("worker-it");
                    assertThat(jwt.jti()).isNotBlank();
                    assertThat(body.expiresAt()).isNotNull();
                });
    }
}
