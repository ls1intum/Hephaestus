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
import org.springframework.data.domain.PageRequest;
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

    /**
     * A COMPLETED job's delivery is considered stuck once it has sat at {@code delivery_status=PENDING}
     * longer than this (#1368 hardening) — long enough that a normal in-flight delivery attempt (a
     * couple of GraphQL calls, seconds) would have finished; anything still PENDING past this point is
     * presumed to be a crash between the terminal write (which sets PENDING) and delivery finishing.
     */
    private static final Duration DELIVERY_PENDING_STUCK_THRESHOLD = Duration.ofMinutes(10);

    /** Bounded delivery-recovery attempts before a stuck PENDING delivery is given up on (marked FAILED). */
    static final int MAX_DELIVERY_RECOVERY_ATTEMPTS = 3;

    /** Cap on how many stuck deliveries one sweep pass loads, so a large backlog can't blow up one pass. */
    private static final int DELIVERY_RECOVERY_BATCH_SIZE = 50;

    private final AgentJobRepository jobRepository;
    private final WorkerRegistryRepository workerRegistryRepository;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AgentJobLifecycleService lifecycleService;
    private final Counter zombieReaped;
    private final Counter orphanRequeued;
    private final Counter orphanFailed;
    private final Counter deliveryRecovered;

    public AgentJobZombieSweeper(
        AgentJobRepository jobRepository,
        WorkerRegistryRepository workerRegistryRepository,
        AgentProperties agentProperties,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        AgentJobLifecycleService lifecycleService,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.workerRegistryRepository = workerRegistryRepository;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.lifecycleService = lifecycleService;
        this.zombieReaped = Counter.builder("agent.job.zombie.reaped")
            .description("Stale RUNNING jobs marked as TIMED_OUT")
            .register(meterRegistry);
        this.orphanRequeued = Counter.builder("agent.job.orphan.requeued")
            .description("RUNNING jobs whose owning worker was lost, requeued for another worker")
            .register(meterRegistry);
        this.orphanFailed = Counter.builder("agent.job.orphan.failed")
            .description("Orphaned jobs that hit the retry cap and were failed")
            .register(meterRegistry);
        this.deliveryRecovered = Counter.builder("agent.job.delivery.recovered")
            .description("Stuck PENDING deliveries successfully re-attempted by the recovery sweep")
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
                // #1368 hardening: backoff-computed available_at + a rotated job token — see
                // AgentJobExecutor#requeueOrphanWithRotation's javadoc (mirrored here since the sweeper
                // and executor are independent CAS callers of the same requeueOrphan query).
                int attemptNumber = orphan.getRetryCount() + 1;
                Instant availableAt = Instant.now().plus(AgentJobBackoff.compute(attemptNumber));
                String newToken = AgentJob.generateJobToken();
                String newTokenHash = AgentJob.computeTokenHash(newToken);
                Integer requeued = transactionTemplate.execute(s ->
                    jobRepository.requeueOrphan(
                        orphan.getJobId(),
                        orphan.getWorkerId(),
                        agentProperties.maxRetries(),
                        availableAt,
                        newToken,
                        newTokenHash
                    )
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
     * Delivery-recovery sweep (#1368 hardening): re-attempts delivery for jobs stuck at
     * {@code delivery_status=PENDING} — the executor crashed between the terminal-write transaction
     * (which sets PENDING) and finishing the actual delivery, so {@link AgentJobLifecycleService#retryDelivery}
     * (which requires the FAILED CAS source) cannot reach them. Bounded by {@link #MAX_DELIVERY_RECOVERY_ATTEMPTS}
     * — once exhausted, the delivery is marked FAILED terminally so it does not sit PENDING forever, and
     * so a human can retry it through the normal (FAILED-sourced) retry endpoint if desired.
     *
     * <p>Each candidate's attempt-counter CAS ({@link AgentJobRepository#claimDeliveryRecoveryAttempt})
     * guards against two sweeper replicas racing the same stuck job.
     */
    @Scheduled(fixedDelay = 5, initialDelay = 3, timeUnit = TimeUnit.MINUTES)
    public void recoverStuckDeliveries() {
        Instant cutoff = Instant.now().minus(DELIVERY_PENDING_STUCK_THRESHOLD);
        List<AgentJob> stuck = jobRepository.findStuckPendingDeliveries(
            cutoff,
            PageRequest.of(0, DELIVERY_RECOVERY_BATCH_SIZE)
        );
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("Found {} agent job(s) stuck at delivery_status=PENDING; attempting recovery", stuck.size());
        for (AgentJob job : stuck) {
            try {
                if (job.getDeliveryAttempts() >= MAX_DELIVERY_RECOVERY_ATTEMPTS) {
                    transactionTemplate.executeWithoutResult(s ->
                        jobRepository.updateDeliveryStatus(
                            job.getId(),
                            DeliveryStatus.FAILED,
                            job.getDeliveryCommentId()
                        )
                    );
                    log.warn(
                        "Delivery recovery exhausted after {} attempt(s); marking FAILED: jobId={}",
                        job.getDeliveryAttempts(),
                        job.getId()
                    );
                    continue;
                }
                short expectedAttempts = job.getDeliveryAttempts();
                Integer claimed = transactionTemplate.execute(s ->
                    jobRepository.claimDeliveryRecoveryAttempt(job.getId(), expectedAttempts)
                );
                if (claimed == null || claimed == 0) {
                    continue; // a concurrent sweeper replica already claimed this pass's attempt
                }
                boolean delivered = lifecycleService.recoverStuckDelivery(job);
                if (delivered) {
                    deliveryRecovered.increment();
                }
            } catch (Exception e) {
                log.warn("Delivery recovery pass failed for job {}: {}", job.getId(), e.getMessage());
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
