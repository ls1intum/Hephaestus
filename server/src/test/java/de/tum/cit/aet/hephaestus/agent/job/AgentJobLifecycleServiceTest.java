package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AgentJobLifecycleServiceTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository agentJobRepository;

    @Mock
    private JobTypeHandlerRegistry handlerRegistry;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SandboxManager sandboxManager;

    private AgentJobLifecycleService service;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new AgentJobLifecycleService(
            agentJobRepository,
            handlerRegistry,
            transactionTemplate,
            sandboxManager,
            Optional.empty()
        );

        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("test-ws");
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

    @Nested
    class RetryDelivery {

        private static final Long WORKSPACE_ID = 1L;

        private AgentJob completedJob;
        private UUID jobId;
        private JobTypeHandler handler;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUpRetryDelivery() {
            // Make transactionTemplate.execute() actually invoke the callback
            when(transactionTemplate.execute(any())).thenAnswer(inv -> {
                TransactionCallback<?> callback = inv.getArgument(0);
                return callback.doInTransaction(mock(TransactionStatus.class));
            });

            // Make transactionTemplate.executeWithoutResult() invoke the consumer (lenient:
            // not all tests reach the delivery path that calls executeWithoutResult)
            lenient()
                .doAnswer(inv -> {
                    Consumer<TransactionStatus> action = inv.getArgument(0);
                    action.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());

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
}
