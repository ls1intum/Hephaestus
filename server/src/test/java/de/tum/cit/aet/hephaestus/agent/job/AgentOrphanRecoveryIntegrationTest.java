package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof of multi-replica orphan recovery against REAL Postgres (#1138, rewritten for the
 * #1368 NATS→Postgres cutover) — the path mocks can't cover: a dead worker's RUNNING job is detected,
 * CAS-requeued back to QUEUED, and becomes claimable by a live poller.
 *
 * <p>The queue IS the {@code agent_job} table now, so there is no separate transport to verify a
 * republish onto — recovery is proven entirely through row state: {@link AgentJobZombieSweeper}
 * requeues the orphan (RUNNING → QUEUED, ownership cleared, {@code retry_count} bumped), and
 * {@link AgentJobExecutor#processJob} (same package, called directly for a deterministic assertion
 * instead of racing the background poll loop) proves the requeued row is actually claimable again.
 *
 * <p>{@code hephaestus.agent.poll-interval} is set to an hour so the executor's own background poll
 * thread stays quiescent for the duration of the test — every claim in this test is driven explicitly.
 * The Docker host points at a socket that can never exist so the worker-role sandbox beans wire
 * (lazy clients, no I/O at construction) without needing a real Docker daemon; any resulting async
 * sandbox failure lands after this test's synchronous claim assertions, and is caught internally
 * (RUNNING → FAILED), never propagating.
 */
@DisplayName("Orphan recovery over PostgreSQL Integration")
class AgentOrphanRecoveryIntegrationTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        registry.add("hephaestus.agent.enabled", () -> "true");
        registry.add("hephaestus.agent.poll-interval", () -> "1h");
        registry.add("hephaestus.agent.max-retries", () -> "5");
        // application-test.yml turns the worker role off by default; AgentJobExecutor needs it on.
        registry.add("hephaestus.runtime.worker.enabled", () -> "true");
        registry.add("hephaestus.sandbox.docker-host", () ->
            "unix:///nonexistent/hephaestus-test-orphan-recovery.sock"
        );
        registry.add("hephaestus.agent.image.pull-policy", () -> "NEVER");
    }

    @Autowired
    private AgentJobZombieSweeper sweeper;

    @Autowired
    private AgentJobExecutor executor;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("orphan-recovery-ws"));
    }

    @Test
    @DisplayName("a dead worker's RUNNING job is requeued (retry_count++) and becomes claimable again")
    void orphanRecoveryRequeuesAndBecomesClaimable() {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        // DB: the job is back on the queue, ownership cleared, retry bumped.
        AgentJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getWorkerId()).isNull();
        assertThat(requeued.getRetryCount()).isEqualTo(1);

        // It is a genuine poll candidate — oldest-first, id-only projection.
        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).contains(jobId);

        // A live poller can claim it: processJob's SKIP LOCKED claim wins and flips it RUNNING.
        boolean claimed = executor.processJob(jobId);
        assertThat(claimed).isTrue();

        AgentJob reclaimed = jobRepository.findById(jobId).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(AgentJobStatus.RUNNING);
        assertThat(reclaimed.getRetryCount()).isEqualTo(1); // claim itself doesn't touch retry_count
    }

    @Test
    @DisplayName("an orphan already at the retry cap is failed, not requeued again")
    void orphanPastRetryCapIsFailedNotRequeued() {
        UUID jobId = runningJobOwnedBy("dead-replica-2", Instant.now().minus(Duration.ofMinutes(5)), 5);
        registerStaleWorker("dead-replica-2", Instant.now().minus(Duration.ofMinutes(5)));

        sweeper.recoverOrphanedJobs();

        AgentJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AgentJobStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("retry limit reached");
        // Never went back through QUEUED — no poll candidate left behind.
        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).doesNotContain(jobId);
    }

    // ── helpers ──

    private UUID runningJobOwnedBy(String workerId, Instant startedAt, int retryCount) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(objectMapper.createObjectNode());
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        job.setRetryCount(retryCount);
        return jobRepository.saveAndFlush(job).getId();
    }

    private void registerStaleWorker(String workerId, Instant lastHeartbeat) {
        WorkerRegistry w = new WorkerRegistry();
        w.setWorkerId(workerId);
        w.setLastHeartbeat(lastHeartbeat);
        w.setRegisteredAt(lastHeartbeat);
        workerRegistryRepository.saveAndFlush(w);
    }
}
