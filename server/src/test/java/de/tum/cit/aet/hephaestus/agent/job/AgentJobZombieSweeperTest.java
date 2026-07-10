package de.tum.cit.aet.hephaestus.agent.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AgentJobZombieSweeperTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private AgentJobSubmitter submitter;

    @Mock
    private WorkerRegistryRepository workerRegistryRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpleMeterRegistry meterRegistry;

    private AgentJobZombieSweeper sweeper;

    private static final AgentNatsProperties NATS_PROPS = new AgentNatsProperties(
        true,
        "nats://localhost:4222",
        "AGENT",
        "hephaestus-agent-executor",
        java.time.Duration.ofMinutes(70),
        5,
        16,
        5,
        java.time.Duration.ofSeconds(25)
    );

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Run the TransactionTemplate callback inline so CAS writes execute in tests.
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                TransactionCallback<Object> cb = inv.getArgument(0);
                return cb.doInTransaction(mock(TransactionStatus.class));
            });
        sweeper = new AgentJobZombieSweeper(
            jobRepository,
            workerRegistryRepository,
            submitter,
            NATS_PROPS,
            objectMapper,
            transactionTemplate,
            meterRegistry
        );
    }

    /** Real entity (owned JPA types must not be mocked) for the absolute-timeout reaper tests. */
    private AgentJob runningJob(UUID id, Instant startedAt, int timeoutSeconds) {
        ConfigSnapshot snapshot = new ConfigSnapshot(
            1,
            1L,
            "cfg",
            LlmProvider.ANTHROPIC,
            CredentialMode.PROXY,
            null,
            null,
            null,
            timeoutSeconds,
            false
        );
        AgentJob job = new AgentJob();
        job.setId(id);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setConfigSnapshot(snapshot.toJson(objectMapper));
        return job;
    }

    private static OrphanedJobRef orphan(UUID jobId, Long workspaceId, int retryCount) {
        return new OrphanedJobRef() {
            @Override
            public UUID getJobId() {
                return jobId;
            }

            @Override
            public Long getWorkspaceId() {
                return workspaceId;
            }

            @Override
            public int getRetryCount() {
                return retryCount;
            }
        };
    }

    @Nested
    @DisplayName("recoverOrphanedJobs (#1138)")
    class RecoverOrphaned {

        @Test
        @DisplayName("re-publishes to the job's workspace only after the requeue CAS wins")
        void requeuesOrphanedJob() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 0))
            );
            when(jobRepository.requeueOrphan(jobId)).thenReturn(1);

            sweeper.recoverOrphanedJobs();

            // The meaningful contract: publish is gated on requeue success and carries the right workspace.
            verify(submitter).publish(jobId, 7L);
        }

        @Test
        @DisplayName("does not re-publish if the CAS requeue lost the race to another sweeper")
        void skipsPublishWhenRequeueRaced() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 0))
            );
            when(jobRepository.requeueOrphan(jobId)).thenReturn(0); // another replica won

            sweeper.recoverOrphanedJobs();

            verify(submitter, never()).publish(any(), any());
        }

        @Test
        @DisplayName("fails (not requeues) an orphan that hit the retry cap")
        void failsOrphanPastRetryCap() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 5))
            ); // retryCount == maxDeliver

            sweeper.recoverOrphanedJobs();

            verify(jobRepository, never()).requeueOrphan(any());
            verify(submitter, never()).publish(any(), any());
        }

        @Test
        @DisplayName("no orphans → no writes")
        void noOrphansNoWork() {
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(List.of());

            sweeper.recoverOrphanedJobs();

            verify(jobRepository, never()).requeueOrphan(any());
            verify(submitter, never()).publish(any(), any());
        }
    }

    @Nested
    class RepublishStaleQueued {

        @Test
        void shouldRepublishStaleQueuedJobs() {
            UUID jobId1 = UUID.randomUUID();
            UUID jobId2 = UUID.randomUUID();
            when(jobRepository.findStaleQueuedJobs(any())).thenReturn(
                List.of(orphan(jobId1, 1L, 0), orphan(jobId2, 2L, 0))
            );

            sweeper.republishStaleQueuedJobs();

            verify(submitter).publish(jobId1, 1L);
            verify(submitter).publish(jobId2, 2L);
        }

        @Test
        void shouldDoNothingWhenNoStaleQueuedJobs() {
            when(jobRepository.findStaleQueuedJobs(any())).thenReturn(List.of());

            sweeper.republishStaleQueuedJobs();

            verify(submitter, never()).publish(any(), any());
        }

        @Test
        void shouldHandlePublishFailureGracefully() {
            UUID jobId1 = UUID.randomUUID();
            UUID jobId2 = UUID.randomUUID();
            when(jobRepository.findStaleQueuedJobs(any())).thenReturn(
                List.of(orphan(jobId1, 1L, 0), orphan(jobId2, 2L, 0))
            );
            // First publish fails, second should still be attempted
            Mockito.doThrow(new RuntimeException("NATS down")).when(submitter).publish(jobId1, 1L);

            sweeper.republishStaleQueuedJobs();

            // Second job should still be attempted despite first failure
            verify(submitter).publish(jobId2, 2L);
        }
    }

    @Nested
    class ReapStaleRunning {

        @Test
        void shouldReapStaleRunningJobs() {
            UUID jobId = UUID.randomUUID();
            // Started 20 minutes ago with 600s (10min) timeout + 5min buffer = 15min
            // 20 min > 15 min → stale
            AgentJob job = runningJob(jobId, Instant.now().minusSeconds(1200), 600);

            when(jobRepository.findStaleRunningJobs(any())).thenReturn(List.of(job));
            when(jobRepository.transitionStatus(any(), any(), any(), any(), any())).thenReturn(1);

            sweeper.reapStaleRunningJobs();

            verify(jobRepository).transitionStatus(
                eq(jobId),
                eq(AgentJobStatus.TIMED_OUT),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        void shouldSkipRunningJobsNotYetStale() {
            UUID jobId = UUID.randomUUID();
            // Started 5 minutes ago with 600s (10min) timeout + 5min buffer = 15min
            // 5 min < 15 min → not stale yet
            AgentJob job = runningJob(jobId, Instant.now().minusSeconds(300), 600);

            when(jobRepository.findStaleRunningJobs(any())).thenReturn(List.of(job));

            sweeper.reapStaleRunningJobs();

            verify(jobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }

        @Test
        void shouldDoNothingWhenNoStaleRunningJobs() {
            when(jobRepository.findStaleRunningJobs(any())).thenReturn(List.of());

            sweeper.reapStaleRunningJobs();

            verify(jobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
        }
    }
}
