package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
        assertThat(event.get().inputRate()).isEqualTo(new BigDecimal("1"));
    }

    @Test
    void duplicateSourceAttemptIsAnIdempotentNoOp() {
        LlmUsageEventRepository repository = mock(LlmUsageEventRepository.class);
        LlmUsageRecorder recorder = recorder(repository);

        assertThatCode(() -> recorder.record(7L, sample(priced(), 2))).doesNotThrowAnyException();
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
        return new LlmUsageRecorder(
            repository,
            mock(WorkspaceRepository.class),
            mock(LlmBudgetService.class),
            new SimpleMeterRegistry()
        );
    }
}
