package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class LlmUsageRecorderTest extends BaseUnitTest {

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
            Instant.now()
        );
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
            new BigDecimal("4")
        );
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

        assertThat(event.get().sourceType()).isEqualTo("AGENT_JOB");
        assertThat(event.get().sourceAttempt()).isEqualTo(3);
        assertThat(event.get().model()).isEqualTo("authoritative-model");
        // 1*1 + 2*2 + .5*3 + .25*4 = 7.5. reasoning is output telemetry, not a second charge.
        assertThat(event.get().costUsd()).isEqualByComparingTo("7.500000");
        assertThat(event.get().appliedWorkspaceModelId()).isEqualTo(42L);
        assertThat(event.get().appliedPer1mInputUsd()).isEqualTo(new BigDecimal("1"));
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

        assertThat(inserts.get()).as("both attempts reach the ledger's conflict guard").isEqualTo(2);
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

        assertThat(event.get().sourceAttempt()).isEqualTo(4);
        assertThat(event.get().costUsd()).isNull();
        assertThat(event.get().pricingState()).isEqualTo("UNPRICED");
        assertThat(event.get().appliedWorkspaceModelId()).isEqualTo(42L);
    }

    private static LlmUsageRecorder recorder(LlmUsageEventRepository repository) {
        return new LlmUsageRecorder(repository, mock(LlmBudgetService.class), new SimpleMeterRegistry());
    }
}
