package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
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
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Polls the {@code agent_job} table for {@code QUEUED} work and executes it through the full
 * pipeline (#1368 NATS→Postgres cutover: the queue IS the table — a QUEUED insert is the enqueue,
 * delivery is poll-based rather than pushed).
 *
 * <p>Each poll iteration computes this worker's free local capacity, fetches at most that many
 * candidate QUEUED job ids (oldest first), and attempts to claim each one with the same {@code FOR
 * UPDATE SKIP LOCKED} micro-transaction the NATS-era executor used. Claim is synchronous on the poll
 * thread (~5ms); a successful claim's actual execution is handed off to the {@code sandboxExecutor}
 * (bounded platform thread pool) so the poll thread is never blocked by a running sandbox.
 *
 * <p>A per-config concurrency-full or already-claimed candidate simply stays/returns to {@code
 * QUEUED} — there is no NAK-with-delay to schedule; the next poll iteration reconsiders it
 * naturally. To avoid a maxed-out config spinning this loop into a tight DB-hammering retry, the
 * loop sleeps {@code pollInterval} whenever an iteration claims nothing at all (empty candidate list,
 * or every candidate was skipped).
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
    // Budget hold (#1368): how long a claim-blocked job waits before the poll loop re-evaluates the
    // cap, and the maximum total time it may stay held before it is cancelled as stale.
    private static final Duration BUDGET_HOLD_INTERVAL = Duration.ofHours(1);
    private static final Duration BUDGET_HOLD_MAX_AGE = Duration.ofDays(7);

    private final AgentProperties agentProperties;
    private final AgentJobRepository jobRepository;
    private final AgentConfigRepository configRepository;
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
    /**
     * Tracks in-flight execution submissions for the drain coordinator's
     * {@link #awaitInFlight(Duration)} call. Phaser is the right primitive here: register on
     * dispatch, arriveAndDeregister on completion, await-advance to wait for all parties to
     * deregister. Survives thread restarts and exception paths via the try-finally.
     */
    private final Phaser inFlight = new Phaser(1); // 1 = the executor itself; deregistered on stop
    /**
     * Job ids this worker is currently executing. Source of truth for job-scoped cancellation
     * (#1138): drain and hub-initiated cancels act only on jobs in this set, never on sibling
     * workers' jobs. Also doubles as the free-capacity signal for the poll loop (#1368). Populated on
     * claim, removed once the claimed job reaches a terminal outcome.
     */
    private final Set<UUID> localRunningJobs = ConcurrentHashMap.newKeySet();
    private final Optional<WorkerCapacityState> capacityState;
    private final Optional<WorkerProperties> workerProperties;
    /** This worker's identity (null only when the worker role is off); stamped on claimed jobs to fence terminal writes. */
    private final String workerId;
    /**
     * Set by {@link #dispatchExecution} when the sandbox executor pool rejected the just-claimed job;
     * read by the poll loop immediately after each {@link #processJob} call to decide whether to back
     * off (#1368 fix wave). Poll-thread-owned: written and read only from the single poll thread (or,
     * in tests, the single calling thread), so no synchronization is needed.
     */
    private boolean lastClaimPoolRejected;

    @Autowired
    public AgentJobExecutor(
        AgentProperties agentProperties,
        AgentJobRepository jobRepository,
        AgentConfigRepository configRepository,
        JobTypeHandlerRegistry handlerRegistry,
        PracticePiAdapter practiceAgent,
        SandboxManager sandboxManager,
        @Qualifier("sandboxExecutor") AsyncTaskExecutor sandboxExecutor,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        LlmUsageRecorder usageRecorder,
        LlmBudgetService llmBudgetService,
        LlmAdmissionService llmAdmissionService,
        Optional<WorkerCapacityState> capacityState,
        Optional<WorkerProperties> workerProperties
    ) {
        this.agentProperties = agentProperties;
        this.jobRepository = jobRepository;
        this.configRepository = configRepository;
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

    /** Compatibility constructor for focused tests that do not exercise live admission. */
    public AgentJobExecutor(
        AgentProperties agentProperties,
        AgentJobRepository jobRepository,
        AgentConfigRepository configRepository,
        JobTypeHandlerRegistry handlerRegistry,
        PracticePiAdapter practiceAgent,
        SandboxManager sandboxManager,
        AsyncTaskExecutor sandboxExecutor,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        LlmUsageRecorder usageRecorder,
        LlmBudgetService llmBudgetService,
        Optional<WorkerCapacityState> capacityState,
        Optional<WorkerProperties> workerProperties
    ) {
        this(
            agentProperties,
            jobRepository,
            configRepository,
            handlerRegistry,
            practiceAgent,
            sandboxManager,
            sandboxExecutor,
            transactionTemplate,
            objectMapper,
            meterRegistry,
            usageRecorder,
            llmBudgetService,
            null,
            capacityState,
            workerProperties
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // Must run after WorkerLivenessReporter.start() (@Order(1)) so the registry row exists pre-first-claim
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
     * Stop the poll loop so no new jobs are claimed. Idempotent. Composable with
     * {@link #awaitInFlight(Duration)} and {@link #cancelInFlight(AgentJobCancellationReason)}
     * from the worker drain coordinator; called standalone from {@link #stop()} for non-worker
     * monolith mode.
     *
     * <p>Joins the poll thread before returning (#1368 fix wave — drain admission race): without this,
     * a claim already in flight when {@code running} flips false can still finish claiming a job (RUNNING,
     * owned) and register with {@link #inFlight} <em>after</em> the drain coordinator has already called
     * {@link #awaitInFlight(Duration)}. Phaser semantics make that registration land in the NEXT phase, so
     * the coordinator's {@code awaitAdvanceInterruptibly} on the OLD (already-complete) phase returns
     * immediately, believing drain is clean — the late-claimed job is then neither awaited nor cancelled,
     * silently escaping the drain contract entirely. Joining here (the claim transaction is ~5ms, so this
     * is normally instantaneous) guarantees no further claim/register can happen once this method returns,
     * closing the window structurally rather than trying to out-race it.
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
     * Stop the containers of the jobs THIS worker is currently running, and hand each one back to
     * the queue for a sibling to pick up — matching the documented drain contract (docs/admin/
     * runtime-roles.mdx: "cancels remaining jobs cleanly — they return to the PostgreSQL-backed
     * queue... bounded by AGENT_MAX_RETRIES"). Scoped to {@link #localRunningJobs} so sibling
     * workers' jobs are untouched — this is the fix for #1138 that makes running more than one
     * worker replica safe.
     *
     * <p>#1368 fix wave: previously this always wrote a terminal {@link AgentJobStatus#CANCELLED},
     * contradicting the documented "returns to the queue" behaviour and discarding the job instead
     * of retrying it. Now it first attempts a worker-fenced requeue (RUNNING → QUEUED, retry_count
     * bumped, capped by {@code max-retries} — the same CAS {@link AgentJobZombieSweeper} uses for
     * orphan recovery, see {@link AgentJobRepository#requeueOrphan}). Only when that CAS loses — the
     * job already left this worker's ownership (a concurrent user-cancel / fence race), or the retry
     * cap is already exhausted — does it fall back to a worker-fenced terminal cancel, so a genuinely
     * exhausted job still ends up FAILED/CANCELLED rather than requeued forever.
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
                    int updated =
                        workerId != null ? requeueOrphanWithRotation(jobId, workerId, job.getRetryCount()) : 0;
                    if (updated > 0) {
                        if (job.getExecutionStartedAt() != null) billTerminatedJob(job, "worker draining");
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
     * Promptly stop a locally-running job's container in response to a hub-initiated cancel
     * (the authoritative {@code agent_job} status transition is performed hub-side before the
     * {@code CancelJob} frame is dispatched). No-op if this worker does not own the job, which is
     * how job-scoped cancellation stays safe across replicas.
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

    /**
     * Single-method stop used when no {@code WorkerDrainCoordinator} owns the worker
     * lifecycle (e.g., non-worker monolith mode). Slim, drain-equivalent default — fixes the
     * latent bug where the previous {@code @PreDestroy} never awaited in-flight sandbox tasks.
     */
    @PreDestroy
    public void stop() {
        stopAcceptingNewJobs();
        awaitInFlight(Duration.ofSeconds(30));
    }

    // Poll loop

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
                    // A pool rejection means the sandbox executor is already saturated — trying more
                    // candidates this iteration would just requeue-churn them too. Stop the batch and
                    // fall through to the backoff sleep below instead (#1368 fix wave).
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
                // Busy-spin / retry-burn protection: if every candidate in this batch was skipped
                // (already claimed by a sibling, concurrency-full, budget-blocked, or a lock timeout),
                // OR the sandbox executor pool rejected a claim, sleep before the next poll instead of
                // immediately re-querying/re-claiming (#1368 fix wave: a saturated pool without this
                // backoff claims-then-immediately-requeues in a tight loop, hammering the DB).
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
     * This worker's free local capacity for new claims: the configured review capacity minus jobs
     * already running locally, bounded by {@code claimBatchSize} so one poll iteration never
     * over-fetches candidates it couldn't dispatch anyway, and further bounded by the sandbox
     * executor's actual free thread-pool slots (#1368 fix wave). {@code WorkerCapacityState.reviewMax}
     * and the sandbox executor's pool size are two independently-configured knobs
     * ({@code HEPHAESTUS_WORKER_CAPACITY_REVIEW_MAX} auto-sizes from CPU count;
     * {@code SANDBOX_MAX_CONCURRENT} defaults to a flat 5) — nothing enforces they agree, so without
     * this bound a reviewMax larger than the pool routinely claims jobs the pool then rejects,
     * burning claim/requeue churn on every such poll. Package-private for testability.
     */
    int computeCapacity() {
        int poolCapacity = capacityState
            .map(cs -> Math.max(0, cs.reviewMax() - localRunningJobs.size()))
            .orElse(agentProperties.claimBatchSize());
        int bounded = Math.min(poolCapacity, agentProperties.claimBatchSize());
        return Math.min(bounded, sandboxExecutorFreeCapacity());
    }

    /**
     * Free slots in the sandbox executor's thread pool, or {@link Integer#MAX_VALUE} (no additional
     * bound) when the injected {@link AsyncTaskExecutor} isn't a {@link ThreadPoolTaskExecutor} (e.g.
     * a test double) — this method only ever narrows {@link #computeCapacity()}, never widens it.
     */
    private int sandboxExecutorFreeCapacity() {
        if (sandboxExecutor instanceof ThreadPoolTaskExecutor pool) {
            return Math.max(0, pool.getMaxPoolSize() - pool.getActiveCount());
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Sleeps {@code pollInterval * (0.9 .. 1.1)} rather than a fixed interval (#1368 hardening): with
     * several replicas all configured with the same {@code pollInterval}, a fixed sleep tends to
     * synchronize their poll timing (they started within milliseconds of each other and every sleep is
     * identical), so every poll tends to land in the same instant — amplifying claim contention right
     * when the queue actually has work. ±10% jitter decorrelates replicas over a few cycles.
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

    // Job claim + dispatch

    /**
     * Attempt to claim {@code jobId} and, on success, dispatch its execution to the sandbox
     * executor. Package-private for testability.
     *
     * @return {@code true} if the job was actually claimed (RUNNING was won and execution
     *     dispatched); {@code false} if it was skipped for any reason (already claimed, concurrency
     *     full, budget blocked, or a transient claim failure) — the job stays/returns to QUEUED for
     *     the next poll to reconsider.
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

    /** Dispatch a claimed job's execution onto the sandbox executor; requeues on pool rejection. */
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
     * {@code retry_count} left untouched (see {@link AgentJobRepository#requeueRejectedClaim} —
     * the job never started executing, so this must not burn its retry budget).
     *
     * <p>#1368 fix wave: retries the write a few times before giving up — this is normally a purely
     * local, contention-free UPDATE (self-fenced, this worker's own just-won claim), so a failure is
     * almost always a transient DB blip. If every attempt fails, the row is left RUNNING under this
     * worker's id with capacity/local-tracking already released here; it does not hang forever even
     * then: {@link WorkerLivenessReporter} heartbeats independently of any single job; a sustained DB
     * outage that breaks every requeue attempt also breaks that heartbeat, so once connectivity
     * returns, {@link AgentJobZombieSweeper#recoverOrphanedJobs} reclaims the row through the normal
     * dead-worker path, and {@link AgentJobZombieSweeper#reapStaleRunningJobs} is the absolute-timeout
     * backstop regardless.
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

    /**
     * Dispatch the claim result: interpret non-success outcomes as "nothing more to do here — the
     * job is already terminal (budget-blocked), already claimed by a concurrent poller, or the
     * config is at capacity and will stay QUEUED until the next poll" — or return the
     * {@link ClaimResult} for the caller to proceed with execution.
     */
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

    // Job execution (runs on the sandbox executor)

    /** Execute an already-claimed job end to end, then release capacity regardless of outcome. */
    private void runClaimedJob(UUID jobId, ClaimResult claim) {
        MDC.put(MDC_JOB_ID, jobId.toString());
        AgentJob job = claim.job;
        MDC.put(MDC_JOB_TYPE, job.getJobType().name());
        Instant startTime = Instant.now();
        // Outcome for the tagged agent.job.execution.duration metric (#1368 hardening) — set in every
        // branch below, including the requeued-not-failed infra-retry outcome, so the metric's status
        // tag distinguishes "requeued for retry" from a terminal FAILED.
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
     * Records {@code agent.job.execution.duration} tagged by job type and outcome (#1368 hardening —
     * previously untagged and only recorded on the success path, silently omitting every failure/
     * cancellation/timeout duration from the metric). Tag cardinality is bounded: {@code jobType} is a
     * small closed enum, {@code status} is a terminal {@link AgentJobStatus} name plus the synthetic
     * {@code "requeued"} (infra-retry, not yet terminal) and {@code "unknown"} (defensive fallback).
     */
    private void recordExecutionDuration(AgentJobType jobType, String outcome, Duration duration) {
        Timer.builder("agent.job.execution.duration")
            .description("Total duration of agent job execution")
            .tag("jobType", jobType != null ? jobType.name() : "unknown")
            .tag("status", outcome)
            .register(meterRegistry)
            .record(duration);
    }

    /** Prepare the complete sandbox specification without starting provider execution. */
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

        // ONE credential path (#1368 slice 5): every sandbox — app-server AND worker pod alike, both
        // share the DB — talks to the in-app LLM proxy via the job's own token. There is no
        // worker-side BYO-LLM override anymore; a worker host reaches the proxy the same way the
        // app-server's own sandboxes do (both run the proxy controller whenever job execution is
        // enabled — see LlmProxySecurityConfig).
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
     * Stamp the job with its run-provenance digests before the sandbox starts: the adapter's prompt-scaffolding
     * digest, and the digest of the complete merged input set the sandbox will receive. Deliberately NOT
     * best-effort, unlike every other provenance side-effect here — an observation that cannot be tied to the
     * inputs that produced it is unfixable evaluation data, so this fails the run before any LLM cost accrues.
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

    /** Build the final {@link SandboxSpec} by merging handler and adapter inputs. */
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

    /** Handle a job cancelled during sandbox execution. */
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
     * Record the ledger entry for a job that ended without a clean terminal write (worker drain,
     * cancellation, infra-retry give-up, or execution failure), inside the transition transaction.
     *
     * <p>#1368: the proxy attributes each non-streaming call's tokens to the job as it runs, so a
     * job that died mid-run still knows what it spent. If it made real, priced calls, bill them
     * PRICED here; otherwise fall back to a zero-token UNPRICED row so the month is still flagged
     * unverifiable. The token totals are read straight from the row (a projection), so a stale
     * in-memory {@code job} does not hide the proxy's committed accumulations.
     */
    private void billTerminatedJob(AgentJob job, String reason) {
        ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        LlmPriceSnapshot price = admittedPrice(snapshot);
        AgentJobLlmUsage counts = jobRepository.findLlmUsageById(job.getId()).orElse(null);
        boolean billable = counts != null && counts.hasBillableUsage() && price.pricingState() != PricingState.UNPRICED;
        LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.from(job.getJobType()),
            LlmUsageSourceType.AGENT_JOB,
            job.getId(),
            job.getRetryCount(),
            snapshot.upstreamModelId(),
            billable ? counts.inputTokens() : 0L,
            billable ? counts.outputTokens() : 0L,
            billable ? counts.cacheReadTokens() : 0L,
            billable ? counts.cacheWriteTokens() : 0L,
            billable ? counts.reasoningTokens() : 0L,
            billable ? counts.totalCalls() : 0,
            price,
            Instant.now()
        );
        if (billable) {
            usageRecorder.record(job.getWorkspace().getId(), sample);
            log.info(
                "Recorded PRICED usage for terminated job ({}): jobId={}, calls={}",
                reason,
                job.getId(),
                counts.totalCalls()
            );
        } else {
            usageRecorder.recordUnverifiable(job.getWorkspace().getId(), sample);
            log.info("Recorded UNPRICED usage ledger entry ({}): jobId={}", reason, job.getId());
        }
    }

    private @Nullable ConfigSnapshot parseSnapshotQuietly(AgentJob job) {
        try {
            return ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        } catch (Exception e) {
            log.warn("Could not deserialise config snapshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Handle execution failures (#1368 hardening — error classification).
     *
     * <p>Everything used to become {@link AgentJobStatus#FAILED} unconditionally, so a transient
     * sandbox-infrastructure blip (Docker daemon unreachable, image pull failed, network partition to
     * the registry) burned the job's whole retry budget on a failure that had nothing to do with the
     * job itself. Now: only {@link SandboxInfrastructureException} or a bare {@link IOException} —
     * i.e. errors PROVABLY caused by sandbox/container/network infrastructure rather than the agent's
     * own run — are treated as retryable: the job is requeued via the same worker-fenced CAS orphan
     * recovery uses ({@link AgentJobRepository#requeueOrphan}, with a backoff-computed {@code
     * available_at} and a rotated job token), bounded by {@code retry_count < max-retries} same as
     * every other requeue path. Once that cap is hit, or the CAS loses the fence (a concurrent
     * cancel/requeue already moved the job), it falls through to the terminal FAILED write below —
     * exactly the pre-#1368-hardening behaviour.
     *
     * <p>Deliberately conservative: everything else — a non-zero agent exit already handled by {@link
     * #determineTerminalStatus}, a parse/envelope-mismatch failure, an unrecognised {@code
     * RuntimeException} from deep in the pipeline — stays FAILED immediately, exactly as before. A
     * false-positive "infra" classification would let a genuinely broken job (e.g. a permanently
     * misconfigured LLM endpoint) silently retry {@code max-retries} times before finally failing,
     * burning real time and (if it gets far enough to spend) budget; under-classifying only costs one
     * job's retry budget on what's usually a self-healing blip, and is the safe direction to err in.
     *
     * @param sandboxExecutionStarted whether sandbox/provider execution may have started; only then
     *     can an unverifiable usage event be truthful
     * @return the outcome to record on {@code agent.job.execution.duration}'s {@code status} tag:
     *     {@code "REQUEUED"} when this classified-as-infra failure was successfully requeued for
     *     retry, or the terminal status name ({@code "FAILED"}) otherwise
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
                int rows = requeueOrphanWithRotation(jobId, workerId, currentRetryCount);
                if (rows > 0 && sandboxExecutionStarted) {
                    billTerminatedJob(job, "infra-failure retry (attempt " + (currentRetryCount + 1) + ")");
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
     * Provably-infra classification (#1368 hardening; narrowed #1368 fix wave, finding #7) — see {@link
     * #handleExecutionFailure} javadoc for the reasoning. Deliberately checks {@link
     * SandboxInfrastructureException} rather than the broader {@link SandboxException}: the broader type
     * also covers validation/config failures (path traversal, size limits, misconfigured network policy)
     * that are deterministic across retries, and {@code DockerSandboxAdapter}'s catch-all wrap of an
     * unexpected exception, which is an unknown defect — retrying either would burn the retry budget on
     * a failure that was never going to resolve itself. {@link SandboxCancelledException} extends {@link
     * SandboxException} but never {@link SandboxInfrastructureException}, so it is excluded without an
     * explicit check. Package-private for testability.
     */
    static boolean isRetryableInfraFailure(Exception e) {
        return e instanceof SandboxInfrastructureException || e instanceof IOException;
    }

    /**
     * Mint a fresh backoff + rotated token and requeue via {@link AgentJobRepository#requeueOrphan}.
     * Shared by {@link #cancelInFlight} (drain requeue) and {@link #handleExecutionFailure} (infra-retry
     * requeue) — both requeue a job THIS worker currently owns, fenced on {@code workerId}.
     *
     * @param currentRetryCount the job's {@code retry_count} as last read by the caller (used only to
     *     size the backoff — the UPDATE's own {@code retry_count < max-retries} WHERE clause is the
     *     authoritative cap, so a stale read here cannot let a job requeue past the cap)
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

    // Claim: micro-transaction #1

    /** Sentinel values for claimJob results that require post-transaction handling. */
    private enum ClaimOutcome {
        ALREADY_CLAIMED,
        CONCURRENCY_FULL,
        BUDGET_BLOCKED,
        BUDGET_HELD,
        MODEL_UNAVAILABLE,
    }

    private record ClaimResult(AgentJob job, ConfigSnapshot snapshot) {}

    /**
     * Attempt to claim a job within a micro-transaction. Returns:
     * - {@link ClaimResult} on success
     * - {@link ClaimOutcome#ALREADY_CLAIMED} if job is not QUEUED (caller does nothing further)
     * - {@link ClaimOutcome#CONCURRENCY_FULL} if concurrency limit reached (job stays QUEUED)
     * - {@link ClaimOutcome#BUDGET_BLOCKED} if the workspace's LLM budget gate refused the job (already terminal)
     * - {@code null} if transaction returned null unexpectedly
     */
    private Object claimJob(UUID jobId) {
        return transactionTemplate.execute(status -> {
            // SKIP LOCKED: if another poller has this row locked, returns empty. available_at is
            // re-checked here too (#1368 fix wave, finding #3) — see the query's javadoc.
            Optional<AgentJob> locked = jobRepository.findByIdQueuedForUpdateSkipLocked(jobId, Instant.now());
            if (locked.isEmpty()) {
                log.debug("Job already claimed or not QUEUED: jobId={}", jobId);
                return ClaimOutcome.ALREADY_CLAIMED;
            }

            AgentJob job = locked.get();

            // Claim-time budget recheck (#1368 fix wave): AgentJobService.submit already gated
            // submission, but a workspace can pre-queue jobs faster than the cap updates — every
            // job queued before the cap was crossed would otherwise still run. Recheck here, right
            // before the job would start. Never re-checked once a job is past this point (no
            // mid-execution kill on budget alone).
            //
            // The job is HELD, not cancelled: budget exhaustion is temporary (the cap resets at the
            // UTC month rollover or when an instance admin raises it), so pushing available_at into
            // the future and leaving the job QUEUED lets the poll loop pick it back up automatically
            // once the workspace is back within budget — which is exactly what the paused-work copy
            // promises the user. retry_count is left untouched (this is not an execution failure).
            // Only a job that has been waiting past BUDGET_HOLD_MAX_AGE is cancelled terminally:
            // month-old detection feedback is noise, and an unbounded hold would loop forever.
            LlmBudgetBlockReason blockReason = llmBudgetService.blockReason(job.getWorkspace().getId());
            if (blockReason != LlmBudgetBlockReason.NONE) {
                Instant now = Instant.now();
                boolean expired =
                    job.getCreatedAt() != null &&
                    Duration.between(job.getCreatedAt(), now).compareTo(BUDGET_HOLD_MAX_AGE) > 0;
                if (expired) {
                    String message =
                        "Budget reached; queued work expired after " + BUDGET_HOLD_MAX_AGE.toDays() + " days.";
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
            AgentConfig config =
                job.getConfig() != null
                    ? configRepository.findByIdForUpdate(job.getConfig().getId()).orElse(null)
                    : null;
            if (config == null) {
                return refuseUnavailableModel(job);
            }
            ConfigSnapshot snapshot;
            try {
                ConfigSnapshot submitted = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
                if (llmAdmissionService != null) {
                    var admitted = llmAdmissionService.admit(config);
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

            // Concurrency gate: the config row is already locked above.
            {
                long runningCount = jobRepository.countByConfigIdAndStatusIn(
                    config.getId(),
                    Set.of(AgentJobStatus.RUNNING)
                );
                if (runningCount >= config.getMaxConcurrentJobs()) {
                    concurrencyRejected.increment();
                    log.info(
                        "Concurrency limit reached: jobId={}, configId={}, running={}, max={}",
                        jobId,
                        config.getId(),
                        runningCount,
                        config.getMaxConcurrentJobs()
                    );
                    return ClaimOutcome.CONCURRENCY_FULL;
                }
            }

            Instant claimedAt = Instant.now();
            // Claim latency (#1368 hardening): time between eligibility and claim — the canonical
            // queue-health signal (judoscale) alongside agent.queue.oldest_age_seconds. availableAt is
            // null only for rows written before this column existed; skip rather than record garbage.
            if (job.getAvailableAt() != null && !job.getAvailableAt().isAfter(claimedAt)) {
                claimLatency.record(Duration.between(job.getAvailableAt(), claimedAt));
            }
            job.setStatus(AgentJobStatus.RUNNING);
            job.setStartedAt(claimedAt);
            job.setExecutionStartedAt(null);
            job.setWorkerId(workerId); // owner for cancel routing, orphan recovery, and terminal-write fencing (#1138)
            job.setConfigSnapshot(snapshot.toJson(objectMapper));
            jobRepository.save(job);

            // Track locally so drain / hub-initiated cancels target only this worker's jobs, and so
            // the poll loop's capacity computation reflects this claim immediately.
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

    /** Release the review-capacity slot on any terminal transition (success/failure/cancel/timeout). */
    private void releaseCapacity() {
        capacityState.ifPresent(WorkerCapacityState::releaseReview);
    }

    /**
     * Terminal RUNNING→{@code status} transition, fenced to this worker's ownership when a worker id
     * is known (#1138): if the job was orphan-requeued to a sibling, this worker's late write finds a
     * different {@code worker_id} and no-ops instead of clobbering the sibling's run. Falls back to an
     * unfenced transition only when no worker identity exists (worker role off) — where there are no
     * siblings to fence against.
     *
     * @return rows updated (0 if no longer RUNNING or no longer owned by this worker)
     */
    private int transitionTerminal(UUID jobId, AgentJobStatus status, Instant now, String error) {
        return workerId != null
            ? jobRepository.transitionStatusOwnedBy(jobId, status, now, error, Set.of(AgentJobStatus.RUNNING), workerId)
            : jobRepository.transitionStatus(jobId, status, now, error, Set.of(AgentJobStatus.RUNNING));
    }

    // Complete: micro-transaction #2

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
     * Determine the terminal status based on sandbox execution outcome and agent output.
     *
     * <p>If the sandbox exited with a non-zero code but the agent still produced valid output
     * (e.g., the runner validation was stricter than the Java-side parser, or the agent wrote
     * result.json but the process exited 1 due to an unrelated error), we treat it as COMPLETED
     * so findings get delivered. The non-zero exit is logged for diagnostics.
     *
     * @return TIMED_OUT, FAILED, or COMPLETED
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
        // Non-zero exit: check if valid output was still produced.
        // The agent may write result.json but the runner exits 1 due to validation mismatch.
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

    /**
     * Persist terminal status and output within a single transaction, fenced to this worker.
     *
     * @return {@code true} if this worker won the terminal write (still RUNNING-and-owned); {@code false}
     *     if the job was cancelled or orphan-requeued to a sibling, in which case the caller must NOT deliver.
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
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return persistTerminalStateOnce(jobId, agentResult, sandboxResult, terminalStatus, errorMessage);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Terminal persistence attempt {}/3 failed for jobId={}: {}", attempt, jobId, e.getMessage());
            }
        }
        throw new TerminalPersistenceException(lastFailure);
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

            var usage = agentResult.usage();
            boolean unverifiable = usage == null || usage.totalCalls() <= 0;
            long inputTokens = usage != null ? nullToZero(usage.inputTokens()) : 0L;
            long outputTokens = usage != null ? nullToZero(usage.outputTokens()) : 0L;
            long cacheReadTokens = usage != null ? nullToZero(usage.cacheReadTokens()) : 0L;
            long cacheWriteTokens = usage != null ? nullToZero(usage.cacheWriteTokens()) : 0L;
            long reasoningTokens = usage != null ? nullToZero(usage.reasoningTokens()) : 0L;
            int totalCalls = usage != null ? usage.totalCalls() : 0;
            if (
                totalCalls > 0 &&
                inputTokens == 0L &&
                outputTokens == 0L &&
                cacheReadTokens == 0L &&
                cacheWriteTokens == 0L &&
                reasoningTokens == 0L
            ) {
                unverifiable = true;
            }

            if (usage != null && usage.totalCalls() > 0) {
                freshJob.setLlmTotalCalls(usage.totalCalls());
                freshJob.setLlmTotalInputTokens(usage.inputTokens());
                freshJob.setLlmTotalOutputTokens(usage.outputTokens());
                freshJob.setLlmTotalReasoningTokens(usage.reasoningTokens());
                freshJob.setLlmCacheReadTokens(usage.cacheReadTokens());
                freshJob.setLlmCacheWriteTokens(usage.cacheWriteTokens());
            }
            // Provider output is telemetry only. The admitted snapshot is authoritative identity.
            freshJob.setLlmModel(snapshot.upstreamModelId());
            freshJob.setLlmModelVersion(snapshot.modelVersion());
            jobRepository.saveAndFlush(freshJob);

            LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.from(freshJob.getJobType()),
                LlmUsageSourceType.AGENT_JOB,
                freshJob.getId(),
                freshJob.getRetryCount(),
                snapshot.upstreamModelId(),
                inputTokens,
                outputTokens,
                cacheReadTokens,
                cacheWriteTokens,
                reasoningTokens,
                totalCalls,
                price,
                Instant.now()
            );
            if (unverifiable) {
                usageRecorder.recordUnverifiable(freshJob.getWorkspace().getId(), sample);
            } else {
                usageRecorder.record(freshJob.getWorkspace().getId(), sample);
            }
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private static final class TerminalPersistenceException extends RuntimeException {

        private TerminalPersistenceException(Throwable cause) {
            super("Could not durably persist terminal job result and usage", cause);
        }
    }

    private LlmPriceSnapshot admittedPrice(ConfigSnapshot snapshot) {
        if (snapshot.priceSnapshot() != null) return snapshot.priceSnapshot();
        if (llmAdmissionService != null) {
            throw new IllegalStateException("Started job has no admitted LLM price snapshot");
        }
        return new LlmPriceSnapshot(FundingSource.INSTANCE, PricingState.UNPRICED, null, null, null, null, null, null);
    }

    private static long nullToZero(@Nullable Integer value) {
        return value != null ? value : 0L;
    }

    /**
     * Deliver results to external systems (outside transaction — may call external APIs).
     * Only delivers if the job completed successfully.
     */
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
                // Preserve comment ID from partial delivery (e.g., comment posted but practice detection failed)
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
