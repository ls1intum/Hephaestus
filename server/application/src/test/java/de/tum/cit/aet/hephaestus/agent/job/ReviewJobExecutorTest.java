package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.handler.DefaultReviewExecutionHandler;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetDecision;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.PracticeReviewRefusalMetrics;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class ReviewJobExecutorTest extends BaseUnitTest {
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final ReviewMemberAiPolicy policy = mock(ReviewMemberAiPolicy.class);
    private final DefaultReviewExecutionHandler handler = mock(DefaultReviewExecutionHandler.class);
    private final ReviewJobAssignments assignments = mock(ReviewJobAssignments.class);
    private final SandboxManager sandbox = mock(SandboxManager.class);
    private final LlmBudgetService budgets = mock(LlmBudgetService.class);
    private Optional<WorkerCapacityState> capacity = Optional.empty();

    private ReviewJobExecutor executor(AsyncTaskExecutor pool) {
        var transactions = mock(TransactionTemplate.class);
        lenient().when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        var meters = new SimpleMeterRegistry();
        var telemetry = new ReviewJobTelemetry(meters, Tracer.NOOP);
        return new ReviewJobExecutor(
                new AgentProperties(
                        true,
                        Duration.ofSeconds(1),
                        5,
                        5,
                        Duration.ofSeconds(25),
                        Duration.ofDays(14),
                        Duration.ofDays(90)),
                jobs,
                handler,
                mock(PracticePiAdapter.class),
                mock(WorkerJwtIssuer.class),
                sandbox,
                pool,
                transactions,
                new JsonMapper(),
                meters,
                new PracticeReviewRefusalMetrics(meters),
                telemetry,
                mock(LlmUsageRecorder.class),
                mock(PracticeScriptRunRecorder.class),
                null,
                capacity,
                Optional.empty(),
                new ReviewJobClaimService(
                        jobs,
                        policy,
                        mock(ReviewMethodVerifier.class),
                        assignments,
                        transactions,
                        new JsonMapper(),
                        meters,
                        new PracticeReviewRefusalMetrics(meters),
                        budgets,
                        null,
                        telemetry,
                        Optional.empty()),
                new ReviewJobRecovery(
                        jobs,
                        new AgentProperties(
                                true,
                                Duration.ofSeconds(1),
                                5,
                                5,
                                Duration.ofSeconds(25),
                                Duration.ofDays(14),
                                Duration.ofDays(90)),
                        new JsonMapper(),
                        mock(LlmUsageRecorder.class),
                        Optional.empty()));
    }

    @Test
    void shouldNotStartASandboxAfterAnotherWorkerClaimedTheJob() {
        UUID id = UUID.randomUUID();
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.empty());
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(id))
                .isEqualTo(ReviewJobExecutor.StartOutcome.SKIPPED);
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldCancelSuppressedWorkBeforeReadingOrRunningItsInputs() {
        var job = RuntimeReviewTestData.job();
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        when(policy.isProcessingSuppressed(job)).thenReturn(true);
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(job.getId()))
                .isEqualTo(ReviewJobExecutor.StartOutcome.SKIPPED);
        assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
        assertThat(job.getCancellationReason()).isEqualTo(ReviewJobCancellationReason.PERSON_DATA_ERASED);
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldNeverRunAnAlreadyAdmittedAttemptAgain() {
        var job = RuntimeReviewTestData.job();
        job.setAdmissionAttempt(0);
        job.setAdmissionDigest("admitted");
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(job.getId()))
                .isEqualTo(ReviewJobExecutor.StartOutcome.SKIPPED);
        assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldRespectTheSandboxPoolSizeWhenClaiming() {
        var pool = new ThreadPoolTaskExecutor();
        pool.setMaxPoolSize(2);
        assertThat(executor(pool).computeCapacity()).isEqualTo(2);
    }

    @Test
    void shouldNotCancelAJobThisWorkerDoesNotOwn() {
        assertThat(executor(mock(AsyncTaskExecutor.class)).cancelLocalJob(UUID.randomUUID(), "cancelled"))
                .isFalse();
        verifyNoInteractions(sandbox);
    }

    @Test
    void shouldFinishAnEmptyDrainWithoutCallingTheProvider() {
        var executor = executor(mock(AsyncTaskExecutor.class));
        executor.stopAcceptingNewJobs();
        assertThat(executor.awaitInFlight(Duration.ZERO)).isTrue();
        assertThat(executor.cancelInFlight(ReviewJobCancellationReason.DRAIN_GRACEFUL))
                .isTrue();
        verifyNoInteractions(sandbox);
    }

    @Test
    void shouldFailMissingAssignmentsBeforeClaimingInsteadOfRetryingForever() {
        var job = RuntimeReviewTestData.job();
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        when(policy.isProcessingSuppressed(job))
                .thenThrow(new JobPreparationException(
                        JobPreparationException.Reason.INPUT_CONTRACT_INVALID, "private SQL/path"));
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(job.getId()))
                .isEqualTo(ReviewJobExecutor.StartOutcome.SKIPPED);
        assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(job.getErrorMessage()).doesNotContain("private SQL/path");
        verify(jobs).save(job);
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldCancelOldBudgetHeldWorkEvenAfterItsAvailabilityWasMoved() {
        var job = RuntimeReviewTestData.job();
        job.setSubmittedAt(Instant.now().minus(Duration.ofDays(8)));
        job.setAvailableAt(Instant.now().minusSeconds(1));
        job.setHoldReason(ReviewJob.HOLD_REASON_BUDGET);
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        when(policy.permitsReview(job)).thenReturn(true);
        when(budgets.decide(job.getWorkspaceId()))
                .thenReturn(new LlmBudgetDecision(LlmBudgetBlockReason.EXHAUSTED, LlmBudgetBlockReason.NONE));
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(job.getId()))
                .isEqualTo(ReviewJobExecutor.StartOutcome.SKIPPED);
        assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.CANCELLED);
        assertThat(job.getCancellationReason()).isEqualTo(ReviewJobCancellationReason.BUDGET_EXHAUSTED);
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldMeasureAgeFromSubmissionAfterARepeatedHold() {
        var job = RuntimeReviewTestData.job();
        job.setSubmittedAt(Instant.now().minus(Duration.ofDays(8)));
        job.setAvailableAt(Instant.now().plus(Duration.ofHours(1)));
        assertThat(ReviewJobTelemetry.age(job)).isGreaterThanOrEqualTo(Duration.ofDays(8));
    }

    @Test
    void shouldReleaseCapacityAndMdcWhenInputPreparationFails() {
        var job = RuntimeReviewTestData.job();
        job.setSubmittedAt(Instant.now().minusSeconds(2));
        job.setConfigSnapshot(new ConfigSnapshot(
                        ConfigSnapshot.SCHEMA_VERSION,
                        new FrozenModel(
                                LlmApiProtocol.OPENAI_COMPLETIONS,
                                "https://model.example",
                                "model",
                                null,
                                null,
                                null,
                                null,
                                DataHandlingTier.UNDECLARED,
                                null,
                                null),
                        null,
                        null,
                        600,
                        false,
                        null)
                .toJson(new JsonMapper()));
        var workerCapacity = mock(WorkerCapacityState.class);
        when(workerCapacity.reviewMax()).thenReturn(1);
        capacity = Optional.of(workerCapacity);
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobs.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);
        when(policy.permitsReview(job)).thenReturn(true);
        var binding = mock(WorkspaceAgentBinding.class);
        when(binding.getDataHandlingTier()).thenReturn(DataHandlingTier.UNDECLARED);
        when(binding.getMaxConcurrentJobs()).thenReturn(1);
        when(policy.binding(job)).thenReturn(Optional.of(binding));
        when(budgets.decide(job.getWorkspaceId())).thenReturn(LlmBudgetDecision.ALLOWED);
        when(assignments.load(job))
                .thenReturn(List.of(RuntimeReviewTestData.assignment(null, null)))
                .thenReturn(List.of(RuntimeReviewTestData.assignment(null, null)))
                .thenThrow(new JobPreparationException("Assignments were removed during execution"));
        when(handler.prepareInputs(job)).thenThrow(new JobPreparationException("Input preparation failed"));
        var pool = mock(AsyncTaskExecutor.class);
        doAnswer(invocation -> {
                    Runnable work = invocation.getArgument(0);
                    work.run();
                    return null;
                })
                .when(pool)
                .execute(any(Runnable.class));
        var executor = executor(pool);

        assertThat(executor.processJob(job.getId())).isEqualTo(ReviewJobExecutor.StartOutcome.STARTED);

        assertThat(executor.computeCapacity()).isEqualTo(1);
        verify(workerCapacity).releaseReview();
        verify(assignments, times(2)).load(job);
        assertThat(MDC.get("job.id")).isNull();
        verifyNoInteractions(sandbox);
    }
}
