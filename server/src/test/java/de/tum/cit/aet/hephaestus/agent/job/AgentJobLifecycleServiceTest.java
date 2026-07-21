package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AgentJobLifecycleServiceTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private JobTypeHandlerRegistry handlerRegistry;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SandboxManager sandboxManager;

    @Mock
    private LlmUsageRecorder usageRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentJobLifecycleService service;

    private Workspace workspace;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AgentJobLifecycleService(
            agentJobRepository,
            handlerRegistry,
            transactionTemplate,
            sandboxManager,
            Optional.empty(),
            usageRecorder,
            objectMapper
        );

        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-ws");

        // cancel() now runs its status-transition CAS through transactionTemplate.execute (#1368 fix
        // wave — moved off @Transactional so the post-commit ledger write can run strictly after the
        // status transition commits, matching AgentJobExecutor's own cancellation handling). Lenient:
        // RetryDelivery's own nested setup re-stubs the same methods for its own tests.
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(inv -> {
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

    private AgentJob createJobWithStatus(AgentJobStatus status) {
        AgentJob job = new AgentJob();
        job.prePersist();
        job.setWorkspace(workspace);
        job.setStatus(status);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        return job;
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelQueuedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.QUEUED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);

            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            AgentJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
        }

        @Test
        void shouldCancelRunningJobAndCallSandbox() {
            AgentJob job = createJobWithStatus(AgentJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);

            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            verify(sandboxManager).cancel(jobId);
        }

        @Test
        void shouldBeIdempotentForCancelledJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.CANCELLED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            AgentJob result = service.cancel(1L, jobId);

            assertThat(result.getStatus()).isEqualTo(AgentJobStatus.CANCELLED);
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }

        @Test
        void shouldThrow409ForCompletedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.COMPLETED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("COMPLETED");
        }

        @Test
        void shouldThrow409ForFailedJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.FAILED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("FAILED");
        }

        @Test
        void shouldThrow409ForTimedOutJob() {
            AgentJob job = createJobWithStatus(AgentJobStatus.TIMED_OUT);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(AgentJobStateConflictException.class);
        }

        @Test
        void shouldThrow404ForNonExistentJob() {
            UUID jobId = UUID.randomUUID();
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1L, jobId)).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void shouldThrow409WhenExecutorWinsRace() {
            AgentJob job = createJobWithStatus(AgentJobStatus.RUNNING);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            // Executor already transitioned to COMPLETED
            when(agentJobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(0);

            AgentJob completedJob = createJobWithStatus(AgentJobStatus.COMPLETED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(completedJob));

            assertThatThrownBy(() -> service.cancel(1L, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("COMPLETED");

            verify(sandboxManager, never()).cancel(any());
        }
    }

    /**
     * #1368 fix wave: {@code cancel} must attempt {@link LlmUsageRecorder#recordUnverifiable} for any
     * job that reached RUNNING (worker_id set at claim), regardless of whether THIS call's own CAS won
     * the CANCELLED transition — a concurrent executor cancellation or worker-drain may have won it
     * first. Never for a job cancelled before it was ever claimed.
     */
    @Nested
    @DisplayName("Unpriced usage ledger write on user-cancel (#1368 fix wave)")
    class UnverifiableUsageLedger {

        @Test
        void cancellingAClaimedRunningJob_recordsAnUnpricedLedgerEntry() {
            AgentJob job = createJobWithStatus(AgentJobStatus.RUNNING);
            job.setWorkerId("worker-1");
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);
            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            cancelledJob.setWorkerId("worker-1");
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            ArgumentCaptor<LlmUsageRecorder.LlmUsageSample> sample = ArgumentCaptor.forClass(
                LlmUsageRecorder.LlmUsageSample.class
            );
            verify(usageRecorder).recordUnverifiable(eq(1L), sample.capture());
            assertThat(sample.getValue().sourceId()).isEqualTo(cancelledJob.getId());
            assertThat(sample.getValue().inputTokens()).isZero();
        }

        @Test
        void cancellingAJobThatNeverStarted_neverTouchesTheLedger() {
            // QUEUED and never claimed — no worker_id — so cancelling it must not attribute any spend.
            AgentJob job = createJobWithStatus(AgentJobStatus.QUEUED);
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));
            when(
                agentJobRepository.transitionStatus(eq(jobId), eq(AgentJobStatus.CANCELLED), any(), any(), any())
            ).thenReturn(1);
            AgentJob cancelledJob = createJobWithStatus(AgentJobStatus.CANCELLED);
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L))
                .thenReturn(Optional.of(job))
                .thenReturn(Optional.of(cancelledJob));

            service.cancel(1L, jobId);

            verify(usageRecorder, never()).recordUnverifiable(any(), any());
        }

        @Test
        void idempotentCancelOfAnAlreadyCancelledStartedJob_stillAttemptsTheLedgerWrite() {
            // Already CANCELLED by some earlier caller (executor / worker-drain / a prior cancel call),
            // but it DID start executing — the duplicate write attempt is safe (unique source_id) and is
            // the only backstop against that earlier caller having missed the write itself.
            AgentJob job = createJobWithStatus(AgentJobStatus.CANCELLED);
            job.setWorkerId("worker-1");
            UUID jobId = job.getId();

            when(agentJobRepository.findByIdAndWorkspaceId(jobId, 1L)).thenReturn(Optional.of(job));

            service.cancel(1L, jobId);

            verify(usageRecorder).recordUnverifiable(eq(1L), any());
            verify(agentJobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class RetryDelivery {

        private static final Long WORKSPACE_ID = 1L;

        private AgentJob completedJob;
        private UUID jobId;
        private JobTypeHandler handler;

        @BeforeEach
        void setUpRetryDelivery() {
            // transactionTemplate.execute()/executeWithoutResult() already invoke their
            // callback/consumer via the lenient stubs in the outer setUp() — re-stubbing the same
            // methods here would re-trigger those stubs during when()'s "record" phase (Mockito
            // invokes the mock to register the matcher, and an already-answered method runs its
            // existing answer with a null argument) and NPE.

            completedJob = createJobWithStatus(AgentJobStatus.COMPLETED);
            jobId = completedJob.getId();

            handler = mock(JobTypeHandler.class);
            // Lenient: not all tests reach the delivery path that calls getHandler
            lenient().when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        }

        @Test
        void shouldThrow404WhenJobNotFound() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("AgentJob");
        }

        @Test
        void shouldThrow409WhenCasTransitionReturnsZero() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                0
            );

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("delivery status FAILED");

            verify(handler, never()).deliver(any());
        }

        @Test
        void shouldDeliverAndUpdateStatusOnSuccess() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                1
            );
            when(agentJobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

            AgentJob result = service.retryDelivery(WORKSPACE_ID, jobId);

            verify(handler).deliver(completedJob);
            verify(agentJobRepository).updateDeliveryStatus(
                eq(jobId),
                eq(DeliveryStatus.DELIVERED),
                eq(completedJob.getDeliveryCommentId())
            );
            assertThat(result.getId()).isEqualTo(jobId);
        }

        @Test
        void shouldRevertToFailedOnDeliveryException() {
            when(agentJobRepository.findByIdAndWorkspaceId(jobId, WORKSPACE_ID)).thenReturn(Optional.of(completedJob));
            when(agentJobRepository.transitionDeliveryStatus(eq(jobId), eq(DeliveryStatus.PENDING), any())).thenReturn(
                1
            );
            when(agentJobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

            doThrow(new RuntimeException("GitHub API rate limited")).when(handler).deliver(completedJob);

            assertThatThrownBy(() -> service.retryDelivery(WORKSPACE_ID, jobId))
                .isInstanceOf(AgentJobStateConflictException.class)
                .hasMessageContaining("Delivery retry failed")
                .hasMessageContaining("GitHub API rate limited");

            verify(agentJobRepository).updateDeliveryStatus(
                eq(jobId),
                eq(DeliveryStatus.FAILED),
                eq(completedJob.getDeliveryCommentId())
            );
        }
    }

    /** #1368 hardening (tri-state + fenced writes: #1368 fix wave, findings #5/#6): {@code recoverStuckDelivery}. */
    @Nested
    @DisplayName("recoverStuckDelivery (#1368 hardening)")
    class RecoverStuckDelivery {

        private static final short CLAIMED_ATTEMPTS = 1;

        private AgentJob completedJob;
        private UUID jobId;
        private JobTypeHandler handler;

        @BeforeEach
        void setUpRecovery() {
            completedJob = createJobWithStatus(AgentJobStatus.COMPLETED);
            completedJob.setDeliveryStatus(DeliveryStatus.PENDING);
            jobId = completedJob.getId();

            handler = mock(JobTypeHandler.class);
            lenient().when(handlerRegistry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).thenReturn(handler);
        }

        @Test
        @DisplayName("a FOUND marker hit records the existing comment id as DELIVERED WITHOUT calling deliver() again")
        void markerHitSkipsRedelivery() {
            lenient()
                .when(handler.findExistingDelivery(completedJob))
                .thenReturn(ExistingDeliveryLookup.found("existing-comment-id"));
            lenient()
                .when(
                    agentJobRepository.transitionDeliveryStatusFenced(
                        eq(jobId),
                        eq(DeliveryStatus.DELIVERED),
                        eq("existing-comment-id"),
                        any(),
                        eq(CLAIMED_ATTEMPTS)
                    )
                )
                .thenReturn(1);

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isTrue();
            verify(handler, never()).deliver(any());
            verify(agentJobRepository).transitionDeliveryStatusFenced(
                jobId,
                DeliveryStatus.DELIVERED,
                "existing-comment-id",
                java.util.Set.of(DeliveryStatus.PENDING),
                CLAIMED_ATTEMPTS
            );
        }

        @Test
        @DisplayName("ABSENT falls through to a normal deliver() attempt, which succeeds")
        void absentFallsThroughToDeliverAndSucceeds() {
            lenient().when(handler.findExistingDelivery(completedJob)).thenReturn(ExistingDeliveryLookup.absent());
            lenient()
                .when(
                    agentJobRepository.transitionDeliveryStatusFenced(
                        eq(jobId),
                        eq(DeliveryStatus.DELIVERED),
                        any(),
                        any(),
                        eq(CLAIMED_ATTEMPTS)
                    )
                )
                .thenReturn(1);

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isTrue();
            verify(handler).deliver(completedJob);
            verify(agentJobRepository).transitionDeliveryStatusFenced(
                jobId,
                DeliveryStatus.DELIVERED,
                completedJob.getDeliveryCommentId(),
                java.util.Set.of(DeliveryStatus.PENDING),
                CLAIMED_ATTEMPTS
            );
        }

        @Test
        @DisplayName(
            "a failed deliver() attempt returns false and does NOT write a terminal status (left PENDING for the next sweep pass)"
        )
        void failedDeliverReturnsFalseAndLeavesPending() {
            lenient().when(handler.findExistingDelivery(completedJob)).thenReturn(ExistingDeliveryLookup.absent());
            doThrow(new RuntimeException("GitHub API rate limited")).when(handler).deliver(completedJob);

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isFalse();
            verify(agentJobRepository, never()).transitionDeliveryStatusFenced(any(), any(), any(), any(), anyShort());
            verify(agentJobRepository, never()).updateDeliveryStatus(any(), any(), any());
        }

        @Test
        @DisplayName("UNKNOWN (dedup check inconclusive) does NOT call deliver() — leaves PENDING for a later pass")
        void unknownLeavesPendingWithoutPosting() {
            lenient().when(handler.findExistingDelivery(completedJob)).thenReturn(ExistingDeliveryLookup.unknown());

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isFalse();
            verify(handler, never()).deliver(any());
            verify(agentJobRepository, never()).transitionDeliveryStatusFenced(any(), any(), any(), any(), anyShort());
        }

        @Test
        @DisplayName("a handler whose dedup check itself throws is treated as UNKNOWN — does not post")
        void dedupCheckThrowingIsTreatedAsUnknown() {
            lenient().when(handler.findExistingDelivery(completedJob)).thenThrow(new RuntimeException("provider down"));

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isFalse();
            verify(handler, never()).deliver(any());
        }

        @Test
        @DisplayName("a superseded attempt's fenced write matches no row — returns false without clobbering the winner")
        void supersededAttemptWriteIsANoOp() {
            lenient().when(handler.findExistingDelivery(completedJob)).thenReturn(ExistingDeliveryLookup.absent());
            // Fence lost: a later attempt already advanced delivery_attempts past what this call claimed.
            lenient()
                .when(
                    agentJobRepository.transitionDeliveryStatusFenced(
                        eq(jobId),
                        eq(DeliveryStatus.DELIVERED),
                        any(),
                        any(),
                        eq(CLAIMED_ATTEMPTS)
                    )
                )
                .thenReturn(0);

            boolean result = service.recoverStuckDelivery(completedJob, CLAIMED_ATTEMPTS);

            assertThat(result).isFalse();
            verify(handler).deliver(completedJob);
        }
    }
}
