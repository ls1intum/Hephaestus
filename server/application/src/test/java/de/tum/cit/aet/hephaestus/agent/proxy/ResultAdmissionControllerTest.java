package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.JobJwt;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class ResultAdmissionControllerTest extends BaseUnitTest {
    @Test
    void shouldRejectCredentialsThatDoNotIdentifyAReviewWorker() {
        var controller = new ResultAdmissionController(mock(ResultAdmissionService.class));
        assertThatThrownBy(() ->
                        controller.readPublicFeedbackHistory(new TestingAuthenticationToken("mentor", "credential")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void shouldReportAnAttemptConflictWithoutAdmittingAnotherPayload() {
        var admission = mock(ResultAdmissionService.class);
        var id = UUID.randomUUID();
        var request = new AdmissionRequestDTO(3, UUID.randomUUID(), id, 0, List.of());
        var now = Instant.now();
        var jwt = new JobJwt(id, 1L, 0, Set.of("llm_proxy"), "test", now, now.plusSeconds(60));
        var identity = new ProxyIdentity(jwt, "worker", null);
        var authentication = new JobTokenAuthentication(identity, jwt.scopes());
        when(admission.admit(new ResultAdmissionService.AdmissionIdentity(id, 1L, 0, "worker"), request))
                .thenThrow(new ResultAdmissionService.StaleAttemptException());
        assertThatThrownBy(() -> new ResultAdmissionController(admission).admitResults(request, authentication))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
