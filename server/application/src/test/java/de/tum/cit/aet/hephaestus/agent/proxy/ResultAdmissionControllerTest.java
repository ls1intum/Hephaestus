package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.integration.core.signal.PracticeReviewRefusalMetrics;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ResultAdmissionControllerTest extends BaseUnitTest {

    @Mock
    private ResultAdmissionService service;

    @Mock
    private PublicReviewCompletionService completion;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private ResultAdmissionController controller;

    @BeforeEach
    void setUp() {
        controller = new ResultAdmissionController(service, new PracticeReviewRefusalMetrics(meterRegistry));
    }

    @Test
    void malformedRequestIsBadRequest() {
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.admit(mapper.createObjectNode(), new TestingAuthenticationToken("x", "x")));
    }

    /** An old runner, or any body that is not exactly the admission envelope, fails explicitly before admission. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"schemaVersion\":1,\"results\":[{\"practiceSlug\":\"a\",\"candidate\":{}}]}",
                "{\"schemaVersion\":1,\"observations\":[]}",
                "{\"results\":[{\"practiceSlug\":\"a\",\"candidate\":{}}]}",
                "{\"schemaVersion\":2,\"results\":[]}",
                "{\"schemaVersion\":2,\"results\":{}}",
                "{\"schemaVersion\":2,\"results\":[{\"practiceSlug\":\"b\",\"candidate\":{}},"
                        + "{\"practiceSlug\":\"a\",\"candidate\":{}}]}",
                "{\"schemaVersion\":2,\"results\":[{\"practiceSlug\":\"a\"}]}",
                "{\"schemaVersion\":2,\"results\":[{\"practiceSlug\":\"Not A Slug\",\"candidate\":{}}]}",
                "{\"schemaVersion\":2,\"results\":[{\"practiceSlug\":\"a\",\"candidate\":{},"
                        + "\"severity\":\"MAJOR\"}]}",
                "[]"
            })
    void shouldRefuseTheBodyAsAMalformedAdmissionWhenItIsNotTheVersionTwoEnvelope(String body) {
        UUID id = UUID.randomUUID();

        var request = mapper.readTree(body);

        assertThatThrownBy(() -> controller.admit(request, authentication(LlmUsageSourceType.AGENT_JOB, id)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).startsWith("malformed_admission");
                });
        verifyNoInteractions(service);
        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    /** The token is the one home of the attempt's identity: a body that names one is not an admission. */
    @ParameterizedTest
    @ValueSource(strings = {"jobId", "attempt", "workspaceId", "workerId"})
    void shouldTakeTheIdentityFromTheTokenWhenTheBodyNamesAnother(String key) {
        UUID id = UUID.randomUUID();
        ObjectNode forged = validRequest();
        forged.put(key, key.equals("jobId") ? UUID.randomUUID().toString() : "2");

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.admit(forged, authentication(LlmUsageSourceType.AGENT_JOB, id)));
        verifyNoInteractions(service);

        when(service.admit(any(), any())).thenReturn(mapper.createObjectNode());
        controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id));
        verify(service).admit(identity(id), validRequest());
    }

    @Test
    void agentJobCredentialAdmitsItsResults() {
        UUID id = UUID.randomUUID();
        var response = mapper.createObjectNode().put("admissionDigest", "sha256:digest");
        when(service.admit(eq(identity(id)), any())).thenReturn(response);

        var actual = controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id));

        assertThat(actual).isSameAs(response);
        // The whole envelope is admitted, so its digest covers every byte the runner sent.
        verify(service).admit(identity(id), validRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"did_not_read_the_diff", "incoherent_assessment", "member_ai_declined"})
    void aRefusedReviewIsAnsweredAsADecisionAndCounted(String reasonCode) {
        UUID id = UUID.randomUUID();
        when(service.admit(eq(identity(id)), any()))
                .thenThrow(new ResultsRefusedException(reasonCode, "The submitted results cannot be admitted"));

        assertThatThrownBy(() -> controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    // Not 5xx: a 5xx is what the sandbox repeats, and repeating puts the same question.
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).contains("cannot be admitted");
                });
        assertThat(meterRegistry
                        .counter("practice.review.refused", "phase", "execution", "reason", reasonCode)
                        .count())
                .isEqualTo(1d);
    }

    @Test
    void shouldAnswerAnInadmissibleSubmissionAsADecisionRatherThanAFault() {
        UUID id = UUID.randomUUID();
        when(service.admit(eq(identity(id)), any()))
                .thenThrow(new JobDeliveryException("Result cited unavailable evidence source"));

        assertThatThrownBy(() -> controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    // Deterministic for this submission, so a 5xx would only have the sandbox ask again.
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).contains("unavailable evidence source");
                });
        assertThat(meterRegistry
                        .counter(
                                "practice.review.refused",
                                "phase",
                                "execution",
                                "reason",
                                ResultAdmissionService.INADMISSIBLE_REASON_CODE)
                        .count())
                .isEqualTo(1d);
    }

    @Test
    void shouldAnswerAVerifierFailureAsAServerFaultTheRunnerRepeats() {
        UUID id = UUID.randomUUID();
        when(service.admit(eq(identity(id)), any()))
                .thenThrow(new IllegalStateException("Captured evidence digest mismatch"));

        assertStatus(
                HttpStatus.INTERNAL_SERVER_ERROR,
                () -> controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id)));
        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void unexpectedPrincipalCannotAdmitResults() {
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> controller.admit(validRequest(), new TestingAuthenticationToken("unexpected", "[REDACTED]")));
        verifyNoInteractions(service);
    }

    @Test
    void mentorCredentialCannotAdmitAgentResults() {
        UUID id = UUID.randomUUID();
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> controller.admit(validRequest(), authentication(LlmUsageSourceType.MENTOR_TURN, id)));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectStaleAttemptWithoutCountingRefusalWhenOwnershipChangesDuringAdmission() {
        UUID id = UUID.randomUUID();
        when(service.admit(eq(identity(id)), any())).thenThrow(new ResultAdmissionService.StaleAttemptException());

        assertStatus(
                HttpStatus.CONFLICT,
                () -> controller.admit(validRequest(), authentication(LlmUsageSourceType.AGENT_JOB, id)));
        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void refreshesHistoryOnlyForTheAuthenticatedAgentAttempt() {
        UUID id = UUID.randomUUID();
        ObjectNode answer = mapper.createObjectNode().put("schemaVersion", 1);
        when(service.publicFeedbackHistory(identity(id)))
                .thenReturn(answer)
                .thenThrow(new ResultAdmissionService.StaleAttemptException())
                .thenThrow(new ResultAdmissionService.PublicHistoryRefusedException("Not admitted"));
        assertThat(controller.publicFeedbackHistory(authentication(LlmUsageSourceType.AGENT_JOB, id)))
                .isSameAs(answer);
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> controller.publicFeedbackHistory(authentication(LlmUsageSourceType.MENTOR_TURN, id)));
        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> controller.publicFeedbackHistory(new TestingAuthenticationToken("unexpected", "[REDACTED]")));
        assertStatus(
                HttpStatus.CONFLICT,
                () -> controller.publicFeedbackHistory(authentication(LlmUsageSourceType.AGENT_JOB, id)));
        assertStatus(
                HttpStatus.UNPROCESSABLE_CONTENT,
                () -> controller.publicFeedbackHistory(authentication(LlmUsageSourceType.AGENT_JOB, id)));
    }

    private static ResultAdmissionService.AdmissionIdentity identity(UUID id) {
        return new ResultAdmissionService.AdmissionIdentity(id, 1L, 0, "worker-1");
    }

    /** The version 2 envelope: one entry per practice, sorted by slug. */
    private ObjectNode validRequest() {
        var request = mapper.createObjectNode().put("schemaVersion", 2);
        var results = request.putArray("results");
        results.addObject().put("practiceSlug", "explains-why").putObject("candidate");
        results.addObject().put("practiceSlug", "small-changes").putObject("candidate");
        return request;
    }

    private static TestingAuthenticationToken authentication(LlmUsageSourceType sourceType, UUID sourceId) {
        ProxyRouting routing = new ProxyRouting(
                "agent",
                "responses",
                "http://model",
                null,
                null,
                null,
                1L,
                new ProxyRouting.BilledAttempt(sourceType, sourceId, 0, BigDecimal.ZERO, "worker-1"),
                null,
                null);
        return new TestingAuthenticationToken(routing, "[REDACTED]");
    }

    private static void assertStatus(HttpStatus status, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }
}
