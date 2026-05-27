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
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;

class AgentJobZombieSweeperTest extends BaseUnitTest {

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private AgentJobSubmitter submitter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SimpleMeterRegistry meterRegistry;

    private AgentJobZombieSweeper sweeper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sweeper = new AgentJobZombieSweeper(jobRepository, submitter, objectMapper, meterRegistry);
    }

    private AgentJob mockQueuedJob(UUID id, Long workspaceId) {
        AgentJob job = mock(AgentJob.class);
        Workspace ws = mock(Workspace.class);
        lenient().when(ws.getId()).thenReturn(workspaceId);
        lenient().when(job.getId()).thenReturn(id);
        lenient().when(job.getWorkspace()).thenReturn(ws);
        lenient().when(job.getCreatedAt()).thenReturn(Instant.now().minusSeconds(900)); // 15 min ago
        return job;
    }

    private AgentJob mockRunningJob(UUID id, Instant startedAt, int timeoutSeconds) {
        AgentJob job = mock(AgentJob.class);
        lenient().when(job.getId()).thenReturn(id);
        lenient().when(job.getStartedAt()).thenReturn(startedAt);
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
        lenient().when(job.getConfigSnapshot()).thenReturn(snapshot.toJson(objectMapper));
        return job;
    }

    @Nested
    class RepublishStaleQueued {

        @Test
        void shouldRepublishStaleQueuedJobs() {
            UUID jobId1 = UUID.randomUUID();
            UUID jobId2 = UUID.randomUUID();
            AgentJob job1 = mockQueuedJob(jobId1, 1L);
            AgentJob job2 = mockQueuedJob(jobId2, 2L);

            when(jobRepository.findStaleQueuedJobs(any())).thenReturn(List.of(job1, job2));

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
            AgentJob job1 = mockQueuedJob(jobId1, 1L);
            AgentJob job2 = mockQueuedJob(jobId2, 2L);

            when(jobRepository.findStaleQueuedJobs(any())).thenReturn(List.of(job1, job2));
            // First publish fails, second should still be attempted
            org.mockito.Mockito.doThrow(new RuntimeException("NATS down")).when(submitter).publish(jobId1, 1L);

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
            AgentJob job = mockRunningJob(jobId, Instant.now().minusSeconds(1200), 600);

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
            AgentJob job = mockRunningJob(jobId, Instant.now().minusSeconds(300), 600);

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
