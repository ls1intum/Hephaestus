package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult.LlmUsage;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class TerminalUsageTest extends BaseUnitTest {

    private static LlmUsage runner(int input, int output, int calls) {
        return new LlmUsage("m", input, output, 0, 0, 0, 0.0, calls);
    }

    @Test
    @DisplayName("a usable runner report is authoritative")
    void runnerReportWins() {
        TerminalUsage usage = TerminalUsage.resolve(runner(1000, 700, 6), new AgentJobLlmUsage(4, 900, 600, 0, 0, 0));

        assertThat(usage.verifiable()).isTrue();
        assertThat(usage.totalCalls()).isEqualTo(6);
        assertThat(usage.inputTokens()).isEqualTo(1000);
        assertThat(usage.outputTokens()).isEqualTo(700);
    }

    @Test
    @DisplayName("no runner report at all falls back to the proxy accumulators")
    void missingRunnerReportFallsBackToTheProxy() {
        TerminalUsage usage = TerminalUsage.resolve(null, new AgentJobLlmUsage(4, 900, 600, 30, 100, 0));

        assertThat(usage.verifiable()).isTrue();
        assertThat(usage.totalCalls()).isEqualTo(4);
        assertThat(usage.inputTokens()).isEqualTo(900);
        assertThat(usage.cacheReadTokens()).isEqualTo(100);
        assertThat(usage.reasoningTokens()).isEqualTo(30);
    }

    @Test
    @DisplayName("a runner report claiming calls but no tokens is not evidence — the proxy is")
    void runnerReportWithZeroTokensFallsBackToTheProxy() {
        TerminalUsage usage = TerminalUsage.resolve(runner(0, 0, 3), new AgentJobLlmUsage(4, 900, 600, 0, 0, 0));

        assertThat(usage.verifiable()).isTrue();
        assertThat(usage.totalCalls()).isEqualTo(4);
        assertThat(usage.inputTokens()).isEqualTo(900);
    }

    @Test
    @DisplayName("null token fields in the runner report do not read as spend")
    void runnerReportWithNullTokenFieldsFallsBackToTheProxy() {
        LlmUsage malformed = new LlmUsage("m", null, null, null, null, null, null, 2);

        TerminalUsage usage = TerminalUsage.resolve(malformed, new AgentJobLlmUsage(1, 10, 20, 0, 0, 0));

        assertThat(usage.verifiable()).isTrue();
        assertThat(usage.inputTokens()).isEqualTo(10);
    }

    @Test
    @DisplayName("neither record has tokens: zero spend, and say so as unverifiable")
    void nothingToBillIsReportedAsUnverifiable() {
        TerminalUsage usage = TerminalUsage.resolve(null, new AgentJobLlmUsage(0, 0, 0, 0, 0, 0));

        assertThat(usage.verifiable()).isFalse();
        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.totalCalls()).isZero();
    }

    @Test
    @DisplayName("an unverifiable attempt still keeps the reported call count as telemetry")
    void unverifiableAttemptKeepsTheReportedCallCount() {
        TerminalUsage usage = TerminalUsage.resolve(runner(0, 0, 3), null);

        assertThat(usage.verifiable()).isFalse();
        assertThat(usage.totalCalls()).isEqualTo(3);
        assertThat(usage.outputTokens()).isZero();
    }

    @Test
    @DisplayName("both records absent is unverifiable, not a crash")
    void bothRecordsAbsentIsUnverifiableNotACrash() {
        TerminalUsage usage = TerminalUsage.resolve(null, null);

        assertThat(usage.verifiable()).isFalse();
        assertThat(usage.totalCalls()).isZero();
    }

    @Nested
    @DisplayName("appendTo picks the append path from the evidence, not from the call site")
    class AppendPath {

        private final LlmUsageRecorder recorder = mock(LlmUsageRecorder.class);

        @ParameterizedTest(name = "verifiable={0} priced={1} -> billed={2}")
        @CsvSource({ "true, true, true", "true, false, false", "false, true, false", "false, false, false" })
        void billedOnlyWhenTheTokensAreRealAndAPriceWasResolved(boolean verifiable, boolean priced, boolean billed) {
            TerminalUsage usage = verifiable ? TerminalUsage.resolve(runner(1000, 700, 6), null) : TerminalUsage.none();
            LlmPriceSnapshot price = priced ? pricedInstance() : LlmPriceSnapshot.unpricedInstance();

            boolean wasBilled = usage.appendTo(recorder, 7L, jobFor(UUID.randomUUID(), 2), "gpt-5", price);

            assertThat(wasBilled).isEqualTo(billed);
            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            if (billed) {
                verify(recorder).record(eq(7L), sample.capture());
            } else {
                verify(recorder).recordUnverifiable(eq(7L), sample.capture());
            }
            assertThat(sample.getValue().sourceType()).isEqualTo(LlmUsageSourceType.AGENT_JOB);
            assertThat(sample.getValue().model()).isEqualTo("gpt-5");
        }

        @Test
        @DisplayName("the row is keyed by the job's own id and attempt, so a retry bills separately")
        void theRowIsKeyedByJobAndAttemptSoARetryBillsSeparately() {
            UUID jobId = UUID.randomUUID();

            TerminalUsage.resolve(runner(10, 20, 1), null).appendTo(
                recorder,
                7L,
                jobFor(jobId, 3),
                "gpt-5",
                pricedInstance()
            );

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(recorder).record(eq(7L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(jobId);
            assertThat(sample.getValue().sourceAttempt()).isEqualTo(3);
        }

        @Test
        @DisplayName("token counts survive onto an unpriced row")
        void unpricedRowKeepsItsTokenCounts() {
            TerminalUsage.resolve(runner(900, 600, 4), null).appendTo(
                recorder,
                7L,
                jobFor(UUID.randomUUID(), 0),
                "gpt-5",
                LlmPriceSnapshot.unpricedInstance()
            );

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(recorder).recordUnverifiable(eq(7L), sample.capture());
            assertThat(sample.getValue().inputTokens()).isEqualTo(900);
            assertThat(sample.getValue().totalCalls()).isEqualTo(4);
        }

        private AgentJob jobFor(UUID jobId, int attempt) {
            AgentJob job = new AgentJob();
            job.setId(jobId);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setRetryCount(attempt);
            return job;
        }

        private LlmPriceSnapshot pricedInstance() {
            return new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                PricingState.PRICED,
                1L,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
        }
    }
}
