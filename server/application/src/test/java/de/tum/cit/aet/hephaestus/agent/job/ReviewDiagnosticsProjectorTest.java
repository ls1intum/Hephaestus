package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReviewDiagnosticsProjectorTest extends BaseUnitTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldAggregateObservedClosedTurnsAndKeepRecoveredFailuresWhenTheLatestCallSucceeded() {
        AgentJob job = job("""
            {"runnerDebug":{"turns":[
              {"phase":"practice","calls":2,"durationMs":50,"modelFailures":{"HTTP_ERROR":1},
               "finalModelFailure":{"kind":"HTTP_ERROR","source":"ADAPTER","phase":"request","status":503,"at":1760000000000},
               "modelCallTiming":{"timedCalls":2,"respondedCalls":1,"elapsedMs":30,"failedElapsedMs":10,"maxElapsedMs":20,"responseMs":5}},
              {"phase":"practice","calls":1,"durationMs":0,"modelFailures":{},"finalModelFailure":null,
               "modelCallTiming":{"timedCalls":0,"respondedCalls":0,"elapsedMs":0,"failedElapsedMs":0,"maxElapsedMs":0,"responseMs":0}},
              {"phase":"public-review","calls":1,"durationMs":10,"modelFailures":{"HTTP_ERROR":1},
               "finalModelFailure":{"kind":"HTTP_ERROR","source":"ADAPTER","phase":"request","status":503,"at":1760000000000,"retryEndedUnsuccessfully":true},
               "modelCallTiming":{"timedCalls":1,"respondedCalls":0,"elapsedMs":10,"failedElapsedMs":10,"maxElapsedMs":10,"responseMs":0}},
              {"phase":"private-feedback","calls":1,"durationMs":0,"modelFailures":{},
               "modelCallTiming":{"timedCalls":1,"respondedCalls":1,"elapsedMs":0,"failedElapsedMs":0,"maxElapsedMs":0,"responseMs":0}},
              {"label":"review composition","calls":0,"durationMs":0}
            ]}}
            """);
        var diagnostics = Objects.requireNonNull(ReviewDiagnosticsProjector.from(job));
        assertThat(diagnostics.phases())
                .extracting(ReviewDiagnosticsDTO.ReviewPhaseDiagnosticsDTO::phase)
                .containsExactly(ReviewDiagnosticsDTO.ReviewPhaseDTO.values());
        var practice = diagnostics.phases().get(0);
        assertThat(practice.recordedTurns()).isEqualTo(2);
        assertThat(practice.assistantCalls()).isEqualTo(3L);
        assertThat(practice.elapsedMs()).isEqualTo(50L);
        assertThat(practice.modelCallTiming())
                .isEqualTo(new ReviewDiagnosticsDTO.ModelCallTimingDTO(2, 1, 30L, 10L, 20L, 5L));
        assertThat(practice.modelFailures())
                .containsExactly(new ReviewDiagnosticsDTO.ModelFailureCountDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.HTTP_ERROR, 1L));
        assertThat(practice.finalModelFailure()).isNull();
        var review = diagnostics.phases().get(1);
        assertThat(review.finalModelFailure())
                .isEqualTo(new ReviewDiagnosticsDTO.TerminalModelFailureDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.HTTP_ERROR,
                        ReviewDiagnosticsDTO.ModelFailureSourceDTO.ADAPTER,
                        ReviewDiagnosticsDTO.ModelFailurePhaseDTO.REQUEST,
                        503,
                        1_760_000_000_000L,
                        true));
        assertThat(Objects.requireNonNull(review.modelCallTiming()).responseMs())
                .isNull();
        assertThat(diagnostics.phases().get(2).modelCallTiming())
                .isEqualTo(new ReviewDiagnosticsDTO.ModelCallTimingDTO(1, 1, 0L, 0L, 0L, 0L));
        var legacy = diagnostics.phases().get(3);
        assertThat(legacy.recordedTurns()).isEqualTo(1);
        assertThat(legacy.elapsedMs()).isZero();
        assertThat(legacy.modelCallTiming()).isNull();
        assertThat(legacy.modelFailures()).isNull();
    }

    @Test
    void shouldKeepPartialAndUnrecordedSpansUnknownInsteadOfReportingZeroElapsed() {
        var diagnostics = Objects.requireNonNull(ReviewDiagnosticsProjector.from(job("""
            {"runnerDebug":{"turns":[
              {"phase":"practice","calls":1,"durationMs":12},
              {"phase":"practice","calls":0},
              {"phase":"private-feedback","calls":1,"durationMs":0,"modelFailures":{"UNKNOWN":1},
               "finalModelFailure":{"kind":"UNKNOWN","source":"INVALID","phase":"request","status":503,"at":1},
               "modelCallTiming":{"timedCalls":0,"respondedCalls":0,"elapsedMs":0,"failedElapsedMs":0,"maxElapsedMs":0,"responseMs":0}}
            ]}}
            """)));
        var practice = diagnostics.phases().get(0);
        assertThat(practice.assistantCalls()).isEqualTo(1L);
        assertThat(practice.elapsedMs()).isNull();
        assertThat(practice.modelCallTiming()).isNull();
        var unreached = diagnostics.phases().get(1);
        assertThat(unreached.recordedTurns()).isZero();
        assertThat(unreached.assistantCalls()).isNull();
        assertThat(unreached.elapsedMs()).isNull();
        var privateFeedback = diagnostics.phases().get(2);
        assertThat(privateFeedback.modelCallTiming())
                .isEqualTo(new ReviewDiagnosticsDTO.ModelCallTimingDTO(0, 0, null, null, null, null));
        assertThat(privateFeedback.finalModelFailure())
                .isEqualTo(new ReviewDiagnosticsDTO.TerminalModelFailureDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.UNKNOWN,
                        ReviewDiagnosticsDTO.ModelFailureSourceDTO.INVALID,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void shouldRejectMalformedUnsafeAndOverflowedFactsWithoutCopyingHostileFields() {
        var diagnostics = Objects.requireNonNull(ReviewDiagnosticsProjector.from(job("""
            {"rawOutput":"secret","runnerDebug":{"publicHistory":{"secret":"private"},"turns":[
              {"phase":"practice","calls":1.5,"durationMs":9007199254740991,
               "modelFailures":{"HTTP_ERROR":-1,"arbitrary-secret":999},
               "finalModelFailure":{"kind":"HTTP_ERROR","source":"ADAPTER","phase":"secret","status":600,"at":8640000000000001,"retryEndedUnsuccessfully":"true","reason":"secret"},
               "modelCallTiming":{"timedCalls":1,"respondedCalls":1,"elapsedMs":1,"failedElapsedMs":0,"maxElapsedMs":1,"responseMs":2}},
              {"phase":"practice","calls":9007199254740992,"durationMs":1,"modelFailures":{}},
              {"phase":"public-review","calls":1000000,"durationMs":-1,"modelFailures":{"ABORTED":1000000}},
              {"phase":"public-review","calls":1,"durationMs":"0","modelFailures":{"ABORTED":1},"finalModelFailure":{"kind":"secret"}},
              {"phase":"secret-phase","label":"practice","toolCalls":{"secret":1},"calls":0,"durationMs":0},
              {"phase":"private-feedback","calls":1,"durationMs":0,
               "modelCallTiming":{"timedCalls":1,"respondedCalls":1,"elapsedMs":9007199254740991,"failedElapsedMs":9007199254740991,"maxElapsedMs":9007199254740991,"responseMs":9007199254740991}},
              {"phase":"private-feedback","calls":1,"durationMs":0,
               "modelCallTiming":{"timedCalls":1,"respondedCalls":1,"elapsedMs":1,"failedElapsedMs":1,"maxElapsedMs":1,"responseMs":1}}
            ]}}
            """)));
        var practice = diagnostics.phases().get(0);
        assertThat(practice.assistantCalls()).isNull();
        assertThat(practice.elapsedMs()).isNull();
        assertThat(practice.modelCallTiming()).isNull();
        assertThat(practice.modelFailures())
                .containsExactly(new ReviewDiagnosticsDTO.ModelFailureCountDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.HTTP_ERROR, null));
        // The latest closed turn has no terminal failure, so an earlier one is not presented as current.
        assertThat(practice.finalModelFailure()).isNull();
        var review = diagnostics.phases().get(1);
        assertThat(review.assistantCalls()).isNull();
        assertThat(review.elapsedMs()).isNull();
        assertThat(review.modelFailures())
                .containsExactly(new ReviewDiagnosticsDTO.ModelFailureCountDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.ABORTED, null));
        assertThat(review.finalModelFailure()).isNull();
        assertThat(diagnostics.phases().get(3).recordedTurns()).isEqualTo(1);
        assertThat(diagnostics.phases().get(2).modelCallTiming())
                .isEqualTo(new ReviewDiagnosticsDTO.ModelCallTimingDTO(2, 2, null, null, 9_007_199_254_740_991L, null));
        assertThat(MAPPER.writeValueAsString(diagnostics))
                .doesNotContain("secret", "rawOutput", "publicHistory", "label", "toolCalls");
    }

    @Test
    void shouldValidateEachSafeTerminalFailureFieldAndPartialTimingIndependentlyOfFreeText() {
        var diagnostics = Objects.requireNonNull(ReviewDiagnosticsProjector.from(job("""
            {"runnerDebug":{"turns":[
              {"phase":"public-review","calls":1,"durationMs":0,"modelFailures":{},
               "modelCallTiming":{"timedCalls":1,"elapsedMs":0},
               "finalModelFailure":{"kind":"HTTP_ERROR","source":"ADAPTER","phase":"secret","status":399,"at":8640000000000001,"retryEndedUnsuccessfully":"true","reason":"secret"}},
              {"phase":"private-feedback","calls":1,"durationMs":0,
               "finalModelFailure":{"kind":"ABORTED","source":"ADAPTER","phase":"response_body","status":503,"at":0,"retryEndedUnsuccessfully":false}}
            ]}}
            """)));
        var review = diagnostics.phases().get(1);
        assertThat(review.modelCallTiming()).isNull();
        assertThat(review.finalModelFailure())
                .isEqualTo(new ReviewDiagnosticsDTO.TerminalModelFailureDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.HTTP_ERROR,
                        ReviewDiagnosticsDTO.ModelFailureSourceDTO.ADAPTER,
                        null,
                        null,
                        null,
                        null));
        assertThat(diagnostics.phases().get(2).finalModelFailure())
                .isEqualTo(new ReviewDiagnosticsDTO.TerminalModelFailureDTO(
                        ReviewDiagnosticsDTO.ModelFailureKindDTO.ABORTED,
                        ReviewDiagnosticsDTO.ModelFailureSourceDTO.ADAPTER,
                        ReviewDiagnosticsDTO.ModelFailurePhaseDTO.RESPONSE_BODY,
                        null,
                        0L,
                        false));
    }

    @Test
    void shouldLeaveUnavailableOutputAbsentForLegacyCancelledCrashedAndRetriedJobs() {
        AgentJob job = job("{}");
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        job.setOutput(MAPPER.readTree("{\"runnerDebug\":{\"turns\":[{}]}}"));
        job.setRetryCount(1);
        job.setStatus(AgentJobStatus.QUEUED);
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        job.setStatus(AgentJobStatus.RUNNING);
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        job = new AgentJob();
        for (var status :
                new AgentJobStatus[] {AgentJobStatus.CANCELLED, AgentJobStatus.FAILED, AgentJobStatus.TIMED_OUT}) {
            job.setStatus(status);
            assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        }
        job.setOutput(MAPPER.readTree("{\"runnerDebug\":{\"turns\":[false]}}"));
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        var turns = MAPPER.createArrayNode();
        for (int i = 0; i < 1001; i++) turns.addObject();
        var output = MAPPER.createObjectNode();
        output.putObject("runnerDebug").set("turns", turns);
        job.setOutput(output);
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
        job.setOutput(MAPPER.readTree("{\"runnerDebug\":{\"turns\":[]}}"));
        var empty = Objects.requireNonNull(ReviewDiagnosticsProjector.from(job));
        assertThat(empty.phases()).allSatisfy(phase -> {
            assertThat(phase.recordedTurns()).isZero();
            assertThat(phase.elapsedMs()).isNull();
        });
        job = new AgentJob();
        job.setStatus(AgentJobStatus.COMPLETED);
        assertThat(ReviewDiagnosticsProjector.from(job)).isNull();
    }

    private static AgentJob job(String output) {
        AgentJob job = new AgentJob();
        job.setStatus(AgentJobStatus.COMPLETED);
        job.setOutput(MAPPER.readTree(output));
        return job;
    }
}
