package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>The queue is the {@code agent_job} table itself, so a QUEUED row is always visible to the next
 * poll and nothing has to be re-dispatched. What can go wrong is a row stuck RUNNING, which is what
 * these two sweeps recover:
 * <ol>
 *   <li><b>Orphan requeue</b> (every 20s): requeues RUNNING jobs whose owning worker's heartbeat went
 *       stale, so a sibling picks them up on its next poll rather than waiting out the full timeout.</li>
 *   <li><b>Stale RUNNING</b> (every 2 min): the absolute-timeout backstop — marks RUNNING jobs
 *       {@code TIMED_OUT} once they exceed their timeout + buffer.</li>
 * </ol>
 *
 * <p><b>No {@code @SchedulerLock} on anything in this class, deliberately</b> — unlike the other
 * schedulers in the codebase. Every sweep here acts through a conditional UPDATE that names the state
 * it expects, so two replicas sweeping the same row produce one winner and one no-op; delivery
 * recovery additionally claims via an attempt-counter CAS BEFORE any external post. Any
 * {@code @Scheduled} method added here must earn that the same way, or take the lock.
 */
@ConditionalOnServerRole
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
     * longer than this — long enough that a normal in-flight delivery attempt (a
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
    private final LlmUsageRecorder usageRecorder;
    private final Counter zombieReaped;
    private final Counter orphanRequeued;
    private final Counter orphanFailed;
    private final Counter deliveryRecovered;
    private final Counter snapshotUnreadable;

    @Autowired
    public AgentJobZombieSweeper(
        AgentJobRepository jobRepository,
        WorkerRegistryRepository workerRegistryRepository,
        AgentProperties agentProperties,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        AgentJobLifecycleService lifecycleService,
        LlmUsageRecorder usageRecorder,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.workerRegistryRepository = workerRegistryRepository;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.lifecycleService = lifecycleService;
        this.usageRecorder = usageRecorder;
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
        // Mirrors mentor.in_flight.reaper.failure: the money path degraded rather than failing, so
        // there is no exception for an operator to find. A sustained rate means jobs are terminalising
        // with spend nobody can price — usually a snapshot written by a newer server than this one.
        this.snapshotUnreadable = Counter.builder("agent.job.snapshot.unreadable")
            .description("Terminal accounting events whose config snapshot could not be read; billed UNPRICED")
            .register(meterRegistry);
    }

    /**
     * Absolute-timeout backstop: mark RUNNING jobs {@code TIMED_OUT} once they exceed their per-job
     * timeout + buffer. This is the last resort for the case where {@link #recoverOrphanedJobs} can't
     * run (e.g. its node is down); in normal operation the faster heartbeat-driven orphan sweep (20s)
     * requeues a dead worker's jobs long before this 2-minute reaper's {@code timeout + 5min} cutoff
     * fires, so the two don't fight over dead-worker jobs — they're separated by timing.
     */
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void reapStaleRunningJobs() {
        // Use the buffer as cutoff: any job running longer than 5 minutes is worth checking.
        // The per-job timeout from configSnapshot determines the actual staleness.
        Instant conservativeCutoff = Instant.now().minus(RUNNING_BUFFER);
        List<AgentJob> staleJobs = jobRepository.findStaleRunningJobs(conservativeCutoff);

        if (staleJobs.isEmpty()) {
            return;
        }

        // Each job reaped in its OWN transaction, like the orphan sweep below. A method-level
        // @Transactional here would not survive its own catch block: a constraint violation inside the
        // loop marks the transaction rollback-only, so the catch resumes, the loop finishes, and the
        // commit then throws UnexpectedRollbackException — discarding every job the batch had already
        // reaped, ledger events included. Per-job boundaries make one poison job cost one job.
        for (AgentJob job : staleJobs) {
            try {
                transactionTemplate.executeWithoutResult(status -> reapIfStale(job.getId()));
            } catch (Exception e) {
                log.warn("Failed to reap stale job: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }
    }

    private void reapIfStale(UUID jobId) {
        AgentJob lockedJob = jobRepository.findByIdWithWorkspaceForUpdate(jobId).orElse(null);
        if (lockedJob == null || lockedJob.getStatus() != AgentJobStatus.RUNNING) return;
        int timeoutSeconds = getTimeoutFromSnapshot(lockedJob);
        Duration maxLifetime = Duration.ofSeconds(timeoutSeconds).plus(RUNNING_BUFFER);
        if (lockedJob.getStartedAt() != null && lockedJob.getStartedAt().plus(maxLifetime).isAfter(Instant.now())) {
            return; // Not stale yet for this specific job's timeout
        }

        int updated = jobRepository.transitionStatus(
            jobId,
            AgentJobStatus.TIMED_OUT,
            Instant.now(),
            "Reaped: exceeded timeout (executor may have crashed)",
            Set.of(AgentJobStatus.RUNNING)
        );

        if (updated > 0) {
            recordUnverifiableUsage(lockedJob);
            zombieReaped.increment();
            log.warn("Reaped stale RUNNING job: jobId={}, startedAt={}", jobId, lockedJob.getStartedAt());
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
                    Integer failed = transactionTemplate.execute(s -> {
                        AgentJob job = jobRepository.findByIdWithWorkspaceForUpdate(orphan.getJobId()).orElse(null);
                        if (job == null) return 0;
                        int rows = jobRepository.transitionStatus(
                            orphan.getJobId(),
                            AgentJobStatus.FAILED,
                            Instant.now(),
                            "Orphaned: owning worker lost and retry limit reached",
                            Set.of(AgentJobStatus.RUNNING)
                        );
                        if (rows > 0) recordUnverifiableUsage(job);
                        return rows;
                    });
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
                // backoff-computed available_at + a rotated job token — see
                // AgentJobExecutor#requeueOrphanWithRotation's javadoc (mirrored here since the sweeper
                // and executor are independent CAS callers of the same requeueOrphan query).
                int attemptNumber = orphan.getRetryCount() + 1;
                Instant availableAt = Instant.now().plus(AgentJobBackoff.compute(attemptNumber));
                String newToken = AgentJob.generateJobToken();
                String newTokenHash = AgentJob.computeTokenHash(newToken);
                Integer requeued = transactionTemplate.execute(s -> {
                    AgentJob job = jobRepository.findByIdWithWorkspaceForUpdate(orphan.getJobId()).orElse(null);
                    if (job == null) return 0;
                    // Snapshot the token counts BEFORE requeuing: requeueOrphan zeroes the row's
                    // accumulators atomically, so a post-requeue re-read would bill zero.
                    AgentJobLlmUsage counts =
                        job.getExecutionStartedAt() != null
                            ? jobRepository.findLlmUsageById(job.getId()).orElse(null)
                            : null;
                    int rows = jobRepository.requeueOrphan(
                        orphan.getJobId(),
                        orphan.getWorkerId(),
                        agentProperties.maxRetries(),
                        availableAt,
                        newToken,
                        newTokenHash
                    );
                    if (rows > 0) recordUnverifiableUsage(job, counts);
                    return rows;
                });
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
     * Delivery-recovery sweep: re-attempts delivery for jobs stuck at
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
                // The CAS above incremented delivery_attempts from expectedAttempts to
                // expectedAttempts + 1 — that post-increment value is THIS attempt's fence token for its
                // terminal write (see AgentJobLifecycleService#recoverStuckDelivery).
                short claimedAttempts = (short) (expectedAttempts + 1);
                boolean delivered = lifecycleService.recoverStuckDelivery(job, claimedAttempts);
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

    /**
     * Read a job's frozen {@link ConfigSnapshot}, or {@code null} when it cannot be read.
     *
     * <p>The ONE place this sweeper parses a snapshot, because the two things it reads out of one —
     * the timeout and the price — are needed on the same job in the same pass and must agree on
     * whether an unreadable snapshot is fatal. It is never: a snapshot the sweeper cannot read is a
     * fact about a job that is already being taken off the queue, and refusing to take it off would
     * leave it RUNNING forever.
     *
     * <p>Unreadable is a real, expected state, not a corrupt row. {@link ConfigSnapshot#fromJson}
     * deliberately rejects a snapshot written by a NEWER schema version, so a reverted rolling deploy
     * leaves behind jobs this server cannot read but a canary pod could. Those jobs still have to
     * terminalise here.
     */
    private @Nullable ConfigSnapshot parseSnapshot(AgentJob job) {
        if (job.getConfigSnapshot() == null) {
            return null;
        }
        try {
            return ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        } catch (RuntimeException e) {
            log.warn("Could not read config snapshot for job {}: {}", job.getId(), e.getMessage());
            return null;
        }
    }

    private int getTimeoutFromSnapshot(AgentJob job) {
        ConfigSnapshot snapshot = parseSnapshot(job);
        return snapshot != null ? snapshot.timeoutSeconds() : 600; // Default 10 minutes
    }

    private void recordUnverifiableUsage(AgentJob job) {
        recordUnverifiableUsage(
            job,
            job.getExecutionStartedAt() != null ? jobRepository.findLlmUsageById(job.getId()).orElse(null) : null
        );
    }

    /**
     * Bill a reaped orphan from token counts the caller captured itself. The {@code counts} MUST be
     * read BEFORE any requeue of this job — {@link AgentJobRepository#requeueOrphan} atomically zeroes
     * the row's accumulators, so a post-requeue re-read would drop the reaped attempt's spend.
     *
     * <p><b>This method must not throw.</b> Every caller runs it inside the same transaction as the
     * state transition it accompanies, so an exception here does not merely skip the ledger event — it
     * rolls back the TIMED_OUT/FAILED/requeue write that the caller already made. The job returns to
     * RUNNING, the next sweep reaches the same line, and it fails identically forever, burning a
     * {@code (workspace, purpose)} concurrency slot for as long as the row exists.
     */
    private void recordUnverifiableUsage(AgentJob job, @Nullable AgentJobLlmUsage counts) {
        if (job.getExecutionStartedAt() == null) return;
        ConfigSnapshot snapshot = parseSnapshot(job);
        if (snapshot == null) {
            snapshotUnreadable.increment();
        }
        // Jobs that started before admission snapshots were introduced — and jobs whose snapshot this
        // server cannot read at all — still need to be recovered. Their spend cannot be reconstructed
        // safely, so preserve the state transition and append an explicit instance-funded UNPRICED
        // event rather than either inventing a cost or rolling back.
        LlmPriceSnapshot price =
            snapshot != null && snapshot.priceSnapshot() != null
                ? snapshot.priceSnapshot()
                : LlmPriceSnapshot.unpricedInstance();
        // A reaped zombie made real, priced calls through the proxy before it was abandoned — bill
        // them from the tokens the proxy attributed to the row (captured pre-requeue) instead of
        // recording zero cost.
        TerminalUsage.resolve(null, counts).appendTo(
            usageRecorder,
            job.getWorkspace().getId(),
            job,
            snapshot != null ? snapshot.upstreamModelId() : null,
            price
        );
    }
}
