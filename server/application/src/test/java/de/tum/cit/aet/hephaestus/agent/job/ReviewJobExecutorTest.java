package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewExecutionHandler;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.PracticeReviewRefusalMetrics;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class ReviewJobExecutorTest extends BaseUnitTest {
    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final ReviewMemberAiPolicy policy = mock(ReviewMemberAiPolicy.class);
    private final ReviewExecutionHandler handler = mock(ReviewExecutionHandler.class);
    private final ReviewJobAssignments assignments = mock(ReviewJobAssignments.class);
    private final SandboxManager sandbox = mock(SandboxManager.class);

    private ReviewJobExecutor executor(AsyncTaskExecutor pool) {
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        var meters = new SimpleMeterRegistry();
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
                policy,
                handler,
                assignments,
                mock(PracticePiAdapter.class),
                mock(WorkerJwtIssuer.class),
                sandbox,
                pool,
                transactions,
                new JsonMapper(),
                meters,
                new PracticeReviewRefusalMetrics(meters),
                mock(ReviewJobTelemetry.class),
                mock(LlmUsageRecorder.class),
                mock(PracticeScriptRunRecorder.class),
                mock(LlmBudgetService.class),
                null,
                Optional.empty(),
                Optional.empty(),
                mock(ReviewMethodVerifier.class));
    }

    @Test
    void shouldNotStartASandboxAfterAnotherWorkerClaimedTheJob() {
        UUID id = UUID.randomUUID();
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.empty());
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(id)).isFalse();
        verifyNoInteractions(handler, sandbox);
    }

    @Test
    void shouldCancelSuppressedWorkBeforeReadingOrRunningItsInputs() {
        var job = RuntimeReviewTestData.job();
        when(jobs.findByIdQueuedForUpdateSkipLocked(any(), any())).thenReturn(Optional.of(job));
        when(policy.isProcessingSuppressed(job)).thenReturn(true);
        var assigned = RuntimeReviewTestData.assignment(5L, null);
        when(assignments.review(job)).thenReturn(RuntimeReviewTestData.review(ReviewKind.ITEM, assigned));
        assertThat(executor(mock(AsyncTaskExecutor.class)).processJob(job.getId()))
                .isFalse();
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
                .isFalse();
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
}
