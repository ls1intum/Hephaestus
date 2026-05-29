package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres proof of the multi-replica orphan-recovery SQL (#1138) — the logic that makes
 * running more than one worker replica safe. Mock-based unit tests cannot exercise the native
 * liveness query (DB-clock {@code now()}, {@code NOT EXISTS} on {@code worker_registry}) or the CAS
 * {@code requeueOrphan}, so the actual coordination guarantees live here.
 */
@DisplayName("Orphan recovery SQL (Postgres) Integration")
class WorkerRegistryOrphanRecoveryIntegrationTest extends BaseIntegrationTest {

    private static final long LEASE_TTL_SECONDS = 60;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // This context has worker.enabled=true and NO hephaestus.worker.control.endpoint — the exact
    // topology where orphan recovery used to silently no-op (Gap D). required=false so a regression
    // (identity bound only when WSS is configured) fails on the assertion, not on context load.
    @Autowired(required = false)
    private WorkerProperties workerProperties;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("orphan-ws"));
    }

    @Test
    @DisplayName("a RUNNING job whose owner heartbeats fresh is NOT orphaned")
    void aliveWorkerNotOrphaned() {
        runningJob("w-alive", Instant.now().minus(Duration.ofMinutes(5)));
        registerWorker("w-alive", Instant.now()); // fresh

        assertThat(findOrphans()).isEmpty();
    }

    @Test
    @DisplayName("a RUNNING job whose owner's heartbeat is stale IS orphaned")
    void staleWorkerOrphaned() {
        UUID id = runningJob("w-stale", Instant.now().minus(Duration.ofMinutes(5)));
        registerWorker("w-stale", Instant.now().minus(Duration.ofMinutes(5))); // stale

        assertThat(findOrphans()).extracting(OrphanedJobRef::getJobId).containsExactly(id);
    }

    @Test
    @DisplayName("a RUNNING job whose owner has no registry row IS orphaned")
    void missingWorkerOrphaned() {
        UUID id = runningJob("w-gone", Instant.now().minus(Duration.ofMinutes(5)));
        // no worker_registry row for w-gone

        assertThat(findOrphans()).extracting(OrphanedJobRef::getJobId).containsExactly(id);
    }

    @Test
    @DisplayName("a just-claimed job (within startup grace) is NOT orphaned even if the worker is unknown")
    void withinStartupGraceNotOrphaned() {
        runningJob("w-new", Instant.now().minus(Duration.ofSeconds(30))); // younger than the 2-min grace
        // no registry row — but grace must protect it

        List<OrphanedJobRef> orphans = jobRepository.findOrphanedRunningJobs(
            Instant.now().minus(Duration.ofMinutes(2)),
            LEASE_TTL_SECONDS
        );
        assertThat(orphans).isEmpty();
    }

    @Test
    @DisplayName("requeueOrphan is a CAS: first caller wins (1), the second is a no-op (0)")
    void requeueOrphanIsCasIdempotent() {
        UUID id = runningJob("w-stale", Instant.now().minus(Duration.ofMinutes(5)));

        // requeueOrphan is @Modifying — wrap in a tx, as the sweeper does.
        int firstWin = transactionTemplate.execute(s -> jobRepository.requeueOrphan(id));
        int secondWin = transactionTemplate.execute(s -> jobRepository.requeueOrphan(id));
        assertThat(firstWin).isEqualTo(1);
        assertThat(secondWin).isEqualTo(0); // already QUEUED — lost the race

        AgentJob requeued = jobRepository.findById(id).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        assertThat(requeued.getWorkerId()).isNull();
        assertThat(requeued.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteStale removes long-dead registrations but keeps fresh ones")
    void deleteStalePurgesDeadWorkers() {
        registerWorker("w-fresh", Instant.now());
        registerWorker("w-dead", Instant.now().minus(Duration.ofHours(2)));

        int removed = transactionTemplate.execute(s ->
            workerRegistryRepository.deleteStale(Duration.ofHours(1).toSeconds())
        );

        assertThat(removed).isEqualTo(1);
        assertThat(workerRegistryRepository.findById("w-fresh")).isPresent();
        assertThat(workerRegistryRepository.findById("w-dead")).isEmpty();
    }

    @Test
    @DisplayName("worker identity binds on the worker role WITHOUT a WSS endpoint (orphan recovery always armed)")
    void workerIdentityBoundWithoutWssEndpoint() {
        assertThat(workerProperties)
            .as("WorkerProperties must bind on the worker role independent of the WSS control endpoint")
            .isNotNull();
        String id = workerProperties.resolvedWorkerId();
        assertThat(id).isNotBlank();
        // Stable per process (the instance suffix is computed once) — both the executor's job-ownership
        // stamp and the WSS JWT subject rely on this being identical across calls.
        assertThat(id).isEqualTo(workerProperties.resolvedWorkerId());
    }

    // ── helpers ──

    private List<OrphanedJobRef> findOrphans() {
        // graceCutoff well in the past so the 5-min-old jobs are eligible; liveness is DB-clock.
        return jobRepository.findOrphanedRunningJobs(Instant.now().minus(Duration.ofMinutes(2)), LEASE_TTL_SECONDS);
    }

    private UUID runningJob(String workerId, Instant startedAt) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(objectMapper.createObjectNode());
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        return jobRepository.saveAndFlush(job).getId();
    }

    private void registerWorker(String workerId, Instant lastHeartbeat) {
        WorkerRegistry w = new WorkerRegistry();
        w.setWorkerId(workerId);
        w.setLastHeartbeat(lastHeartbeat);
        w.setRegisteredAt(lastHeartbeat);
        workerRegistryRepository.saveAndFlush(w);
    }
}
