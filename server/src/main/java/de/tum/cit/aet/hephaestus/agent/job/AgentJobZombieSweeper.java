package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Periodic recovery for orphaned agent jobs. Runs on the server role only (relies on
 * {@code @EnableScheduling}, which is server-scoped); reads {@code worker_registry} written by workers.
 *
 * <p>Two sweeps (a third — re-publishing stale QUEUED jobs the NATS consumer never picked up — is
 * obsolete now that the queue is the {@code agent_job} table itself: a QUEUED row is always visible
 * to the next poll, there is nothing to re-publish):
 * <ol>
 *   <li><b>Orphan requeue</b> (every 20s): requeues RUNNING jobs whose owning worker's heartbeat went
 *       stale, so a sibling picks them up on its next poll rather than waiting out the full timeout.</li>
 *   <li><b>Stale RUNNING</b> (every 2 min): the absolute-timeout backstop — marks RUNNING jobs
 *       {@code TIMED_OUT} once they exceed their timeout + buffer.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Zombie sweeper operates across all workspaces")
public class AgentJobZombieSweeper {

    private static final Logger log = LoggerFactory.getLogger(AgentJobZombieSweeper.class);

    private static final Duration RUNNING_BUFFER = Duration.ofMinutes(5);

    /**
     * A worker is "alive" if it self-reported within this window. ~2.4× the worker's liveness
     * heartbeat cadence ({@code hephaestus.agent.heartbeat-interval}, 25s), so a couple of
     * dropped heartbeats don't falsely declare a live worker dead.
     */
    private static final Duration WORKER_LEASE_TTL = Duration.ofSeconds(60);

    /**
     * Startup grace: only consider a RUNNING job orphaned once it has been running longer than this,
     * giving a freshly-(re)connected worker time to write its first heartbeat before we reason about
     * its liveness.
     */
    private static final Duration ORPHAN_STARTUP_GRACE = Duration.ofSeconds(120);

    /** Registrations older than this are purged (≫ the orphan lease, so live jobs are recovered first). */
    private static final Duration STALE_REGISTRATION_TTL = Duration.ofHours(1);

    private final AgentJobRepository jobRepository;
    private final WorkerRegistryRepository workerRegistryRepository;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Counter zombieReaped;
    private final Counter orphanRequeued;
    private final Counter orphanFailed;

    public AgentJobZombieSweeper(
        AgentJobRepository jobRepository,
        WorkerRegistryRepository workerRegistryRepository,
        AgentProperties agentProperties,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.workerRegistryRepository = workerRegistryRepository;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.zombieReaped = Counter.builder("agent.job.zombie.reaped")
            .description("Stale RUNNING jobs marked as TIMED_OUT")
            .register(meterRegistry);
        this.orphanRequeued = Counter.builder("agent.job.orphan.requeued")
            .description("RUNNING jobs whose owning worker was lost, requeued for another worker")
            .register(meterRegistry);
        this.orphanFailed = Counter.builder("agent.job.orphan.failed")
            .description("Orphaned jobs that hit the retry cap and were failed")
            .register(meterRegistry);
    }

