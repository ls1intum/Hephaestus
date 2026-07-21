package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AgentJobZombieSweeperTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private WorkerRegistryRepository workerRegistryRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpleMeterRegistry meterRegistry;

    private AgentJobZombieSweeper sweeper;

    private static final AgentProperties AGENT_PROPS = new AgentProperties(
        true,
        java.time.Duration.ofSeconds(1),
        5,
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
            AGENT_PROPS,
            objectMapper,
            transactionTemplate,
            meterRegistry
        );
    }

    /** Real entity (owned JPA types must not be mocked) for the absolute-timeout reaper tests. */
    private AgentJob runningJob(UUID id, Instant startedAt, int timeoutSeconds) {
        ConfigSnapshot snapshot = new ConfigSnapshot(
            ConfigSnapshot.SCHEMA_VERSION,
            1L,
            "cfg",
            "anthropic-messages",
            "https://api.anthropic.com",
            "claude-sonnet-4",
            null,
            null,
            null,
            false,
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

    private static final String DEAD_WORKER_ID = "dead-replica";

    private static OrphanedJobRef orphan(UUID jobId, Long workspaceId, int retryCount) {
        return orphan(jobId, workspaceId, retryCount, DEAD_WORKER_ID);
    }

    private static OrphanedJobRef orphan(UUID jobId, Long workspaceId, int retryCount, String workerId) {
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

            @Override
            public String getWorkerId() {
                return workerId;
            }
        };
    }

    @Nested
    @DisplayName("recoverOrphanedJobs (#1138)")
    class RecoverOrphaned {

        @Test
        @DisplayName("requeues (RUNNING → QUEUED) and counts it once the CAS wins")
        void requeuesOrphanedJob() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 0))
            );
            when(jobRepository.requeueOrphan(jobId, DEAD_WORKER_ID, AGENT_PROPS.maxRetries())).thenReturn(1);

            sweeper.recoverOrphanedJobs();

            verify(jobRepository).requeueOrphan(jobId, DEAD_WORKER_ID, AGENT_PROPS.maxRetries());
            assertThat(meterRegistry.counter("agent.job.orphan.requeued").count()).isEqualTo(1d);
        }

        @Test
        @DisplayName("does not count a requeue win if the CAS lost the race to another sweeper")
        void skipsWhenRequeueRaced() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 0))
            );
            when(jobRepository.requeueOrphan(jobId, DEAD_WORKER_ID, AGENT_PROPS.maxRetries())).thenReturn(0); // another replica won

            sweeper.recoverOrphanedJobs();

            verify(jobRepository).requeueOrphan(jobId, DEAD_WORKER_ID, AGENT_PROPS.maxRetries());
            // No further status write beyond the attempted requeue itself, and no requeue credited.
            verify(jobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
            assertThat(meterRegistry.counter("agent.job.orphan.requeued").count()).isZero();
        }

        @Test
        @DisplayName("fails (not requeues) an orphan that hit the retry cap")
        void failsOrphanPastRetryCap() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(
                List.of(orphan(jobId, 7L, 5))
            ); // retryCount == maxRetries

            sweeper.recoverOrphanedJobs();

            verify(jobRepository, never()).requeueOrphan(any(), any(), anyInt());
            verify(jobRepository).transitionStatus(
                eq(jobId),
                eq(AgentJobStatus.FAILED),
                any(),
                any(),
                eq(Set.of(AgentJobStatus.RUNNING))
            );
        }

        @Test
        @DisplayName("no orphans → no writes")
        void noOrphansNoWork() {
            when(jobRepository.findOrphanedRunningJobs(any(), ArgumentMatchers.anyLong())).thenReturn(List.of());

            sweeper.recoverOrphanedJobs();

            verify(jobRepository, never()).requeueOrphan(any(), any(), anyInt());
            verify(jobRepository, never()).transitionStatus(any(), any(), any(), any(), any());
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
