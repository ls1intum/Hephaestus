package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.observability.StructuredLogKeys;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
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
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Recovers lost workers and times out attempts through fenced, per-job transactions. */
@ConditionalOnServerRole
@Component
@ConditionalOnBooleanProperty(RuntimeRole.AGENT_ENABLED_PROPERTY)
@WorkspaceAgnostic("Zombie sweeper operates across all workspaces")
public class ReviewJobZombieSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobZombieSweeper.class);

    private static final Duration RUNNING_BUFFER = SandboxLayout.RESULT_UPLOAD_GRACE.plusMinutes(5);

    /** Grace before a RUNNING job is judged orphaned, so a (re)started worker can write its first heartbeat. */
    private static final Duration ORPHAN_STARTUP_GRACE = Duration.ofMinutes(2);

    /**
     * Must stay well above {@link AgentProperties#WORKER_LEASE_TTL} so a dead worker's jobs are requeued
     * before its registry row goes.
     */
    private static final Duration STALE_REGISTRATION_TTL = Duration.ofHours(1);

    /** Timeout applied when a job's frozen snapshot cannot be read. */
    private static final int FALLBACK_TIMEOUT_SECONDS = 600;

    private final ReviewJobRepository jobRepository;
    private final ReviewJobRecovery recovery;
    private final WorkerRegistryRepository workerRegistryRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final LlmUsageRecorder usageRecorder;
    private final Counter zombieReaped;
    private final Counter orphanRequeued;
    private final Counter orphanFailed;
    private final Counter snapshotUnreadable;
    private final ReviewJobTelemetry jobTelemetry;

    @Autowired
    public ReviewJobZombieSweeper(
            ReviewJobRepository jobRepository,
            WorkerRegistryRepository workerRegistryRepository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            LlmUsageRecorder usageRecorder,
            MeterRegistry meterRegistry,
            ReviewJobTelemetry jobTelemetry,
            ReviewJobRecovery recovery) {
        this.jobRepository = jobRepository;
        this.recovery = recovery;
        this.workerRegistryRepository = workerRegistryRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.usageRecorder = usageRecorder;
        this.zombieReaped = Counter.builder(AgentMetrics.AGENT_JOB_ZOMBIE_REAPED)
                .description("Stale RUNNING jobs marked as TIMED_OUT")
                .register(meterRegistry);
        this.orphanRequeued = Counter.builder(AgentMetrics.AGENT_JOB_ORPHAN_REQUEUED)
                .description("RUNNING jobs whose owning worker was lost, requeued for another worker")
                .register(meterRegistry);
        this.orphanFailed = Counter.builder(AgentMetrics.AGENT_JOB_ORPHAN_FAILED)
                .description("Orphaned jobs that hit the retry cap and were failed")
                .register(meterRegistry);
        this.snapshotUnreadable = Counter.builder(AgentMetrics.AGENT_JOB_SNAPSHOT_UNREADABLE)
                .description("Terminal accounting events whose config snapshot could not be read; billed UNPRICED")
                .register(meterRegistry);
        this.jobTelemetry = jobTelemetry;
    }

    /**
     * Absolute-timeout backstop for the case where {@link #recoverOrphanedJobs} cannot run at all: mark
     * RUNNING jobs {@code TIMED_OUT} once they exceed their per-job timeout + {@link #RUNNING_BUFFER}.
     */
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void reapStaleRunningJobs() {
        Instant conservativeCutoff = Instant.now().minus(RUNNING_BUFFER);
        List<ReviewJob> staleJobs = jobRepository.findStaleRunningJobs(conservativeCutoff);

        if (staleJobs.isEmpty()) {
            return;
        }

        // Per-job transaction, never a method-level @Transactional: one poison job must not roll back
        // the jobs the batch already reaped (and their ledger events) at commit time.
        for (ReviewJob job : staleJobs) {
            try {
                ReviewJob reaped = transactionTemplate.execute(status -> reapIfStale(job.getId()));
                if (reaped != null) {
                    // The sweeper thread has no ambient trace context; restore the job's own trace ID
                    // (with a fresh span so no stale scheduler-thread value pairs with it) so the
                    // terminal event joins the lifecycle it closes.
                    MDC.put(StructuredLogKeys.TRACE_ID, reaped.getTraceId());
                    MDC.put(StructuredLogKeys.SPAN_ID, ReviewJobExecutor.randomSpanId());
                    try {
                        jobTelemetry.terminal(reaped, ReviewJobStatus.TIMED_OUT, ReviewJobTelemetry.age(reaped));
                    } finally {
                        MDC.remove(StructuredLogKeys.TRACE_ID);
                        MDC.remove(StructuredLogKeys.SPAN_ID);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to reap stale job: jobId={}, error={}", job.getId(), e.getMessage());
            }
        }
    }

    private @Nullable ReviewJob reapIfStale(UUID jobId) {
        ReviewJob lockedJob = jobRepository.lockById(jobId).orElse(null);
        if (lockedJob == null || lockedJob.getStatus() != ReviewJobStatus.RUNNING) return null;
        int timeoutSeconds = getTimeoutFromSnapshot(lockedJob);
        Duration maxLifetime = Duration.ofSeconds(timeoutSeconds).plus(RUNNING_BUFFER);
        Instant deadlineAnchor = lockedJob.getExecutionStartedAt() != null
                ? lockedJob.getExecutionStartedAt()
                : lockedJob.getStartedAt();
        if (deadlineAnchor != null && deadlineAnchor.plus(maxLifetime).isAfter(Instant.now())) {
            return null;
        }

        int updated = jobRepository.transitionStatus(
                jobId,
                ReviewJobStatus.TIMED_OUT,
                Instant.now(),
                "The job exceeded its timeout. The executor may have crashed.",
                Set.of(ReviewJobStatus.RUNNING));

        if (updated > 0) {
            recordUnverifiableUsage(lockedJob);
            zombieReaped.increment();
            log.warn("Reaped stale RUNNING job: jobId={}, startedAt={}", jobId, lockedJob.getStartedAt());
            return lockedJob;
        }
        return null;
    }

    /**
     * Requeue RUNNING jobs whose owning worker stopped heartbeating, so a sibling picks them up instead
     * of waiting out the full job timeout; jobs past the retry cap are failed instead.
     */
    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS, initialDelay = 30)
    public void recoverOrphanedJobs() {
        List<OrphanedJobRef> orphans = jobRepository.findOrphanedRunningJobs(
                Instant.now().minus(ORPHAN_STARTUP_GRACE), AgentProperties.WORKER_LEASE_TTL.toSeconds());
        if (orphans.isEmpty()) {
            return;
        }
        log.warn("Found {} orphaned RUNNING job(s) (owning worker lost); recovering", orphans.size());
        for (OrphanedJobRef orphan : orphans) {
            try {
                ReviewJobRecovery.Outcome outcome = transactionTemplate.execute(
                        s -> recovery.recover(orphan.getJobId(), orphan.getWorkerId(), orphan.getAttempt(), null));
                if (outcome == ReviewJobRecovery.Outcome.REQUEUED) {
                    orphanRequeued.increment();
                    log.warn("Requeued orphaned job {} (retry {})", orphan.getJobId(), orphan.getAttempt() + 1);
                } else if (outcome == ReviewJobRecovery.Outcome.FAILED) {
                    orphanFailed.increment();
                    log.warn("Orphaned job {} failed at attempt {}", orphan.getJobId(), orphan.getAttempt());
                }
            } catch (Exception e) {
                log.warn("Failed to recover orphaned job {}: {}", orphan.getJobId(), e.getMessage());
            }
        }
    }

    /**
     * Bound {@code worker_registry} growth: no worker ever deletes its own row (see
     * {@link WorkerLivenessReporter#stop()}), so every departed worker is reaped here once its heartbeat
     * has been stale for {@link #STALE_REGISTRATION_TTL}.
     */
    @Scheduled(fixedDelay = 60, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void purgeStaleWorkerRegistrations() {
        Integer removed = transactionTemplate.execute(
                s -> workerRegistryRepository.deleteStale(STALE_REGISTRATION_TTL.toSeconds()));
        if (removed != null && removed > 0) {
            log.info("Purged {} stale worker_registry row(s)", removed);
        }
    }

    /** A corrupt snapshot must not prevent terminal recovery. */
    private @Nullable ConfigSnapshot parseSnapshot(ReviewJob job) {
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

    private int getTimeoutFromSnapshot(ReviewJob job) {
        ConfigSnapshot snapshot = parseSnapshot(job);
        return snapshot != null ? snapshot.timeoutSeconds() : FALLBACK_TIMEOUT_SECONDS;
    }

    private void recordUnverifiableUsage(ReviewJob job) {
        recordUnverifiableUsage(
                job,
                job.getExecutionStartedAt() != null
                        ? jobRepository.findLlmUsageById(job.getId()).orElse(null)
                        : null);
    }

    /**
     * Bill a reaped attempt from token counts the caller read BEFORE any requeue of the job (see
     * {@link ReviewJobRepository#requeueOrphan}, which zeroes the row's accumulators).
     *
     * <p><b>Must not throw.</b> Every caller runs it inside the same transaction as the state
     * transition it accompanies, so an exception here rolls that transition back and the job returns to
     * RUNNING to fail identically on the next sweep, forever.
     */
    private void recordUnverifiableUsage(ReviewJob job, @Nullable ReviewJobLlmUsage counts) {
        if (job.getExecutionStartedAt() == null) return;
        ConfigSnapshot snapshot = parseSnapshot(job);
        if (snapshot == null) {
            snapshotUnreadable.increment();
        }
        LlmPriceSnapshot price = snapshot != null && snapshot.model().priceSnapshot() != null
                ? snapshot.model().priceSnapshot()
                : LlmPriceSnapshot.unpricedInstance();
        TerminalUsage.fromProxy(counts)
                .appendTo(usageRecorder, jobRepository, job.getWorkspaceId(), job, snapshot, price);
    }
}