    /**
     * Absolute-timeout backstop: mark RUNNING jobs {@code TIMED_OUT} once they exceed their per-job
     * timeout + buffer. This is the last resort for the case where {@link #recoverOrphanedJobs} can't
     * run (e.g. its node is down); in normal operation the faster heartbeat-driven orphan sweep (20s)
     * requeues a dead worker's jobs long before this 2-minute reaper's {@code timeout + 5min} cutoff
     * fires, so the two don't fight over dead-worker jobs — they're separated by timing.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void reapStaleRunningJobs() {
        // Use the buffer as cutoff: any job running longer than 5 minutes is worth checking.
        // The per-job timeout from configSnapshot determines the actual staleness.
        Instant conservativeCutoff = Instant.now().minus(RUNNING_BUFFER);
        List<AgentJob> staleJobs = jobRepository.findStaleRunningJobs(conservativeCutoff);

        if (staleJobs.isEmpty()) {
            return;
        }

        for (AgentJob job : staleJobs) {
            try {
                int timeoutSeconds = getTimeoutFromSnapshot(job);
                Duration maxLifetime = Duration.ofSeconds(timeoutSeconds).plus(RUNNING_BUFFER);
                if (job.getStartedAt() != null && job.getStartedAt().plus(maxLifetime).isAfter(Instant.now())) {
                    continue; // Not stale yet for this specific job's timeout
                }

                int updated = jobRepository.transitionStatus(
                    job.getId(),
                    AgentJobStatus.TIMED_OUT,
                    Instant.now(),
                    "Reaped: exceeded timeout (executor may have crashed)",
                    Set.of(AgentJobStatus.RUNNING)
                );

                if (updated > 0) {
                    zombieReaped.increment();
                    log.warn("Reaped stale RUNNING job: jobId={}, startedAt={}", job.getId(), job.getStartedAt());
                }
            } catch (Exception e) {
                log.warn("Failed to reap stale job: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }
    }

    /**
     * Fast orphan recovery: requeue RUNNING jobs whose owning worker stopped heartbeating
     * (crash / partition / kill), so a sibling worker picks them up on its next poll instead of waiting
     * out the full job timeout. CAS-guarded so concurrent sweepers on multiple replicas can't
     * double-requeue. Jobs past the retry cap are failed. Runs more often than the absolute-timeout
     * reaper because heartbeat loss is detectable far sooner than timeout expiry.
     *
     * <p>Unlike the sibling sweep this is not method-{@code @Transactional}: each job's CAS runs in
     * its own transaction so one poison job can't roll back the batch.
     */
    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS, initialDelay = 30)
    public void recoverOrphanedJobs() {
        List<OrphanedJobRef> orphans = jobRepository.findOrphanedRunningJobs(
            Instant.now().minus(ORPHAN_STARTUP_GRACE),
            WORKER_LEASE_TTL.toSeconds()
        );
        if (orphans.isEmpty()) {
            return;
        }
        log.warn("Found {} orphaned RUNNING job(s) (owning worker lost); recovering", orphans.size());
        for (OrphanedJobRef orphan : orphans) {
            try {
                // DB retry_count is the authoritative cross-requeue budget — a requeued job simply
                // becomes QUEUED again and is picked up by the next poll from any live worker.
                if (orphan.getRetryCount() >= agentProperties.maxRetries()) {
                    Integer failed = transactionTemplate.execute(s ->
                        jobRepository.transitionStatus(
                            orphan.getJobId(),
                            AgentJobStatus.FAILED,
                            Instant.now(),
                            "Orphaned: owning worker lost and retry limit reached",
                            Set.of(AgentJobStatus.RUNNING)
                        )
                    );
                    if (failed != null && failed > 0) {
                        orphanFailed.increment();
                        log.warn(
                            "Orphaned job {} hit retry cap ({}); failed",
                            orphan.getJobId(),
                            orphan.getRetryCount()
                        );
                    }
                    continue;
                }
                Integer requeued = transactionTemplate.execute(s ->
                    jobRepository.requeueOrphan(orphan.getJobId(), orphan.getWorkerId(), agentProperties.maxRetries())
                );
                if (requeued != null && requeued > 0) {
                    orphanRequeued.increment();
                    log.warn("Requeued orphaned job {} (retry {})", orphan.getJobId(), orphan.getRetryCount() + 1);
                }
            } catch (Exception e) {
                log.warn("Failed to recover orphaned job {}: {}", orphan.getJobId(), e.getMessage());
            }
        }
    }

    /**
     * Bound {@code worker_registry} growth: purge registrations whose heartbeat is long stale.
     * Workers that exit cleanly delete their own row; this reaps the rest — SIGKILLed workers and
     * {@code worker_id} churn (hostname-derived ids across pod restarts). The TTL is far longer than the
     * orphan lease, so jobs owned by such a worker are already requeued before its row is removed.
     */
    @Scheduled(fixedDelay = 60, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void purgeStaleWorkerRegistrations() {
        Integer removed = transactionTemplate.execute(s ->
            workerRegistryRepository.deleteStale(STALE_REGISTRATION_TTL.toSeconds())
        );
        if (removed != null && removed > 0) {
            log.info("Purged {} stale worker_registry row(s)", removed);
        }
    }

    private int getTimeoutFromSnapshot(AgentJob job) {
        try {
            if (job.getConfigSnapshot() != null) {
                ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
                return snapshot.timeoutSeconds();
            }
        } catch (Exception e) {
            log.debug("Could not parse config snapshot for job {}: {}", job.getId(), e.getMessage());
        }
        return 600; // Default 10 minutes
    }
}
