package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LlmUsageRecorderTest extends BaseUnitTest {

    private static LlmUsageInsert captured(AtomicReference<LlmUsageInsert> event) {
        LlmUsageInsert captured = event.get();
        assertThat(captured).isNotNull();
        return captured;
    }

    private static LlmUsageRecorder.LlmUsageSample sample(LlmPriceSnapshot price, int attempt) {
        return new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.PULL_REQUEST_REVIEW,
                LlmUsageSourceType.AGENT_JOB,
                UUID.randomUUID(),
                attempt,
                "authoritative-model",
                1_000_000,
                2_000_000,
                500_000,
                250_000,
                900_000,
                1,
                price,
                UsageProvenance.RUNNER,
                Instant.now());
    }

    private static LlmPriceSnapshot priced() {
        return new LlmPriceSnapshot(
                FundingSource.WORKSPACE,
                PricingState.PRICED,
                null,
                42L,
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("4"));
    }

    @Test
    void writesFrozenPriceAndAttemptWithoutConsultingMutableCatalog() {
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmUsageEventRepository repository = mock(LlmUsageEventRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("insertIfAbsent")) {
                event.set(invocation.getArgument(0));
                return 1;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        LlmUsageRecorder recorder = recorder(repository);

        recorder.record(7L, sample(priced(), 3));

        assertThat(captured(event).sourceType()).isEqualTo("AGENT_JOB");
        assertThat(captured(event).sourceAttempt()).isEqualTo(3);
        assertThat(captured(event).model()).isEqualTo("authoritative-model");
        // 1*1 + 2*2 + .5*3 + .25*4 = 7.5. reasoning is output telemetry, not a second charge.
        assertThat(captured(event).costUsd()).isEqualByComparingTo("7.500000");
        assertThat(captured(event).appliedWorkspaceModelId()).isEqualTo(42L);
        assertThat(captured(event).appliedPer1mInputUsd()).isEqualTo(new BigDecimal("1"));
    }

    /**
     * The idempotency branch has an observable consequence, and this is it: a redelivered attempt must
     * not re-fire the recorder's side effects. Asserting only "does not throw" would pass with the
     * whole {@code inserted == 0} branch deleted. (Its integration twin proves the UNIQUE constraint
     * itself; here the concern is what the recorder does with the zero it gets back.)
     */
    @Test
    void aDuplicateSourceAttemptFiresNoSecondSideEffect() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger inserts = new AtomicInteger();
        LlmUsageEventRepository repository = mock(LlmUsageEventRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("insertIfAbsent")) {
                // First write lands; PostgreSQL's ON CONFLICT DO NOTHING absorbs the replay.
                return inserts.incrementAndGet() == 1 ? 1 : 0;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        LlmUsageRecorder recorder = new LlmUsageRecorder(repository, mock(LlmBudgetService.class), registry);
        LlmUsageRecorder.LlmUsageSample redelivered = sample(priced(), 2);

        recorder.recordUnverifiable(7L, redelivered);
        recorder.recordUnverifiable(7L, redelivered);

        assertThat(inserts.get())
                .as("both attempts reach the ledger's conflict guard")
                .isEqualTo(2);
        assertThat(registry.counter("llm.usage.unverifiable").count())
                .as("but only the attempt that actually inserted is counted")
                .isEqualTo(1.0);
    }

    @Test
    void unexpectedLedgerFailurePropagatesToRollBackSourceResult() {
        LlmUsageEventRepository repository = mock(LlmUsageEventRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("insertIfAbsent")) {
                throw new IllegalStateException("database unavailable");
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        LlmUsageRecorder recorder = recorder(repository);

        assertThatThrownBy(() -> recorder.record(7L, sample(priced(), 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void unverifiableAttemptKeepsFrozenProvenanceButStoresNoCost() {
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmUsageEventRepository repository = mock(LlmUsageEventRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("insertIfAbsent")) {
                event.set(invocation.getArgument(0));
                return 1;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        recorder(repository).recordUnverifiable(7L, sample(priced(), 4));

        assertThat(captured(event).sourceAttempt()).isEqualTo(4);
        assertThat(captured(event).costUsd()).isNull();
        assertThat(captured(event).pricingState()).isEqualTo("UNPRICED");
        assertThat(captured(event).appliedWorkspaceModelId()).isEqualTo(42L);
    }

    /**
     * A PRICED snapshot missing a rate the sample actually needs is a hole in the frozen price, and
     * pricing it anyway would silently under-bill: the un-priced bucket would contribute zero and the
     * row would read as an exact, cheap cost. It is downgraded to UNPRICED — no cost, and counted, so
     * the month reads "unverifiable" rather than "nearly free".
     */
    @Test
    void aPricedSnapshotMissingARateTheSampleNeedsIsDowngradedToUnpriced() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmPriceSnapshot missingOutputRate = new LlmPriceSnapshot(
                FundingSource.WORKSPACE,
                PricingState.PRICED,
                null,
                42L,
                new BigDecimal("1"),
                null, // output rate absent, and the sample below reports output tokens
                new BigDecimal("3"),
                new BigDecimal("4"));

        recorderWith(registry, capturing(event)).record(7L, sample(missingOutputRate, 1));

        assertThat(captured(event).pricingState()).isEqualTo("UNPRICED");
        assertThat(captured(event).costUsd()).isNull();
        assertThat(captured(event).appliedPer1mInputUsd()).isEqualByComparingTo("1");
        assertThat(registry.counter("llm.usage.uncosted").count()).isEqualTo(1.0);
    }

    @Test
    void aCostTooSmallForTheColumnIsRoundedUpAndCounted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        // One input token at a hundredth of a micro-dollar per million: positive, but far below the
        // column's six decimal places.
        LlmPriceSnapshot tinyRate = pricedWithInputRate(new BigDecimal("0.00000001"));

        recorderWith(registry, capturing(event)).record(7L, sampleOfInputTokensOnly(tinyRate, 1));

        assertThat(captured(event).costUsd())
                .as("a paid call must stay distinguishable from a free one")
                .isEqualByComparingTo("0.000001");
        assertThat(registry.counter("llm.usage.cost.clamped", "direction", "up_to_minimum")
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void aCostTooLargeForTheWireIsCappedAndCounted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        // 1e12 input tokens at $1000/1M = $1,000,000,000 — at the ceiling that survives binary64.
        LlmPriceSnapshot bigRate = pricedWithInputRate(new BigDecimal("1000"));

        recorderWith(registry, capturing(event)).record(7L, sampleOfInputTokensOnly(bigRate, 1_000_000_000_000L));

        assertThat(captured(event).costUsd()).isEqualByComparingTo("999999999.999999");
        assertThat(registry.counter("llm.usage.cost.clamped", "direction", "down_to_maximum")
                        .count())
                .isEqualTo(1.0);
    }

    /**
     * Token counters are read off provider payloads and off a row the proxy increments concurrently, so
     * a negative or zero value is reachable without any of our code being wrong. The ledger's columns
     * are unsigned in meaning: a negative token count would subtract from a month's totals, and a zero
     * call count would make a real call invisible in the per-call rollup.
     */
    @Test
    void nonsensicalCountsAreClampedBeforeTheyReachTheLedger() {
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmUsageRecorder.LlmUsageSample negative = new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.PULL_REQUEST_REVIEW,
                LlmUsageSourceType.AGENT_JOB,
                UUID.randomUUID(),
                -1,
                "authoritative-model",
                -5,
                -6,
                -7,
                -8,
                -9,
                0,
                priced(),
                UsageProvenance.RUNNER,
                Instant.now());

        recorderWith(new SimpleMeterRegistry(), capturing(event)).record(7L, negative);

        assertThat(captured(event).sourceAttempt()).isZero();
        assertThat(captured(event).inputTokens()).isZero();
        assertThat(captured(event).outputTokens()).isZero();
        assertThat(captured(event).cacheReadTokens()).isZero();
        assertThat(captured(event).cacheWriteTokens()).isZero();
        assertThat(captured(event).reasoningTokens()).isZero();
        assertThat(captured(event).totalCalls())
                .as("a recorded event is at least one call")
                .isEqualTo(1);
    }

    /**
     * Instance-funded spend schedules a post-commit budget alert, and there is nothing to hang it on
     * outside a transaction. Failing loudly is the point: recording money outside the source result's
     * transaction would let the result roll back while the charge stayed.
     */
    @Test
    void instanceFundedSpendOutsideATransactionIsRefusedRatherThanRecorded() {
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmUsageRecorder recorder = recorderWith(new SimpleMeterRegistry(), capturing(event));

        assertThatThrownBy(() -> recorder.record(7L, sampleOfInputTokensOnly(instancePriced(), 1_000_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source result transaction");
    }

    /**
     * The alert is a courtesy WARN after the money is already committed, so a failure in it must not
     * unwind the commit. It is counted instead — {@code llm.budget.alert.failure} is the only way an
     * operator learns the crossing notice never went out.
     */
    @Test
    void aFailingPostCommitBudgetAlertIsCountedRatherThanPropagated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<LlmUsageInsert> event = new AtomicReference<>();
        LlmBudgetService budgetService = mock(LlmBudgetService.class);
        when(budgetService.headroom(7L)).thenThrow(new IllegalStateException("budget read failed"));
        LlmUsageRecorder recorder = new LlmUsageRecorder(capturing(event), budgetService, registry);

        TransactionSynchronizationManager.initSynchronization();
        try {
            recorder.record(7L, sampleOfInputTokensOnly(instancePriced(), 1_000_000));
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            assertThatCode(() -> synchronizations.get(0).afterCommit()).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(captured(event).costUsd())
                .as("the charge itself still landed")
                .isEqualByComparingTo("1.000000");
        assertThat(registry.counter("llm.budget.alert.failure").count()).isEqualTo(1.0);
    }

    private static LlmPriceSnapshot instancePriced() {
        return new LlmPriceSnapshot(
                FundingSource.INSTANCE, PricingState.PRICED, 40L, null, new BigDecimal("1"), null, null, null);
    }

    private static LlmPriceSnapshot pricedWithInputRate(BigDecimal per1mInputUsd) {
        return new LlmPriceSnapshot(
                FundingSource.WORKSPACE, PricingState.PRICED, null, 42L, per1mInputUsd, null, null, null);
    }

    private static LlmUsageRecorder.LlmUsageSample sampleOfInputTokensOnly(LlmPriceSnapshot price, long inputTokens) {
        return new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.PULL_REQUEST_REVIEW,
                LlmUsageSourceType.AGENT_JOB,
                UUID.randomUUID(),
                1,
                "authoritative-model",
                inputTokens,
                0,
                0,
                0,
                0,
                1,
                price,
                UsageProvenance.RUNNER,
                Instant.now());
    }

    private static LlmUsageEventRepository capturing(AtomicReference<LlmUsageInsert> event) {
        return mock(LlmUsageEventRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("insertIfAbsent")) {
                event.set(invocation.getArgument(0));
                return 1;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static LlmUsageRecorder recorderWith(SimpleMeterRegistry registry, LlmUsageEventRepository repository) {
        return new LlmUsageRecorder(repository, mock(LlmBudgetService.class), registry);
    }

    private static LlmUsageRecorder recorder(LlmUsageEventRepository repository) {
        return new LlmUsageRecorder(repository, mock(LlmBudgetService.class), new SimpleMeterRegistry());
    }
}
