package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void shouldReportAnAttemptConflictWithoutAdmittingAnotherPayload() {
        var admission = mock(ResultAdmissionService.class);
        var id = UUID.randomUUID();
        var request = new AdmissionRequestDTO(3, UUID.randomUUID(), id, 0, List.of());
        var routing = new ProxyRouting(
                "review-worker",
                "openai-responses",
                "https://model.example",
                null,
                null,
                null,
                1L,
                new ProxyRouting.BilledAttempt(UsageSourceType.REVIEW_JOB, id, 0, BigDecimal.ZERO, "worker"),
                null,
                null);
        when(admission.admit(any(), any())).thenThrow(new ResultAdmissionService.StaleAttemptException());
        assertThatThrownBy(() -> new ResultAdmissionController(admission)
                        .admitResults(request, new TestingAuthenticationToken(routing, "credential")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));
    }
}
