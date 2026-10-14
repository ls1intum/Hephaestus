package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ReviewJobLifecycleServiceTest extends BaseUnitTest {

    @Mock
    private ReviewJobRepository agentJobRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SandboxManager sandboxManager;

    @Mock
    private LlmUsageRecorder usageRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReviewJobLifecycleService service;

    private Workspace workspace;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ReviewJobLifecycleService(
                agentJobRepository,
                transactionTemplate,
                sandboxManager,
                Optional.empty(),
                usageRecorder,
                objectMapper,
                new ReviewJobTelemetry(new SimpleMeterRegistry(), Tracer.NOOP));

        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-ws");

        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient()
                .doAnswer(inv -> {
                    Consumer<TransactionStatus> action = inv.getArgument(0);
                    action.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }

    private ReviewJob createJobWithStatus(ReviewJobStatus status) {
        ReviewJob job = new ReviewJob();
        job.prePersist();
        RuntimeJobTestData.initialize(job, (workspace).getId());
        job.setStatus(status);

        ConfigSnapshot snapshot = new ConfigSnapshot(
                        ConfigSnapshot.SCHEMA_VERSION,
                        "openai-completions",
                        "https://api.openai.com/v1",
                        "test-model",
                        null,
                        null,
                        null,
                        UsageFundingSource.INSTANCE,
                        1L,
                        1L,
                        null,
                        600,
                        false,
                        null,
                        null,
                        null)
                .withPriceSnapshot(new LlmPriceSnapshot(
                        UsageFundingSource.INSTANCE,
                        UsagePricingState.PRICED,
                        1L,
                        null,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE));
        job.setConfigSnapshot(snapshot.toJson(objectMapper));
        return job;
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelQueuedJob() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.QUEUED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);

            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            ReviewJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
        }

        @Test
        void shouldCancelRunningJobAndCallSandbox() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);

            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            ReviewJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
            verify(sandboxManager).cancel(jobId);
        }

        @Test
        void shouldBeIdempotentForCancelledJob() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.CANCELLED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));

            ReviewJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }

        @ParameterizedTest
        @EnumSource(
                value = ReviewJobStatus.class,
                names = {"COMPLETED", "FAILED", "TIMED_OUT"})
        void shouldThrow409ForAnAlreadyTerminalJob(ReviewJobStatus terminalStatus) {
            ReviewJob job = createJobWithStatus(terminalStatus);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                    .isInstanceOf(ReviewJobStateConflictException.class)
                    .hasMessage("This review already finished. You cannot cancel it.");
        }

        @Test
        void shouldThrow404ForNonExistentJob() {
            UUID jobId = UUID.randomUUID();
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void shouldThrow409WhenExecutorWinsRace() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(any(), any(), any(), any(), any()))
                    .thenReturn(0);

            ReviewJob completedJob = createJobWithStatus(ReviewJobStatus.COMPLETED);
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(completedJob));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                    .isInstanceOf(ReviewJobStateConflictException.class)
                    .hasMessage("This review finished before the server could cancel it.");

            verify(sandboxManager, never()).cancel(any());
        }
    }

    @Nested
    @DisplayName("Unpriced usage ledger write on user-cancel")
    class UnverifiableUsageLedger {

        @Test
        void cancellingAClaimedRunningJob_recordsAnUnpricedLedgerEntry() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.RUNNING);
            job.setWorkerId("worker-1");
            job.setExecutionStartedAt(Instant.now());
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);
            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            cancelledJob.setId(jobId);
            cancelledJob.setWorkerId("worker-1");
            cancelledJob.setExecutionStartedAt(job.getExecutionStartedAt());
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample =
                    ArgumentCaptor.forClass(LlmUsageRecorder.LlmUsageSample.class);
            verify(usageRecorder).recordUnverifiable(eq(1L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(cancelledJob.getId());
            assertThat(sample.getValue().sourceAttempt()).isEqualTo(cancelledJob.getAttempt());
            assertThat(sample.getValue().inputTokens()).isZero();
        }

        @Test
        void cancellingALegacyClaimedJobWithoutAdmissionPrice_preservesCancellationAndRecordsUnpricedUsage() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.RUNNING);
            job.setWorkerId("worker-1");
            job.setExecutionStartedAt(Instant.now());
            ConfigSnapshot legacySnapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper)
                    .withPriceSnapshot(null);
            job.setConfigSnapshot(legacySnapshot.toJson(objectMapper));
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);
            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            cancelledJob.setId(jobId);
            cancelledJob.setWorkerId("worker-1");
            cancelledJob.setExecutionStartedAt(job.getExecutionStartedAt());
            cancelledJob.setConfigSnapshot(legacySnapshot.toJson(objectMapper));
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            ReviewJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample =
                    ArgumentCaptor.forClass(LlmUsageRecorder.LlmUsageSample.class);
            verify(usageRecorder).recordUnverifiable(eq(1L), sample.capture());
            assertThat(sample.getValue().price().fundingSource()).isEqualTo(UsageFundingSource.INSTANCE);
            assertThat(sample.getValue().price().pricingState()).isEqualTo(UsagePricingState.UNPRICED);
        }

        @Test
        void cancellingAJobThatNeverStarted_neverTouchesTheLedger() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.QUEUED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);
            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            verify(usageRecorder, never()).recordUnverifiable(any(), any());
        }

        @Test
        void cancellingAClaimedJobStillInPreparation_neverTouchesTheLedger() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.RUNNING);
            job.setWorkerId("worker-1");
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));
            when(agentJobRepository.transitionStatus(eq(jobId), eq(ReviewJobStatus.CANCELLED), any(), any(), any()))
                    .thenReturn(1);
            ReviewJob cancelledJob = createJobWithStatus(ReviewJobStatus.CANCELLED);
            cancelledJob.setId(jobId);
            cancelledJob.setWorkerId("worker-1");
            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L))
                    .thenReturn(Optional.of(job))
                    .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            verify(usageRecorder, never()).recordUnverifiable(any(), any());
        }

        @Test
        void idempotentCancelOfAnAlreadyCancelledStartedJob_doesNotWriteOutsideWinningTransaction() {
            ReviewJob job = createJobWithStatus(ReviewJobStatus.CANCELLED);
            job.setWorkerId("worker-1");
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceIdForUpdate(jobId, 1L)).thenReturn(Optional.of(job));

            service.cancel(1L, jobId);

            verify(usageRecorder, never()).recordUnverifiable(any(), any());
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }
    }
}
