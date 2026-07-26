package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeAgentRequest;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetDecision;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Polls the {@code agent_job} table for {@code QUEUED} work: the queue IS the table, so a QUEUED
 * insert is the enqueue. Claim runs synchronously on the poll thread; execution is handed to the
 * {@code sandboxExecutor} so the poll thread is never blocked by a running sandbox.
 *
 * <p>Transaction boundaries are deliberately narrow:
 * <ul>
 *   <li><b>Claim</b> (~5ms): SKIP LOCKED → set RUNNING → commit</li>
 *   <li><b>Execute</b> (minutes): No transaction, no DB connection held</li>
 *   <li><b>Complete</b> (~5ms): Set terminal status → commit</li>
 * </ul>
 */
@Component
// Wires when the agent job queue is enabled AND the worker role isn't explicitly disabled. Combined
// into @ConditionalOnExpression because Spring honors only ONE @ConditionalOnProperty per element.
@ConditionalOnExpression(
    "${" + RuntimeRole.AGENT_ENABLED_PROPERTY + ":false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}"
)
@WorkspaceAgnostic("Job poller processes jobs across all workspaces")
public class AgentJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentJobExecutor.class);

    private static final String MDC_JOB_ID = "agent.jobId";
    private static final String MDC_JOB_TYPE = "agent.jobType";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONTAINER_LOGS_CHARS = 65536; // 64KB
    // How long a claim-blocked job waits before the poll loop re-evaluates the cap, and the maximum
    // total time it may stay held before it is cancelled as stale.
    private static final Duration BUDGET_HOLD_INTERVAL = Duration.ofHours(1);
    private static final Duration BUDGET_HOLD_MAX_AGE = Duration.ofDays(7);

    private final AgentProperties agentProperties;
    private final AgentJobRepository jobRepository;
    private final WorkspaceAgentBindingRepository bindingRepository;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final PracticePiAdapter practiceAgent;
    private final SandboxManager sandboxManager;
    private final AsyncTaskExecutor sandboxExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final LlmUsageRecorder usageRecorder;
    private final LlmBudgetService llmBudgetService;
    private final @Nullable LlmAdmissionService llmAdmissionService;

    private final Counter concurrencyRejected;
    private final Timer claimLatency;
    private final Counter infraRetryRequeued;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread pollThread;
    private final Phaser inFlight = new Phaser(1); // 1 = the executor itself; deregistered on stop
    /**
     * Job ids this worker is currently executing. Drain and hub-initiated cancels act only on jobs in
     * this set, never on sibling workers' jobs, and the poll loop reads it as its free-capacity signal.
     */
    private final Set<UUID> localRunningJobs = ConcurrentHashMap.newKeySet();
    private final Optional<WorkerCapacityState> capacityState;
    private final Optional<WorkerProperties> workerProperties;
    /** Null only when the worker role is off; stamped on claimed jobs to fence terminal writes. */
    private final String workerId;
    /**
     * Poll-thread-owned: written and read only from the single poll thread (or, in tests, the single
     * calling thread), so no synchronization is needed.
     */
    private boolean lastClaimPoolRejected;

    @Autowired
    public AgentJobExecutor(
        AgentProperties agentProperties,
        AgentJobRepository jobRepository,
        WorkspaceAgentBindingRepository bindingRepository,
        JobTypeHandlerRegistry handlerRegistry,
        PracticePiAdapter practiceAgent,
        SandboxManager sandboxManager,
        @Qualifier("sandboxExecutor") AsyncTaskExecutor sandboxExecutor,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        LlmUsageRecorder usageRecorder,
        LlmBudgetService llmBudgetService,
        @Nullable LlmAdmissionService llmAdmissionService,
        Optional<WorkerCapacityState> capacityState,
        Optional<WorkerProperties> workerProperties
    ) {
        this.agentProperties = agentProperties;
        this.jobRepository = jobRepository;
        this.bindingRepository = bindingRepository;
        this.handlerRegistry = handlerRegistry;
        this.practiceAgent = practiceAgent;
        this.sandboxManager = sandboxManager;
        this.sandboxExecutor = sandboxExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.usageRecorder = usageRecorder;
        this.llmBudgetService = llmBudgetService;
        this.llmAdmissionService = llmAdmissionService;
        this.capacityState = capacityState;
        this.workerProperties = workerProperties;
        this.workerId = workerProperties.map(WorkerProperties::resolvedWorkerId).orElse(null);

        this.concurrencyRejected = Counter.builder("agent.job.concurrency.rejected")
            .description("Jobs rejected due to concurrency limits")
            .register(meterRegistry);
        this.claimLatency = Timer.builder("agent.job.claim.latency")
            .description("Time between a job becoming available (available_at) and being claimed")
            .register(meterRegistry);
        this.infraRetryRequeued = Counter.builder("agent.job.infra.retry.requeued")
            .description("Jobs requeued (not failed) after a classified sandbox-infrastructure failure")
            .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // After WorkerLivenessReporter.start() (@Order(1)), so the registry row exists pre-first-claim
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        pollThread = Thread.ofPlatform().name("agent-job-poll").daemon(true).start(this::pollLoop);

        log.info(
            "Agent job executor started: workerId={}, pollInterval={}, claimBatchSize={}",
            workerId,
            agentProperties.pollInterval(),
            agentProperties.claimBatchSize()
        );
    }

    /** Bound on how long {@link #stopAcceptingNewJobs()} waits for the poll thread to actually exit. */
    private static final Duration POLL_THREAD_JOIN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Stops the poll loop so no new jobs are claimed. Idempotent.
     *
     * <p>Joining the poll thread is what closes the drain admission race: a claim already in flight when
     * {@code running} flips false can otherwise still register with {@link #inFlight} <em>after</em> the
     * drain coordinator called {@link #awaitInFlight(Duration)}. Phaser puts that late registration in
     * the NEXT phase, so the coordinator's await on the old (already-complete) phase returns at once
     * believing drain was clean, and the late-claimed job is neither awaited nor cancelled.
     */
    public void stopAcceptingNewJobs() {
        running.set(false);
        Thread thread = pollThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(POLL_THREAD_JOIN_TIMEOUT.toMillis());
            if (thread.isAlive()) {
                log.warn(
                    "Poll thread did not stop within {} of stopAcceptingNewJobs() — a claim may still be in flight",
                    POLL_THREAD_JOIN_TIMEOUT
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait up to {@code timeout} for in-flight execution submissions to complete. Must be called
     * after {@link #stopAcceptingNewJobs()} so no new parties register.
     *
     * @return {@code true} if all in-flight work completed within {@code timeout}; {@code false}
     *     on timeout (caller should cancel remaining).
     */
    public boolean awaitInFlight(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return inFlight.getUnarrivedParties() <= 1; // only the executor party left
        }
        try {
            int phase = inFlight.arriveAndDeregister();
            inFlight.awaitAdvanceInterruptibly(phase, timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Stops the containers of the jobs THIS worker is currently running and hands each one back to the
     * queue for a sibling to pick up, per the drain contract in docs/admin/runtime-roles.mdx. Scoped to
     * {@link #localRunningJobs} so sibling workers' jobs are untouched.
     *
     * <p>A worker-fenced requeue is attempted first ({@link AgentJobRepository#requeueOrphan}, capped by
     * {@code max-retries}). Only when that CAS loses — the job left this worker's ownership, or the
     * retry cap is exhausted — does it fall back to a terminal cancel, so an exhausted job ends up
     * CANCELLED rather than requeued forever.
     */
    public void cancelInFlight(AgentJobCancellationReason reason) {
        Set<UUID> snapshot = Set.copyOf(localRunningJobs);
        if (snapshot.isEmpty()) return;
        log.info("Draining {} in-flight job(s) owned by this worker with reason {}", snapshot.size(), reason);
        for (UUID jobId : snapshot) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    AgentJob job = jobRepository.findByIdWithWorkspaceForUpdate(jobId).orElse(null);
                    if (job == null) return;
                    // Snapshot the token counts BEFORE requeuing: the requeue zeroes the row's
                    // accumulators atomically, so a post-requeue re-read would bill zero.
                    AgentJobLlmUsage drainCounts =
                        job.getExecutionStartedAt() != null ? jobRepository.findLlmUsageById(jobId).orElse(null) : null;
                    int updated =
                        workerId != null ? requeueOrphanWithRotation(jobId, workerId, job.getRetryCount()) : 0;
                    if (updated > 0) {
                        if (job.getExecutionStartedAt() != null) {
                            billTerminatedJob(job, "worker draining", drainCounts);
                        }
                        return;
                    }
                    int cancelled =
                        workerId != null
                            ? jobRepository.transitionToCancelledOwnedBy(
                                  jobId,
                                  Instant.now(),
                                  "worker draining",
                                  reason,
                                  Set.of(AgentJobStatus.RUNNING),
                                  workerId
                              )
                            : jobRepository.transitionToCancelled(
                                  jobId,
                                  Instant.now(),
                                  "worker draining",
                                  reason,
                                  Set.of(AgentJobStatus.RUNNING)
                              );
                    if (cancelled > 0 && job.getExecutionStartedAt() != null) {
                        billTerminatedJob(job, "worker draining");
                    }
                });
                sandboxManager.cancel(jobId);
            } catch (Exception e) {
                log.warn("Failed to drain in-flight job {}: {}", jobId, e.getClass().getSimpleName());
            }
        }
    }

    /**
     * The authoritative {@code agent_job} status transition is performed hub-side before the
     * {@code CancelJob} frame is dispatched; this only stops the container. No-op if this worker does
     * not own the job, which is how job-scoped cancellation stays safe across replicas.
     *
     * @return {@code true} if this worker owns the job and a stop was requested
     */
    public boolean cancelLocalJob(UUID jobId, String reason) {
        if (!localRunningJobs.contains(jobId)) {
            return false;
        }
        log.info("Hub-initiated cancel for locally-running job {}: {}", jobId, reason);
        try {
            sandboxManager.cancel(jobId);
            return true;
        } catch (Exception e) {
            log.warn("Local cancel failed for job {}: {}", jobId, e.getClass().getSimpleName());
            return false;
        }
    }

    /** Used when no {@code WorkerDrainCoordinator} owns the worker lifecycle (monolith mode). */
    @PreDestroy
    public void stop() {
        stopAcceptingNewJobs();
        awaitInFlight(Duration.ofSeconds(30));
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                int capacity = computeCapacity();
                if (capacity <= 0) {
                    sleepPollInterval();
                    continue;
                }

                List<UUID> candidates = jobRepository.findQueuedIdsOldestFirst(capacity);
                if (candidates.isEmpty()) {
                    sleepPollInterval();
                    continue;
                }

                boolean anyDispatched = false;
                boolean poolRejected = false;
                for (UUID jobId : candidates) {
                    if (!running.get()) {
                        break;
                    }
                    if (poolRejected) {
                        break;
                    }
                    lastClaimPoolRejected = false;
                    if (processJob(jobId)) {
                        if (lastClaimPoolRejected) {
                            poolRejected = true;
                        } else {
                            anyDispatched = true;
                        }
                    }
                }
                // Busy-spin protection: without this backoff, an all-skipped batch re-queries immediately
                // and a saturated pool claims-then-requeues in a tight loop, hammering the DB.
                if (!anyDispatched || poolRejected) {
                    sleepPollInterval();
                }
            } catch (Exception e) {
                log.warn("Poll loop error, retrying in {}: {}", agentProperties.pollInterval(), e.getMessage());
                sleepPollInterval();
            }
        }
        log.info("Agent job executor poll loop stopped");
    }

    /**
     * {@code WorkerCapacityState.reviewMax} and the sandbox executor's pool size are independently
     * configured and nothing enforces they agree, so the free-slot bound is what stops a reviewMax
     * larger than the pool from claiming jobs the pool then rejects.
     */
    int computeCapacity() {
        int poolCapacity = capacityState
            .map(cs -> Math.max(0, cs.reviewMax() - localRunningJobs.size()))
            .orElse(agentProperties.claimBatchSize());
        int bounded = Math.min(poolCapacity, agentProperties.claimBatchSize());
        return Math.min(bounded, sandboxExecutorFreeCapacity());
    }

    /** Only ever narrows {@link #computeCapacity()}: an unknown executor type imposes no bound. */
    private int sandboxExecutorFreeCapacity() {
        if (sandboxExecutor instanceof ThreadPoolTaskExecutor pool) {
            return Math.max(0, pool.getMaxPoolSize() - pool.getActiveCount());
        }
        return Integer.MAX_VALUE;
    }

    /**
     * ±10% jitter, because replicas sharing one {@code pollInterval} would otherwise stay synchronized
     * and land every poll in the same instant, amplifying claim contention exactly when work exists.
     */
    private void sleepPollInterval() {
        try {
            double jitterMultiplier = 0.9 + (ThreadLocalRandom.current().nextDouble() * 0.2);
            long millis = Math.round(agentProperties.pollInterval().toMillis() * jitterMultiplier);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return {@code true} if the job was actually claimed and dispatched; {@code false} if it was
     *     skipped for any reason, leaving it QUEUED for the next poll to reconsider.
     */
    boolean processJob(UUID jobId) {
        Optional<ClaimResult> claimed;
        try {
            claimed = dispatchClaimResult(jobId, claimJob(jobId));
        } catch (CannotAcquireLockException e) {
            log.debug("Lock timeout during claim for job {}, will retry on next poll", jobId);
            return false;
        } catch (Exception e) {
            log.warn("Claim failed for job {}, will retry on next poll: {}", jobId, e.getMessage());
            return false;
        }
        if (claimed.isEmpty()) {
            return false;
        }
        dispatchExecution(jobId, claimed.get());
        return true;
    }

    private void dispatchExecution(UUID jobId, ClaimResult claim) {
        try {
            inFlight.register();
            sandboxExecutor.execute(() -> {
                try {
                    runClaimedJob(jobId, claim);
                } finally {
                    inFlight.arriveAndDeregister();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.arriveAndDeregister();
            lastClaimPoolRejected = true;
            log.warn(
                "Sandbox executor rejected claimed job {} (pool smaller than configured worker capacity?) — requeuing",
                jobId
            );
            requeueRejectedClaim(jobId);
        }
    }

    /** Bounded retry budget for the pool-rejection requeue write (transient DB blips only). */
    private static final int REQUEUE_REJECTED_CLAIM_ATTEMPTS = 3;
    private static final Duration REQUEUE_REJECTED_CLAIM_RETRY_DELAY = Duration.ofMillis(200);

    /**
     * Undo a claim the sandbox executor couldn't accept: RUNNING → QUEUED, ownership cleared,
     * {@code retry_count} left untouched — the job never started executing, so this must not burn its
     * retry budget.
     *
     * <p>If every attempt fails the row is deliberately left RUNNING under this worker's id (capacity
     * and local tracking are still released). The same sustained DB outage also breaks
     * {@link WorkerLivenessReporter}'s heartbeat, so {@link AgentJobZombieSweeper} reclaims the row
     * through the normal dead-worker path once connectivity returns.
     */
    private void requeueRejectedClaim(UUID jobId) {
        try {
            boolean requeued = false;
            Exception lastFailure = null;
            for (int attempt = 1; attempt <= REQUEUE_REJECTED_CLAIM_ATTEMPTS && !requeued; attempt++) {
                try {
                    transactionTemplate.executeWithoutResult(status ->
                        jobRepository.requeueRejectedClaim(jobId, workerId)
                    );
                    requeued = true;
                } catch (Exception e) {
                    lastFailure = e;
                    if (attempt < REQUEUE_REJECTED_CLAIM_ATTEMPTS) {
                        log.debug(
                            "Requeue of rejected claim {} failed (attempt {}/{}), retrying: {}",
                            jobId,
                            attempt,
                            REQUEUE_REJECTED_CLAIM_ATTEMPTS,
                            e.getMessage()
                        );
                        sleepQuietly(REQUEUE_REJECTED_CLAIM_RETRY_DELAY);
                    }
                }
            }
            if (!requeued) {
                log.error(
                    "Failed to requeue rejected claim {} after {} attempts — row stays RUNNING under this worker " +
                        "until liveness/timeout recovery reclaims it: {}",
                    jobId,
                    REQUEUE_REJECTED_CLAIM_ATTEMPTS,
                    lastFailure != null ? lastFailure.getMessage() : "unknown"
                );
            }
        } finally {
            releaseCapacity();
            localRunningJobs.remove(jobId);
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<ClaimResult> dispatchClaimResult(UUID jobId, Object claimResult) {
        if (
            claimResult == ClaimOutcome.ALREADY_CLAIMED ||
            claimResult == ClaimOutcome.BUDGET_BLOCKED ||
            claimResult == ClaimOutcome.MODEL_UNAVAILABLE
        ) {
            return Optional.empty();
        }
        if (claimResult == ClaimOutcome.CONCURRENCY_FULL || claimResult == ClaimOutcome.BUDGET_HELD) {
            // Job stays QUEUED — concurrency will free up, or the budget hold's available_at will
            // mature and the poll loop re-evaluates the cap on the next eligible poll.
            return Optional.empty();
        }
        if (claimResult instanceof ClaimResult claim) {
            return Optional.of(claim);
        }
        log.warn("Unexpected claim result for job {}, leaving QUEUED for the next poll", jobId);
        return Optional.empty();
    }

    // Everything below runs on the sandbox executor, not the poll thread.

    private void runClaimedJob(UUID jobId, ClaimResult claim) {
        MDC.put(MDC_JOB_ID, jobId.toString());
        AgentJob job = claim.job;
        MDC.put(MDC_JOB_TYPE, job.getJobType().name());
        Instant startTime = Instant.now();
        String metricOutcome = "unknown";
        boolean sandboxExecutionStarted = false;
        try {
            log.info("Executing agent job: jobId={}, jobType={}", jobId, job.getJobType());

            SandboxSpec sandboxSpec = prepareSandboxSpec(jobId, job, claim.snapshot);
            // From this boundary onward provider usage may exist even when execute() throws before
            // returning a result. Persist it so cancellation/recovery on another process can make the
            // same accounting distinction. A lost fence means the job was cancelled or requeued while
            // preparation ran; do not start its sandbox.
            if (!markExecutionStarted(jobId)) {
                metricOutcome = "OWNERSHIP_LOST";
                log.info("Skipped sandbox start after execution fence was lost: jobId={}", jobId);
                return;
            }
            sandboxExecutionStarted = true;
            SandboxResult result = sandboxManager.execute(sandboxSpec);
            AgentResult agentResult = practiceAgent.parseResult(result);

            JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());
            AgentJobStatus terminalStatus = completeJob(jobId, agentResult, result, handler, job);
            metricOutcome = terminalStatus != null ? terminalStatus.name() : "unknown";

            log.info("Agent job completed: jobId={}, duration={}", jobId, Duration.between(startTime, Instant.now()));
        } catch (SandboxCancelledException e) {
            handleCancellation(jobId, job);
            metricOutcome = AgentJobStatus.CANCELLED.name();
        } catch (TerminalPersistenceException e) {
            // Provider work already completed. Leave RUNNING for the zombie sweeper to terminalize
            // and account as UNPRICED; never execute the provider a second time.
            log.error("Terminal job persistence failed after provider completion: jobId={}", jobId, e);
            metricOutcome = "PERSISTENCE_FAILED";
        } catch (Exception e) {
            metricOutcome = handleExecutionFailure(jobId, job, e, sandboxExecutionStarted);
        } finally {
            recordExecutionDuration(job.getJobType(), metricOutcome, Duration.between(startTime, Instant.now()));
            releaseCapacity();
            localRunningJobs.remove(jobId);
            MDC.remove(MDC_JOB_ID);
            MDC.remove(MDC_JOB_TYPE);
        }
    }

    private boolean markExecutionStarted(UUID jobId) {
        Integer updated = transactionTemplate.execute(status ->
            jobRepository.markExecutionStarted(jobId, workerId, Instant.now())
        );
        return updated != null && updated == 1;
    }

    /**
     * Tag cardinality is bounded: {@code jobType} is a small closed enum and {@code outcome} is a
     * terminal {@link AgentJobStatus} name plus {@code "REQUEUED"} and {@code "unknown"}.
     */
    private void recordExecutionDuration(AgentJobType jobType, String outcome, Duration duration) {
        Timer.builder("agent.job.execution.duration")
            .description("Total duration of agent job execution")
            .tag("jobType", jobType != null ? jobType.name() : "unknown")
            .tag("status", outcome)
            .register(meterRegistry)
            .record(duration);
    }

    /** Prepares the spec without starting provider execution — no LLM cost accrues here. */
    private SandboxSpec prepareSandboxSpec(UUID jobId, AgentJob job, ConfigSnapshot snapshot) {
        JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());

        // Wrap in a read-only transaction so prepareInputFiles/buildPrompt can
        // resolve lazy JPA proxies (e.g. PullRequest.author) on this sandbox thread.
        // Re-fetch the job WITH workspace eagerly loaded to avoid LazyInitializationException
        // (the original job object is detached from the claim transaction).
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        readOnlyTx.setReadOnly(true);
        record PrepareResult(Map<String, byte[]> files, Map<String, String> volumeMounts) {}
        PrepareResult prepared = readOnlyTx.execute(status -> {
            AgentJob managedJob = jobRepository.findByIdWithWorkspace(jobId).orElse(job);
            Map<String, byte[]> files = handler.prepareInputFiles(managedJob);
            Map<String, String> volumes = handler.volumeMounts(managedJob);
            return new PrepareResult(files, volumes);
        });

        // ONE credential path: every sandbox, app-server and worker pod alike, talks to the in-app LLM
        // proxy via the job's own token. There is no worker-side BYO-LLM override.
        PracticeAgentRequest adapterRequest = new PracticeAgentRequest(
            snapshot.apiProtocol(),
            snapshot.upstreamModelId(),
            snapshot.contextWindow(),
            snapshot.maxOutputTokens(),
            snapshot.supportsReasoning(),
            job.getJobToken(),
            snapshot.allowInternet(),
            snapshot.timeoutSeconds()
        );

        PracticeSandboxSpec agentSpec = practiceAgent.buildSandboxSpec(adapterRequest);
        SandboxSpec sandboxSpec = buildSandboxSpec(
            jobId,
            prepared.files(),
            prepared.volumeMounts(),
            agentSpec,
            snapshot
        );
        persistProvenanceDigests(jobId, agentSpec.promptDigest(), sandboxSpec.inputFiles());
        return sandboxSpec;
    }

    /**
     * Deliberately NOT best-effort, unlike every other provenance side-effect here: an observation that
     * cannot be tied to the inputs that produced it is unfixable evaluation data, so a failed digest
     * write fails the run before any LLM cost accrues.
     */
    private void persistProvenanceDigests(UUID jobId, @Nullable String promptDigest, Map<String, byte[]> inputFiles) {
        String inputsDigest = ProvenanceDigest.inputsDigestHex(inputFiles, jobId);
        Integer updated = transactionTemplate.execute(status ->
            jobRepository.updateProvenanceDigests(jobId, promptDigest, inputsDigest)
        );
        if (updated == null || updated != 1) {
            throw new IllegalStateException("Provenance digest write matched no job row: jobId=" + jobId);
        }
        log.debug("Provenance digests: jobId={}, prompt={}, inputs={}", jobId, promptDigest, inputsDigest);
    }

    private static SandboxSpec buildSandboxSpec(
        UUID jobId,
        Map<String, byte[]> handlerFiles,
        Map<String, String> handlerVolumeMounts,
        PracticeSandboxSpec agentSpec,
        ConfigSnapshot snapshot
    ) {
        // Merge handler + adapter input files (adapter takes precedence on collision)
        Map<String, byte[]> allInputFiles = new HashMap<>(handlerFiles);
        allInputFiles.putAll(agentSpec.inputFiles());

        // Merge handler + adapter volume mounts with collision detection
        Map<String, String> allVolumeMounts = new HashMap<>(handlerVolumeMounts);
        for (var entry : agentSpec.volumeMounts().entrySet()) {
            String existing = allVolumeMounts.put(entry.getKey(), entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                log.warn(
                    "Volume mount collision: hostPath={}, handler={}, adapter={} (using adapter)",
                    entry.getKey(),
                    existing,
                    entry.getValue()
                );
            }
        }
        // Detect multiple host paths mapped to the same container path
        Set<String> containerPaths = new HashSet<>(allVolumeMounts.values());
        if (containerPaths.size() < allVolumeMounts.size()) {
            log.warn("Multiple host paths mapped to the same container path: {}", allVolumeMounts);
        }

        ResourceLimits limits = new ResourceLimits(
            ResourceLimits.DEFAULT.memoryBytes(),
            ResourceLimits.DEFAULT.cpus(),
            ResourceLimits.DEFAULT.pidsLimit(),
            Duration.ofSeconds(snapshot.timeoutSeconds())
        );

        return new SandboxSpec(
            jobId,
            agentSpec.image(),
            agentSpec.command(),
            agentSpec.environment(),
            agentSpec.networkPolicy(),
            limits,
            agentSpec.securityProfile(),
            allInputFiles,
            agentSpec.outputPath(),
            allVolumeMounts
        );
    }

    private void handleCancellation(UUID jobId, AgentJob job) {
        transactionTemplate.executeWithoutResult(status -> {
            int updated = transitionTerminal(
                jobId,
                AgentJobStatus.CANCELLED,
                Instant.now(),
                "Cancelled during execution"
            );
            if (updated > 0) billTerminatedJob(job, "cancelled during execution");
        });
        log.info("Agent job cancelled: jobId={}", jobId);
    }

    /**
     * Bills a job that ended without a clean terminal write, re-reading the token counts from the row
     * so a stale in-memory {@code job} cannot hide the proxy's committed accumulations. Safe only on
     * terminal (non-requeue) paths — see the overload.
     */
    private void billTerminatedJob(AgentJob job, String reason) {
        billTerminatedJob(job, reason, jobRepository.findLlmUsageById(job.getId()).orElse(null));
    }

    /**
     * {@code counts} MUST be read BEFORE any requeue of this job: {@link
     * AgentJobRepository#requeueOrphan} atomically ZEROes the row's token accumulators so the next
     * attempt bills only its own calls, and a caller that requeues first would then bill zero and
     * silently drop this attempt's spend.
     */
    private void billTerminatedJob(AgentJob job, String reason, @Nullable AgentJobLlmUsage counts) {
        ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        LlmPriceSnapshot price = terminalPriceOrUnpriced(snapshot);
        // No runner report exists on this path by definition — the job never reached a clean finish.
        TerminalUsage usage = TerminalUsage.resolve(null, counts);
        boolean billed = usage.appendTo(
            usageRecorder,
            job.getWorkspace().getId(),
            job,
            snapshot.upstreamModelId(),
            price
        );
        if (billed) {
            log.info(
                "Recorded PRICED usage for terminated job ({}): jobId={}, calls={}",
                reason,
                job.getId(),
                usage.totalCalls()
            );
        } else {
            log.info("Recorded UNPRICED usage ledger entry ({}): jobId={}", reason, job.getId());
        }
    }

    /**
     * Only a provably-infra failure is requeued; everything else fails terminally at once.
     * Under-classifying costs one job's retry budget on a usually self-healing blip, whereas a
     * false-positive lets a permanently broken job (say a misconfigured LLM endpoint) retry
     * {@code max-retries} times, burning time and budget — so this errs conservative.
     *
     * @param sandboxExecutionStarted whether provider execution may have started; only then can an
     *     unverifiable usage event be truthful
     * @return the {@code agent.job.execution.duration} outcome tag
     */
    private String handleExecutionFailure(UUID jobId, AgentJob job, Exception e, boolean sandboxExecutionStarted) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        String errorMessage = truncateErrorMessage(e.getMessage());
        log.error("Agent job failed: jobId={}, error={}", jobId, errorMessage, e);

        if (workerId != null && isRetryableInfraFailure(e)) {
            int currentRetryCount = job.getRetryCount();
            Integer updated = transactionTemplate.execute(status -> {
                // Snapshot the token counts BEFORE requeuing: the requeue zeroes the row's
                // accumulators atomically, so a post-requeue re-read would bill zero.
                AgentJobLlmUsage retryCounts = sandboxExecutionStarted
                    ? jobRepository.findLlmUsageById(jobId).orElse(null)
                    : null;
                int rows = requeueOrphanWithRotation(jobId, workerId, currentRetryCount);
                if (rows > 0 && sandboxExecutionStarted) {
                    billTerminatedJob(
                        job,
                        "infra-failure retry (attempt " + (currentRetryCount + 1) + ")",
                        retryCounts
                    );
                }
                return rows;
            });
            if (updated != null && updated > 0) {
                infraRetryRequeued.increment();
                log.warn(
                    "Requeuing job {} after classified sandbox-infrastructure failure (attempt {}): {}",
                    jobId,
                    currentRetryCount + 1,
                    errorMessage
                );
                return "REQUEUED";
            }
            log.warn(
                "Job {} hit an infra failure but could not be requeued (retry cap exhausted or fence lost) — failing terminally",
                jobId
            );
        }

        transactionTemplate.executeWithoutResult(status -> {
            int updated = transitionTerminal(jobId, AgentJobStatus.FAILED, Instant.now(), errorMessage);
            if (updated > 0 && sandboxExecutionStarted) billTerminatedJob(job, "execution failure");
        });
        return AgentJobStatus.FAILED.name();
    }

    /**
     * Deliberately narrower than {@link SandboxException}, which also covers deterministic
     * validation/config failures and {@code DockerSandboxAdapter}'s catch-all wrap of an unknown defect
     * — retrying either would burn the budget on a failure that was never going to resolve itself.
     * {@link SandboxCancelledException} is excluded without a check for the same reason.
     */
    static boolean isRetryableInfraFailure(Exception e) {
        return e instanceof SandboxInfrastructureException || e instanceof IOException;
    }

    /**
     * @param currentRetryCount sizes the backoff only — the UPDATE's own {@code retry_count <
     *     max-retries} WHERE clause is the authoritative cap, so a stale read here cannot let a job
     *     requeue past it
     */
    private int requeueOrphanWithRotation(UUID jobId, String owningWorkerId, int currentRetryCount) {
        int attemptNumber = currentRetryCount + 1;
        Instant availableAt = Instant.now().plus(AgentJobBackoff.compute(attemptNumber));
        String newToken = AgentJob.generateJobToken();
        String newTokenHash = AgentJob.computeTokenHash(newToken);
        return jobRepository.requeueOrphan(
            jobId,
            owningWorkerId,
            agentProperties.maxRetries(),
            availableAt,
            newToken,
            newTokenHash
        );
    }

    /** Sentinel values for claimJob results that require post-transaction handling. */
    private enum ClaimOutcome {
        ALREADY_CLAIMED,
        CONCURRENCY_FULL,
        BUDGET_BLOCKED,
        BUDGET_HELD,
        MODEL_UNAVAILABLE,
    }

    private record ClaimResult(AgentJob job, ConfigSnapshot snapshot) {}

    /** @return a {@link ClaimResult} on success, otherwise a {@link ClaimOutcome} sentinel. */
    private Object claimJob(UUID jobId) {
        return transactionTemplate.execute(status -> {
            // SKIP LOCKED: if another poller has this row locked, returns empty. available_at is
            // re-checked here too — see the query's javadoc.
            Optional<AgentJob> locked = jobRepository.findByIdQueuedForUpdateSkipLocked(jobId, Instant.now());
            if (locked.isEmpty()) {
                log.debug("Job already claimed or not QUEUED: jobId={}", jobId);
                return ClaimOutcome.ALREADY_CLAIMED;
            }

            AgentJob job = locked.get();

            // Rechecked here even though submit already gated: a workspace can pre-queue jobs faster
            // than the cap updates, and every one queued before the cap was crossed would otherwise
            // still run. Never re-checked past this point — no mid-execution kill on budget alone.
            //
            // HELD, not cancelled: exhaustion is temporary (the cap resets at the UTC month rollover,
            // or an admin raises it), so pushing available_at out and staying QUEUED lets the poll loop
            // resume the job automatically, which is what the paused-work copy promises the user.
            // retry_count is untouched — this is not an execution failure. Only a hold older than
            // BUDGET_HOLD_MAX_AGE is cancelled: month-old feedback is noise and an unbounded hold would
            // loop forever. Scoped to who pays for this purpose, so the host's exhausted budget cannot
            // hold work the workspace funds through its own provider, or vice versa.
            LlmBudgetBlockReason blockReason = llmBudgetService
                .decide(job.getWorkspace().getId())
                .forFunding(claimedFundingSource(job));
            if (blockReason != LlmBudgetBlockReason.NONE) {
                Instant now = Instant.now();
                boolean expired =
                    job.getCreatedAt() != null &&
                    Duration.between(job.getCreatedAt(), now).compareTo(BUDGET_HOLD_MAX_AGE) > 0;
                if (expired) {
                    String message =
                        "Cancelled: still over budget after " + BUDGET_HOLD_MAX_AGE.toDays() + " days on hold.";
                    job.setStatus(AgentJobStatus.CANCELLED);
                    job.setCompletedAt(now);
                    job.setErrorMessage(message);
                    job.setCancellationReason(AgentJobCancellationReason.BUDGET_EXHAUSTED);
                    jobRepository.save(job);
                    log.info(
                        "Cancelling claim — held past {} days on budget: jobId={}, workspaceId={}, blockReason={}",
                        BUDGET_HOLD_MAX_AGE.toDays(),
                        jobId,
                        job.getWorkspace().getId(),
                        blockReason
                    );
                    meterRegistry.counter("agent.job.budget.refused").increment();
                    return ClaimOutcome.BUDGET_BLOCKED;
                }
                job.setAvailableAt(now.plus(BUDGET_HOLD_INTERVAL));
                // Marks this as a budget hold specifically, so raising the cap can release it at once
                // instead of leaving the admin to wait out BUDGET_HOLD_INTERVAL.
                job.setHoldReason(AgentJob.HOLD_REASON_BUDGET);
                jobRepository.save(job);
                log.info(
                    "Holding claim — monthly LLM budget {}: jobId={}, workspaceId={}, retryAt={}",
                    blockReason == LlmBudgetBlockReason.EXHAUSTED ? "exhausted" : "unverifiable (cap set)",
                    jobId,
                    job.getWorkspace().getId(),
                    job.getAvailableAt()
                );
                meterRegistry.counter("agent.job.budget.held").increment();
                return ClaimOutcome.BUDGET_HELD;
            }

            // Lock and live-revalidate the exact catalog binding immediately before RUNNING. Submit-time
            // behaviour stays frozen; only availability/grants and the price are refreshed, and a changed
            // binding is refused rather than silently switching the queued job to another model.
            AgentPurpose purpose = job.getPurpose();
            WorkspaceAgentBinding binding =
                purpose == null
                    ? null
                    : bindingRepository
                          .findByWorkspaceIdAndPurpose(job.getWorkspace().getId(), purpose)
                          .filter(WorkspaceAgentBinding::isEnabled)
                          .orElse(null);
            if (binding == null) {
                return refuseUnavailableModel(job);
            }
            ConfigSnapshot snapshot;
            try {
                ConfigSnapshot submitted = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
                if (llmAdmissionService != null) {
                    var admitted = llmAdmissionService.admit(binding);
                    var ref = admitted.connection();
                    if (
                        submitted.connectionScope() != ref.scope() ||
                        !java.util.Objects.equals(submitted.connectionId(), ref.connectionId()) ||
                        !java.util.Objects.equals(submitted.modelId(), ref.modelId()) ||
                        !java.util.Objects.equals(submitted.workspaceId(), ref.workspaceId()) ||
                        !java.util.Objects.equals(submitted.upstreamModelId(), admitted.resolved().upstreamModelId())
                    ) {
                        return refuseUnavailableModel(job);
                    }
                    snapshot = submitted.withPriceSnapshot(admitted.price());
                } else {
                    snapshot = submitted;
                }
            } catch (IllegalStateException e) {
                return refuseUnavailableModel(job);
            }

            // Concurrency gate: at most maxConcurrentJobs RUNNING for this workspace + purpose. Admission
            // above holds the binding row lock (joined into this transaction), so the count is stable.
            {
                long runningCount = jobRepository.countByWorkspaceIdAndPurposeAndStatusIn(
                    job.getWorkspace().getId(),
                    purpose,
                    Set.of(AgentJobStatus.RUNNING)
                );
                if (runningCount >= binding.getMaxConcurrentJobs()) {
                    concurrencyRejected.increment();
                    log.info(
                        "Concurrency limit reached: jobId={}, workspaceId={}, purpose={}, running={}, max={}",
                        jobId,
                        job.getWorkspace().getId(),
                        purpose,
                        runningCount,
                        binding.getMaxConcurrentJobs()
                    );
                    return ClaimOutcome.CONCURRENCY_FULL;
                }
            }

            Instant claimedAt = Instant.now();
            // availableAt is null only for rows written before this column existed; skip those rather
            // than record garbage latency.
            if (job.getAvailableAt() != null && !job.getAvailableAt().isAfter(claimedAt)) {
                claimLatency.record(Duration.between(job.getAvailableAt(), claimedAt));
            }
            job.setStatus(AgentJobStatus.RUNNING);
            job.setStartedAt(claimedAt);
            job.setExecutionStartedAt(null);
            // The hold is over — clear its marker so it never outlives the hold it describes. A stale
            // 'BUDGET' marker would survive onto a later crash-retry requeue, and a cap raise would
            // then fast-forward a backoff that has nothing to do with the budget.
            job.setHoldReason(null);
            job.setWorkerId(workerId); // owner for cancel routing, orphan recovery, terminal-write fencing
            job.setConfigSnapshot(snapshot.toJson(objectMapper));
            jobRepository.save(job);

            localRunningJobs.add(jobId);
            capacityState.ifPresent(WorkerCapacityState::claimReview);
            return new ClaimResult(job, snapshot);
        });
    }

    private ClaimOutcome refuseUnavailableModel(AgentJob job) {
        String message = "Configured model is unavailable.";
        job.setStatus(AgentJobStatus.CANCELLED);
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(message);
        job.setCancellationReason(AgentJobCancellationReason.MODEL_UNAVAILABLE);
        jobRepository.save(job);
        meterRegistry.counter("agent.job.model.refused").increment();
        log.info("Refusing claim — configured model unavailable: jobId={}", job.getId());
        return ClaimOutcome.MODEL_UNAVAILABLE;
    }

    private void releaseCapacity() {
        capacityState.ifPresent(WorkerCapacityState::releaseReview);
    }

    /**
     * Fenced on {@code worker_id}: if the job was orphan-requeued to a sibling, this worker's late
     * write no-ops instead of clobbering the sibling's run. Unfenced only when no worker identity
     * exists, where there are no siblings to fence against.
     *
     * @return rows updated (0 if no longer RUNNING or no longer owned by this worker)
     */
    private int transitionTerminal(UUID jobId, AgentJobStatus status, Instant now, String error) {
        return workerId != null
            ? jobRepository.transitionStatusOwnedBy(jobId, status, now, error, Set.of(AgentJobStatus.RUNNING), workerId)
            : jobRepository.transitionStatus(jobId, status, now, error, Set.of(AgentJobStatus.RUNNING));
    }

    private AgentJobStatus completeJob(
        UUID jobId,
        AgentResult agentResult,
        SandboxResult sandboxResult,
        JobTypeHandler handler,
        AgentJob job
    ) {
        AgentJobStatus terminalStatus = determineTerminalStatus(sandboxResult, agentResult);
        // persistTerminalState returns false when we lost the fence (cancelled / orphan-requeued); we
        // must not deliver then, or a stuck-then-recovered worker would double-post the sibling's findings.
        boolean persisted = persistTerminalState(jobId, agentResult, sandboxResult, terminalStatus);
        if (persisted) {
            deliverResults(jobId, terminalStatus, handler);
        } else {
            log.info("Skipping delivery: job no longer owned/RUNNING (requeued or cancelled): jobId={}", jobId);
        }
        return terminalStatus;
    }

    /**
     * A non-zero exit that still produced valid output counts as COMPLETED so the findings are
     * delivered — the runner's validation is stricter than the Java-side parser, so an agent can write
     * result.json and still exit 1 for an unrelated reason.
     */
    private AgentJobStatus determineTerminalStatus(SandboxResult sandboxResult, AgentResult agentResult) {
        if (sandboxResult.timedOut()) {
            return AgentJobStatus.TIMED_OUT;
        }
        if (sandboxResult.exitCode() == 0) {
            return AgentJobStatus.COMPLETED;
        }
        // Distinguish envelope drift (exit 42) from generic failure — the runner emits this when
        // the task.json schemaVersion / kind doesn't match this image. Operators need to see
        // this distinctly from agent crashes; the secondary metric also alerts on image drift.
        if (sandboxResult.exitCode() == SandboxLayout.EXIT_ENVELOPE_MISMATCH) {
            log.error(
                "Pi runner rejected task envelope (exit {}) — server/image schemaVersion or kind drift. " +
                    "Rebuild the agent-pi image or roll back the server.",
                SandboxLayout.EXIT_ENVELOPE_MISMATCH
            );
            meterRegistry.counter("agent.pi.envelope.mismatch").increment();
            return AgentJobStatus.FAILED;
        }
        if (agentResult != null && agentResult.output() != null) {
            Object rawOutput = agentResult.output().get("rawOutput");
            if (rawOutput instanceof String raw && !raw.isBlank()) {
                log.info(
                    "Agent exited with code {} but produced output — treating as COMPLETED for delivery",
                    sandboxResult.exitCode()
                );
                return AgentJobStatus.COMPLETED;
            }
            if (rawOutput != null) {
                log.warn(
                    "Agent exited with code {} and rawOutput is present but not a String (type={})",
                    sandboxResult.exitCode(),
                    rawOutput.getClass().getSimpleName()
                );
            }
        }
        return AgentJobStatus.FAILED;
    }

    /** Attempts at the terminal accounting write, and the base delay doubled between them. */
    private static final int TERMINAL_PERSIST_ATTEMPTS = 3;
    private static final Duration TERMINAL_PERSIST_RETRY_DELAY = Duration.ofMillis(200);

    /**
     * The pause between attempts grows, since a lock timeout or a failover needs more than the
     * microseconds an immediate retry gives it. A deterministic failure gives up on the first attempt.
     *
     * <p>Giving up deliberately leaves the row RUNNING: the provider work is already done and paid for,
     * so re-running would charge for it twice. {@link AgentJobZombieSweeper} terminalises the row
     * instead and books the proxy-recorded spend.
     *
     * @return {@code true} if this worker won the terminal write; {@code false} if the job was
     *     cancelled or orphan-requeued to a sibling, in which case the caller must NOT deliver.
     */
    private boolean persistTerminalState(
        UUID jobId,
        AgentResult agentResult,
        SandboxResult sandboxResult,
        AgentJobStatus terminalStatus
    ) {
        String errorMessage = switch (terminalStatus) {
            case TIMED_OUT -> "Container timed out";
            case FAILED -> "Container exited with code " + sandboxResult.exitCode();
            default -> null;
        };

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= TERMINAL_PERSIST_ATTEMPTS; attempt++) {
            try {
                return persistTerminalStateOnce(jobId, agentResult, sandboxResult, terminalStatus, errorMessage);
            } catch (RuntimeException e) {
                if (!isRetryableTerminalWriteFailure(e)) {
                    log.error("Terminal persistence failed unrecoverably for jobId={}: {}", jobId, e.getMessage());
                    throw new TerminalPersistenceException(e);
                }
                lastFailure = e;
                log.warn(
                    "Terminal persistence attempt {}/{} failed for jobId={}: {}",
                    attempt,
                    TERMINAL_PERSIST_ATTEMPTS,
                    jobId,
                    e.getMessage()
                );
                if (attempt < TERMINAL_PERSIST_ATTEMPTS) {
                    sleepQuietly(TERMINAL_PERSIST_RETRY_DELAY.multipliedBy(1L << (attempt - 1)));
                }
            }
        }
        throw new TerminalPersistenceException(lastFailure);
    }

    /**
     * NOT the same question as {@link #isRetryableInfraFailure}, which decides whether the whole JOB is
     * requeued for a fresh run. This one only decides whether to re-attempt the accounting write for
     * provider work that has ALREADY happened — conflating the two would charge twice for a job that
     * already spent money.
     *
     * <p>Walks the cause chain (bounded, cycle-safe): {@code TransactionTemplate} and JPA both surface
     * the underlying failure wrapped.
     */
    static boolean isRetryableTerminalWriteFailure(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (
                cause instanceof TransientDataAccessException ||
                cause instanceof RecoverableDataAccessException ||
                cause instanceof DataAccessResourceFailureException ||
                cause instanceof CannotCreateTransactionException
            ) {
                return true;
            }
            Throwable next = cause.getCause();
            cause = next == cause ? null : next;
        }
        return false;
    }

    private boolean persistTerminalStateOnce(
        UUID jobId,
        AgentResult agentResult,
        SandboxResult sandboxResult,
        AgentJobStatus terminalStatus,
        @Nullable String errorMessage
    ) {
        Boolean persisted = transactionTemplate.execute(status -> {
            int updated = transitionTerminal(jobId, terminalStatus, Instant.now(), errorMessage);
            if (updated == 0) {
                log.info("Job no longer owned/RUNNING, skipping output persist: jobId={}", jobId);
                return false;
            }

            AgentJob freshJob = jobRepository.findById(jobId).orElseThrow();
            // Read the proxy's committed per-call accumulations BEFORE the runner-reported totals are
            // written over them below — they are the fallback when the runner reported nothing.
            AgentJobLlmUsage proxyCounts = jobRepository.findLlmUsageById(jobId).orElse(null);
            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(freshJob.getConfigSnapshot(), objectMapper);
            LlmPriceSnapshot price = admittedPrice(snapshot);

            freshJob.setOutput(objectMapper.valueToTree(agentResult.output()));
            freshJob.setExitCode(sandboxResult.exitCode());
            if (sandboxResult.logs() != null && !sandboxResult.logs().isBlank()) {
                String logs = sandboxResult.logs();
                freshJob.setContainerLogs(
                    logs.length() > MAX_CONTAINER_LOGS_CHARS
                        ? logs.substring(logs.length() - MAX_CONTAINER_LOGS_CHARS)
                        : logs
                );
            }
            if (terminalStatus == AgentJobStatus.COMPLETED) {
                freshJob.setDeliveryStatus(DeliveryStatus.PENDING);
            }

            var runnerUsage = agentResult.usage();
            // The runner's own report is preferred (it also covers streamed calls the proxy skips),
            // but an absent or empty one falls back to what the proxy actually watched go upstream —
            // otherwise a runner that never wrote usage.json would book real spend as zero.
            TerminalUsage usage = TerminalUsage.resolve(runnerUsage, proxyCounts);

            if (runnerUsage != null && runnerUsage.totalCalls() > 0) {
                freshJob.setLlmTotalCalls(runnerUsage.totalCalls());
                freshJob.setLlmTotalInputTokens(runnerUsage.inputTokens());
                freshJob.setLlmTotalOutputTokens(runnerUsage.outputTokens());
                freshJob.setLlmTotalReasoningTokens(runnerUsage.reasoningTokens());
                freshJob.setLlmCacheReadTokens(runnerUsage.cacheReadTokens());
                freshJob.setLlmCacheWriteTokens(runnerUsage.cacheWriteTokens());
            }
            // Provider output is telemetry only. The admitted snapshot is authoritative identity.
            freshJob.setLlmModel(snapshot.upstreamModelId());
            freshJob.setLlmModelVersion(snapshot.modelVersion());
            jobRepository.saveAndFlush(freshJob);

            usage.appendTo(usageRecorder, freshJob.getWorkspace().getId(), freshJob, snapshot.upstreamModelId(), price);
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private static final class TerminalPersistenceException extends RuntimeException {

        private TerminalPersistenceException(Throwable cause) {
            super("Could not durably persist terminal job result and usage", cause);
        }
    }

    /**
     * A job whose purpose or binding is gone yields {@code null}, which
     * {@link LlmBudgetDecision#forFunding} judges against BOTH caps: an unattributable job must not be
     * a way around either one.
     */
    private @Nullable FundingSource claimedFundingSource(AgentJob job) {
        AgentPurpose purpose = job.getPurpose();
        if (purpose == null) {
            return null;
        }
        return bindingRepository
            .findByWorkspaceIdAndPurpose(job.getWorkspace().getId(), purpose)
            .map(WorkspaceAgentBinding::getFundingSource)
            .orElse(null);
    }

    private LlmPriceSnapshot admittedPrice(ConfigSnapshot snapshot) {
        if (snapshot.priceSnapshot() != null) return snapshot.priceSnapshot();
        if (llmAdmissionService != null) {
            throw new IllegalStateException("Started job has no admitted LLM price snapshot");
        }
        return LlmPriceSnapshot.unpricedInstance();
    }

    /**
     * Unlike {@link #admittedPrice}, this must NEVER throw: an exception here would roll back the very
     * transition that marks the job terminal, resurrecting a job that has already run (and can loop).
     * A missing price is billed UNPRICED, matching {@link AgentJobZombieSweeper}'s reaper.
     */
    private LlmPriceSnapshot terminalPriceOrUnpriced(ConfigSnapshot snapshot) {
        LlmPriceSnapshot price = snapshot.priceSnapshot();
        return price != null ? price : LlmPriceSnapshot.unpricedInstance();
    }

    /** Runs outside any transaction — it calls external APIs. */
    private void deliverResults(UUID jobId, AgentJobStatus terminalStatus, JobTypeHandler handler) {
        if (terminalStatus != AgentJobStatus.COMPLETED) {
            return;
        }

        // Reload to get the freshly persisted output
        AgentJob deliverJob = jobRepository.findById(jobId).orElse(null);
        if (deliverJob != null) {
            try {
                handler.deliver(deliverJob);
                persistDeliveryStatus(jobId, DeliveryStatus.DELIVERED, deliverJob.getDeliveryCommentId());
            } catch (Exception e) {
                log.warn("Delivery failed for job {} (output saved, job still COMPLETED): {}", jobId, e.getMessage());
                // Preserve the comment id from a partial delivery: the comment may already be posted.
                persistDeliveryStatus(jobId, DeliveryStatus.FAILED, deliverJob.getDeliveryCommentId());
                meterRegistry.counter("agent.job.delivery.failure").increment();
            }
        }
    }

    private void persistDeliveryStatus(UUID jobId, DeliveryStatus status, @Nullable String commentId) {
        try {
            transactionTemplate.executeWithoutResult(tx ->
                jobRepository.updateDeliveryStatus(jobId, status, commentId)
            );
        } catch (Exception e) {
            log.error("Failed to persist delivery status: jobId={}, status={}", jobId, status, e);
        }
    }

    private static String truncateErrorMessage(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
            ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "... [truncated]"
            : message;
    }
}
