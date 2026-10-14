package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.gateway.WorkspaceBudgetExceededException;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewExecutionHandler;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ReviewSourceNotReadyException;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeAgentRequest;
import de.tum.cit.aet.hephaestus.agent.practice.PracticePiAdapter;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.runtime.StorableText;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetDecision;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.PracticeReviewRefusalMetrics;
import de.tum.cit.aet.hephaestus.observability.StructuredLogKeys;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Polls the {@code review_job} table for {@code QUEUED} work: the queue IS the table, so a QUEUED
 * insert is the enqueue. Claim runs synchronously on the poll thread; execution is handed to the
 * {@code sandboxExecutor} so the poll thread is never blocked by a running sandbox.
 *
 * <p>Claim and terminal write each get their own short transaction; execution runs between them with
 * no transaction and no DB connection held.
 */
@Component
@ConditionalOnWorkerRole
@ConditionalOnBooleanProperty(RuntimeRole.AGENT_ENABLED_PROPERTY)
@WorkspaceAgnostic("Job poller processes jobs across all workspaces")
public class ReviewJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobExecutor.class);

    private static final String MDC_JOB_ID = StructuredLogKeys.JOB_ID;
    private static final String MDC_JOB_TYPE = "agent.jobType";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    // How long a claim-blocked job waits before the poll loop re-evaluates the cap.
    private static final Duration BUDGET_HOLD_INTERVAL = Duration.ofHours(1);
    // Measured from submission, not from when the hold started: what goes stale is the work the job
    // would produce, and bounding the hold instead would let short repeated holds run forever.
    private static final Duration BUDGET_HOLD_MAX_JOB_AGE = Duration.ofDays(7);

    /**
     * When the terminal accounting write is worth re-attempting. NOT the same question as
     * {@link #isRetryableExecutionFailure}, which requeues the whole JOB — conflating the two would charge
     * twice for a job that already spent money. {@code includes} matches nested causes, which is what
     * makes these usable directly against JPA's and {@code TransactionTemplate}'s wrapping.
     */
    static final RetryPolicy TERMINAL_PERSIST_POLICY = RetryPolicy.builder()
            .includes(
                    TransientDataAccessException.class,
                    RecoverableDataAccessException.class,
                    DataAccessResourceFailureException.class,
                    CannotCreateTransactionException.class)
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            // Growing rather than fixed: a lock timeout or a failover outlasts an immediate retry.
            .multiplier(2)
            .build();

    /**
     * Any failure of the pool-rejection requeue is worth another try: one statement, no side effect to
     * undo, and not landing it strands a job until the zombie sweeper notices. Runs on the poll thread,
     * so full exhaustion parks polling for the summed delay.
     */
    private static final RetryPolicy REQUEUE_REJECTED_CLAIM_POLICY = RetryPolicy.builder()
            .includes(Exception.class)
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            .build();

    private final AgentProperties agentProperties;
    private final ReviewJobRepository jobRepository;
    private final ReviewMemberAiPolicy memberAiPolicy;
    private final ReviewMethodVerifier methods;
    private final ReviewExecutionHandler handler;
    private final ReviewJobAssignments assignments;
    private final PracticePiAdapter practiceAgent;
    private final WorkerJwtIssuer workerJwtIssuer;

    private final SandboxManager sandboxManager;
    private final AsyncTaskExecutor sandboxExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final PracticeReviewRefusalMetrics practiceReviewRefusalMetrics;
    private final LlmUsageRecorder usageRecorder;
    private final PracticeScriptRunRecorder practiceScriptRuns;
    private final LlmBudgetService llmBudgetService;
    private final @Nullable LlmAdmissionService llmAdmissionService;

    // RetryTemplate, not @Retryable: both wrap private methods called from inside this class, and a
    // self-invocation never reaches the proxy the annotation needs.
    private final RetryTemplate terminalPersistRetries = retryTemplate(TERMINAL_PERSIST_POLICY);
    private final RetryTemplate requeueRetries = retryTemplate(REQUEUE_REJECTED_CLAIM_POLICY);

    private final Counter concurrencyRejected;
    private final Timer claimLatency;
    private final Counter infraRetryRequeued;
    private final ReviewJobTelemetry jobTelemetry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile @Nullable Thread pollThread;
    private final Phaser inFlight = new Phaser(1); // 1 = the executor itself; deregistered on stop
    /** Scopes drain and hub-initiated cancels to this worker's own jobs; also its free-capacity signal. */
    private final Set<UUID> localRunningJobs = ConcurrentHashMap.newKeySet();

    private final Optional<WorkerCapacityState> capacityState;
    /** Null only when the worker role is off; stamped on claimed jobs to fence terminal writes. */
    private final @Nullable String workerId;
    /** Poll-thread-owned, hence unsynchronized. */
    private boolean lastClaimPoolRejected;

    /** Stages a writer continuing an admitted review; without it such an attempt fails as unrecoverable. */
    private @Nullable ReviewCompositionContinuation compositionContinuation;

    @Autowired(required = false)
    void setCompositionContinuation(ReviewCompositionContinuation compositionContinuation) {
        this.compositionContinuation = compositionContinuation;
    }

    @Autowired
    public ReviewJobExecutor(
            AgentProperties agentProperties,
            ReviewJobRepository jobRepository,
            ReviewMemberAiPolicy memberAiPolicy,
            ReviewExecutionHandler handler,
            ReviewJobAssignments assignments,
            PracticePiAdapter practiceAgent,
            WorkerJwtIssuer workerJwtIssuer,
            SandboxManager sandboxManager,
            @Qualifier("sandboxExecutor") AsyncTaskExecutor sandboxExecutor,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            PracticeReviewRefusalMetrics practiceReviewRefusalMetrics,
            ReviewJobTelemetry jobTelemetry,
            LlmUsageRecorder usageRecorder,
            PracticeScriptRunRecorder practiceScriptRuns,
            LlmBudgetService llmBudgetService,
            @Nullable LlmAdmissionService llmAdmissionService,
            Optional<WorkerCapacityState> capacityState,
            Optional<WorkerProperties> workerProperties,
            ReviewMethodVerifier methods) {
        this.agentProperties = agentProperties;
        this.jobRepository = jobRepository;
        this.memberAiPolicy = memberAiPolicy;
        this.methods = methods;
        this.handler = handler;
        this.assignments = assignments;
        this.practiceAgent = practiceAgent;
        this.workerJwtIssuer = workerJwtIssuer;
        this.sandboxManager = sandboxManager;
        this.sandboxExecutor = sandboxExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.practiceReviewRefusalMetrics = practiceReviewRefusalMetrics;
        this.jobTelemetry = jobTelemetry;
        this.usageRecorder = usageRecorder;
        this.practiceScriptRuns = practiceScriptRuns;
        this.llmBudgetService = llmBudgetService;
        this.llmAdmissionService = llmAdmissionService;
        this.capacityState = capacityState;
        this.workerId = workerProperties.map(WorkerProperties::resolvedWorkerId).orElse(null);

        this.concurrencyRejected = Counter.builder(AgentMetrics.AGENT_JOB_CONCURRENCY_REJECTED)
                .description("Jobs rejected due to concurrency limits")
                .register(meterRegistry);
        this.claimLatency = Timer.builder(AgentMetrics.AGENT_JOB_CLAIM_LATENCY)
                .description("Time between a job becoming available (available_at) and being claimed")
                .register(meterRegistry);
        this.infraRetryRequeued = Counter.builder(AgentMetrics.AGENT_JOB_INFRA_RETRY_REQUEUED)
                .description("Jobs requeued (not failed) after a retryable preparation or infrastructure failure")
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
                agentProperties.claimBatchSize());
    }

    private static final Duration POLL_THREAD_JOIN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Stops the poll loop so no new jobs are claimed. Idempotent.
     *
     * <p>Joining the poll thread is what closes the drain admission race: an in-flight claim could
     * otherwise register with {@link #inFlight} after {@link #awaitInFlight(Duration)} was called, and
     * the Phaser would put that registration in the NEXT phase — so the coordinator's await returns at
     * once believing drain was clean, and the late-claimed job is neither awaited nor cancelled.
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
                        POLL_THREAD_JOIN_TIMEOUT);
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

    /** One envelope for all container stops of a drain, not one per job. */
    private static final Duration DRAIN_CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Settles this worker's jobs before stopping their containers. Unadmitted attempts may requeue;
     * admitted attempts retain their results and fail without repeating the review. The drain
     * contract is in docs/admin/runtime-roles.mdx.
     *
     * @return {@code true} if every job this worker still owned was settled and every container stop
     *     it issued returned; {@code false} if a settlement failed or a stop failed, did not return in
     *     time, or was interrupted
     */
    public boolean cancelInFlight(ReviewJobCancellationReason reason) {
        return cancelInFlight(reason, DRAIN_CLEANUP_TIMEOUT);
    }

    /**
     * Settles every job first, one short transaction each, so no container stop delays custody. Only a
     * job whose settlement transition won here is stopped: a job another worker or a cancellation now
     * owns keeps running there.
     */
    boolean cancelInFlight(ReviewJobCancellationReason reason, Duration cleanupTimeout) {
        Set<UUID> snapshot = Set.copyOf(localRunningJobs);
        if (snapshot.isEmpty()) return true;
        log.info("Draining {} in-flight job(s) owned by this worker with reason {}", snapshot.size(), reason);
        boolean settledAll = true;
        List<UUID> settled = new ArrayList<>();
        for (UUID jobId : snapshot) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> settleForDrain(jobId, reason)))) {
                    settled.add(jobId);
                }
            } catch (Exception e) {
                settledAll = false;
                log.warn(
                        "Failed to settle in-flight job {} for drain: {}",
                        jobId,
                        e.getClass().getSimpleName());
            }
        }
        boolean stopped = stopContainers(settled, cleanupTimeout);
        return settledAll && stopped;
    }

    /** Settles one job under its ownership fence; true only if a transition made here won. */
    private boolean settleForDrain(UUID jobId, ReviewJobCancellationReason reason) {
        ReviewCompositionContinuation continuing = compositionContinuation;
        if (continuing != null) continuing.holdRecoveryCustody();
        ReviewJob job = jobRepository.lockById(jobId).orElse(null);
        if (job == null) return false;
        // BEFORE requeuing: the requeue zeroes the accumulators, so a later read bills zero.
        ReviewJobLlmUsage drainCounts = job.getExecutionStartedAt() != null
                ? jobRepository.findLlmUsageById(jobId).orElse(null)
                : null;
        int updated = workerId != null ? requeueOrphanWithRotation(jobId, workerId, job.getAttempt()) : 0;
        // An attempt whose results were admitted is never measured again: the requeue refused it. Its
        // public writer continues from the original inputs if its admission kept them; otherwise it ends here.
        if (updated == 0 && workerId != null) {
            updated = continuing != null ? requeueForComposition(continuing, job, workerId) : 0;
        }
        if (updated == 0 && workerId != null) {
            updated = jobRepository.failAdmittedOwnedBy(
                    jobId,
                    workerId,
                    job.getAttempt(),
                    Instant.now(),
                    ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION);
        }
        if (updated > 0) {
            if (job.getExecutionStartedAt() != null) {
                billTerminatedJob(job, "worker draining", drainCounts);
            }
            return true;
        }
        int cancelled = workerId != null
                ? jobRepository.transitionToCancelledOwnedBy(
                        jobId, Instant.now(), "worker draining", reason, Set.of(ReviewJobStatus.RUNNING), workerId)
                : jobRepository.transitionToCancelled(
                        jobId, Instant.now(), "worker draining", reason, Set.of(ReviewJobStatus.RUNNING));
        if (cancelled > 0 && job.getExecutionStartedAt() != null) {
            billTerminatedJob(job, "worker draining");
        }
        return cancelled > 0;
    }

    /**
     * Stops the settled jobs' containers concurrently under one deadline. Platform threads, because
     * docker-java's blocking I/O pins a virtual thread; a pool of their own, because the sandbox pool's
     * threads are the ones still waiting on these containers. A stop blocked in native I/O may ignore
     * the interrupt and finish after this returns; custody has already been settled, and daemon
     * threads allow shutdown to continue.
     */
    private boolean stopContainers(List<UUID> jobIds, Duration timeout) {
        if (jobIds.isEmpty()) return true;
        List<Callable<Object>> stops = jobIds.stream()
                .map(jobId -> Executors.callable(() -> sandboxManager.cancel(jobId)))
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(
                stops.size(),
                Thread.ofPlatform().name("agent-drain-stop-", 0).daemon(true).factory());
        try {
            List<Future<Object>> results = pool.invokeAll(stops, timeout.toMillis(), TimeUnit.MILLISECONDS);
            boolean acknowledged = true;
            for (int i = 0; i < results.size(); i++) {
                Future<Object> result = results.get(i);
                if (result.state() == Future.State.SUCCESS) continue;
                acknowledged = false;
                log.warn(
                        "Container stop for drained job {} {}",
                        jobIds.get(i),
                        result.state() == Future.State.FAILED
                                ? "failed: " + result.exceptionNow().getClass().getSimpleName()
                                : "did not return within " + timeout);
            }
            return acknowledged;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while stopping {} drained job container(s)", jobIds.size());
            return false;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Stops the container only; the authoritative status transition already happened hub-side before
     * the {@code CancelJob} frame was dispatched.
     *
     * @return true if this worker owns the job and a stop was requested
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
     * The sandbox pool's size is the hard bound: worker capacity and pool size are separate knobs, so a
     * capacity larger than the pool must not claim jobs the pool would then reject. What occupies a slot
     * is a job this worker holds, not a thread the pool reports busy: the pool hands a task straight to
     * a thread, so between submit and that thread entering the task it reports the slot idle, and a
     * claim made on that reading is rejected on submit and requeued for nothing.
     */
    int computeCapacity() {
        int concurrency = Math.min(
                capacityState.map(WorkerCapacityState::reviewMax).orElse(Integer.MAX_VALUE),
                sandboxExecutorConcurrency());
        int free = concurrency == Integer.MAX_VALUE
                ? agentProperties.claimBatchSize()
                : Math.max(0, concurrency - localRunningJobs.size());
        return Math.min(free, agentProperties.claimBatchSize());
    }

    /** Only ever narrows {@link #computeCapacity()}: an unknown executor type imposes no bound. */
    private int sandboxExecutorConcurrency() {
        return sandboxExecutor instanceof ThreadPoolTaskExecutor pool ? pool.getMaxPoolSize() : Integer.MAX_VALUE;
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
     * @return true if the job was claimed and dispatched. Anything else leaves it QUEUED for the next
     *     poll, except the {@link ClaimOutcome}s {@link #claimJob} has already cancelled outright.
     */
    boolean processJob(UUID jobId) {
        ClaimAttempt attempt;
        try {
            attempt = claimJob(jobId);
        } catch (CannotAcquireLockException e) {
            log.debug("Lock timeout during claim for job {}, will retry on next poll", jobId);
            return false;
        } catch (Exception e) {
            log.warn("Claim failed for job {}, will retry on next poll: {}", jobId, e.getMessage());
            return false;
        }
        if (attempt instanceof TerminalClaim terminal) {
            jobTelemetry.terminal(terminal.job(), terminal.status(), ReviewJobTelemetry.age(terminal.job()));
            return false;
        }
        if (!(attempt instanceof ClaimResult claim)) {
            return false;
        }
        // Counted after the claim commits, never inside its transaction: a failed commit rolls the row
        // back to QUEUED and no execution then runs to release the count. Since the count is the hard
        // claim bound, one leaked entry would retire a slot for this worker's lifetime.
        localRunningJobs.add(jobId);
        capacityState.ifPresent(WorkerCapacityState::claimReview);
        dispatchExecution(jobId, claim);
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
                    "Sandbox executor rejected claimed job {} (pool smaller than configured worker capacity?) —"
                            + " requeuing",
                    jobId);
            requeueRejectedClaim(jobId);
        }
    }

    /**
     * Undo a claim the sandbox executor couldn't accept: RUNNING → QUEUED, ownership cleared,
     * {@code attempt} left untouched — the job never started executing, so this must not burn its
     * retry budget.
     *
     * <p>If every attempt fails the row is deliberately left RUNNING under this worker's id (capacity
     * and local tracking are still released). The same sustained DB outage also breaks
     * {@link WorkerLivenessReporter}'s heartbeat, so {@link ReviewJobZombieSweeper} reclaims the row
     * through the normal dead-worker path once connectivity returns.
     */
    private void requeueRejectedClaim(UUID jobId) {
        try {
            requeueRetries.execute(named("Requeue of rejected claim " + jobId, () -> {
                transactionTemplate.executeWithoutResult(status -> jobRepository.requeueRejectedClaim(jobId, workerId));
                return null;
            }));
        } catch (RetryException e) {
            log.error(
                    "Failed to requeue rejected claim {} — row stays RUNNING under this worker until "
                            + "liveness/timeout recovery reclaims it",
                    jobId,
                    e.getLastException());
        } finally {
            releaseCapacity();
            localRunningJobs.remove(jobId);
        }
    }

    /**
     * A template that WARNs once per failed attempt. Without the listener a retried write is silent
     * until it gives up, so an operator cannot tell a database that wobbled from one that never
     * wobbled.
     */
    private static RetryTemplate retryTemplate(RetryPolicy policy) {
        RetryTemplate template = new RetryTemplate(policy);
        template.setRetryListener(new RetryListener() {
            @Override
            public void onRetryableExecution(RetryPolicy inUse, Retryable<?> operation, RetryState state) {
                // Fires after every execution, successful ones included; isSuccessful() is false
                // exactly when the attempt just recorded threw.
                if (state.isSuccessful()) {
                    return;
                }
                log.warn(
                        "{} failed (attempt {}): {}",
                        operation.getName(),
                        state.getExceptions().size(),
                        String.valueOf(state.getLastException()));
            }
        });
        return template;
    }

    /** Names a unit of work so the retry log says which write is failing. */
    private static <R> Retryable<R> named(String name, Supplier<@Nullable R> work) {
        return new Retryable<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public R execute() {
                return work.get();
            }
        };
    }

    /** Runs on the sandbox executor, not the poll thread. */
    private void runClaimedJob(UUID jobId, ClaimResult claim) {
        ReviewJob job = claim.job;
        var executionSpan = jobTelemetry.startExecution(job);
        var executionScope = jobTelemetry.executionScope(executionSpan);
        MDC.put(StructuredLogKeys.TRACE_ID, executionSpan.context().traceId());
        MDC.put(StructuredLogKeys.SPAN_ID, executionSpan.context().spanId());
        MDC.put(
                StructuredLogKeys.TRACE_FLAGS,
                Boolean.TRUE.equals(executionSpan.context().sampled()) ? "01" : "00");
        MDC.put(MDC_JOB_ID, jobId.toString());
        MDC.put(StructuredLogKeys.WORKSPACE_ID, Long.toString(job.getWorkspaceId()));
        MDC.put(MDC_JOB_TYPE, assignments.load(job).getFirst().item().kind().value());
        Instant startTime = Instant.now();
        String metricOutcome = "unknown";
        boolean sandboxExecutionStarted = false;
        // The stage belongs to this run, including failures before input preparation begins.
        var preparationStage = new AtomicReference<>(PreparationFailure.Stage.RUN_SETUP);
        PreparedJobInputs stagedInputs = null;
        try {
            Duration queueDuration = Duration.between(job.getAvailableAt(), job.getStartedAt());
            jobTelemetry.transition(
                    job,
                    "agent.job.started",
                    ReviewJobTelemetry.Phase.QUEUE,
                    ReviewJobTelemetry.ResultOutcome.STARTED,
                    queueDuration);

            PreparedSandbox preparedSandbox = prepareSandboxSpec(jobId, job, claim.snapshot, preparationStage);
            stagedInputs = preparedSandbox.stagedInputs();
            SandboxSpec sandboxSpec = preparedSandbox.spec();
            // Past this boundary provider usage may exist even if execute() throws, so it is persisted
            // for recovery on another process. A lost fence means the job was cancelled or requeued
            // while preparation ran, so its sandbox must not start.
            preparationStage.set(PreparationFailure.Stage.EXECUTION_FENCE);
            if (!markExecutionStarted(jobId)) {
                metricOutcome = "OWNERSHIP_LOST";
                log.info("Skipped sandbox start after execution fence was lost: jobId={}", jobId);
                return;
            }
            sandboxExecutionStarted = true;
            SandboxResult result = sandboxManager.execute(sandboxSpec);
            AgentResult agentResult = practiceAgent.parseResult(result);

            // Two exits say the run could not reach something it needed, rather than anything about the
            // reviewed work: the review finished and could not admit it, because the sandbox holds an
            // address the server has since moved away from; or no practice was reached at all because
            // every model call went unanswered. Neither leaves anything to deliver, and neither is a
            // fact a second attempt would repeat. Past the retry cap both fall through and terminalize.
            String unreachable = unreachableReason(result.exitCode());
            if (unreachable != null && requeueForAnotherAttempt(jobId, job, unreachable, true)) {
                metricOutcome = "REQUEUED";
                log.warn(
                        "Requeuing job {} ({}): nothing it measured can be delivered from here (attempt {})",
                        jobId,
                        unreachable,
                        job.getAttempt() + 1);
                return;
            }

            ReviewJobStatus terminalStatus = completeJob(jobId, agentResult, result);
            metricOutcome = terminalStatus != null ? terminalStatus.name() : "unknown";
            ReviewCompositionContinuation kept = compositionContinuation;
            if (terminalStatus != null
                    && !Set.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING).contains(terminalStatus)
                    && kept != null) {
                // Its result is recorded: no writer reads the original inputs again. The retention sweep removes
                // what a failure here leaves.
                try {
                    kept.forget(job);
                } catch (RuntimeException e) {
                    log.warn("Could not remove original composition inputs: jobId={}", jobId, e);
                }
            }

            log.info("Agent job completed: jobId={}, duration={}", jobId, Duration.between(startTime, Instant.now()));
        } catch (SandboxCancelledException e) {
            metricOutcome = handleCancellation(jobId, job) ? ReviewJobStatus.CANCELLED.name() : "OWNERSHIP_LOST";
        } catch (WorkspaceBudgetExceededException e) {
            ObjectNode output = objectMapper
                    .createObjectNode()
                    .put("outcome", "WORKSPACE_REFUSED")
                    .put("reasonCode", "WORKSPACE_BUDGET_EXCEEDED");
            output.set("details", objectMapper.valueToTree(e.getBody().getProperties()));
            Integer updated = transactionTemplate.execute(status -> jobRepository.transitionToEvidenceRefused(
                    jobId, workerId, job.getAttempt(), Instant.now(), output));
            metricOutcome = updated != null && updated == 1 ? "WORKSPACE_BUDGET_EXCEEDED" : "OWNERSHIP_LOST";
            if (updated != null && updated == 1) {
                recordPracticeReviewRefusal("workspace_budget_exceeded");
                jobTelemetry.terminal(job, ReviewJobStatus.COMPLETED, ReviewJobTelemetry.age(job));
            }
        } catch (InsufficientEvidenceException e) {
            // The evidence it carries was never staged for an attempt, so nothing else releases it.
            try (PreparedJobInputs refused = e.preparedInputs()) {
                persistRefusedEvidence(jobId, job.getAttempt(), refused);
                ObjectNode output = objectMapper.createObjectNode().put("outcome", "INSUFFICIENT_EVIDENCE");
                Integer updated = transactionTemplate.execute(status -> jobRepository.transitionToEvidenceRefused(
                        jobId, workerId, job.getAttempt(), Instant.now(), output));
                if (updated != null && updated == 1) {
                    recordPracticeReviewRefusal("insufficient_evidence");
                    // The metric outcome names the refusal; the lifecycle contract records the committed
                    // terminal state as COMPLETED.
                    metricOutcome = "INSUFFICIENT_EVIDENCE";
                    jobTelemetry.terminal(job, ReviewJobStatus.COMPLETED, ReviewJobTelemetry.age(job));
                    log.info(
                            "Completed agent job without model execution: jobId={}, outcome=INSUFFICIENT_EVIDENCE",
                            jobId);
                } else {
                    metricOutcome = "OWNERSHIP_LOST";
                }
            } catch (Exception persistenceFailure) {
                metricOutcome = handleExecutionFailure(
                        jobId,
                        job,
                        persistenceFailure,
                        false,
                        PreparationFailure.of(PreparationFailure.Stage.PROVENANCE, persistenceFailure));
            }
        } catch (TerminalPersistenceException e) {
            // Provider work already completed. Leave RUNNING for the zombie sweeper to terminalize
            // and account as UNPRICED; never execute the provider a second time.
            log.error("Terminal job persistence failed after provider completion: jobId={}", jobId, e);
            metricOutcome = "PERSISTENCE_FAILED";
        } catch (Exception e) {
            metricOutcome = handleExecutionFailure(
                    jobId,
                    job,
                    e,
                    sandboxExecutionStarted,
                    sandboxExecutionStarted ? null : PreparationFailure.of(preparationStage.get(), e));
        } finally {
            // Admission and delivery are done by now, so the attempt's evidence can be retired: deleted
            // once its results were admitted, kept for the cleanup grace otherwise.
            if (stagedInputs != null) {
                stagedInputs.close();
            }
            Duration executionDuration = Duration.between(startTime, Instant.now());
            recordExecutionDuration(
                    assignments.load(job).getFirst().item().kind().value(), metricOutcome, executionDuration);
            jobTelemetry.record(ReviewJobTelemetry.Phase.EXECUTION, executionDuration);
            ReviewJobStatus terminalStatus = terminalStatus(metricOutcome);
            if (terminalStatus != null) {
                jobTelemetry.terminal(job, terminalStatus, ReviewJobTelemetry.age(job));
            }
            releaseCapacity();
            localRunningJobs.remove(jobId);
            MDC.remove(MDC_JOB_ID);
            MDC.remove(StructuredLogKeys.WORKSPACE_ID);
            MDC.remove(MDC_JOB_TYPE);
            MDC.remove(StructuredLogKeys.TRACE_ID);
            MDC.remove(StructuredLogKeys.SPAN_ID);
            MDC.remove(StructuredLogKeys.TRACE_FLAGS);
            executionSpan.tag("hephaestus.job.outcome", metricOutcome);
            executionScope.close();
            executionSpan.end();
        }
    }

    private static @Nullable ReviewJobStatus terminalStatus(String outcome) {
        try {
            ReviewJobStatus status = ReviewJobStatus.valueOf(outcome);
            return !Set.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING).contains(status) ? status : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Package-private: the zombie sweeper reuses it when it restores a reaped job's trace context. */
    static String randomSpanId() {
        long value;
        do {
            value = ThreadLocalRandom.current().nextLong();
        } while (value == 0);
        return "%016x".formatted(value);
    }

    private boolean markExecutionStarted(UUID jobId) {
        Integer updated = transactionTemplate.execute(
                status -> jobRepository.markExecutionStarted(jobId, workerId, Instant.now()));
        return updated != null && updated == 1;
    }

    private void recordExecutionDuration(String itemKind, String outcome, Duration duration) {
        Timer.builder(AgentMetrics.AGENT_JOB_EXECUTION_DURATION)
                .description("Total duration of agent job execution")
                .tag("itemKind", itemKind)
                .tag("status", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Prepared inputs stay available through execution and admission; the caller releases them on every outcome.
     */
    private record PreparedSandbox(SandboxSpec spec, PreparedJobInputs stagedInputs) {}

    /** No provider execution happens here, so no LLM cost accrues if this throws. */
    private PreparedSandbox prepareSandboxSpec(
            UUID jobId, ReviewJob job, ConfigSnapshot snapshot, AtomicReference<PreparationFailure.Stage> stage) {
        stage.set(PreparationFailure.Stage.INPUTS);
        ReviewExecutionHandler handler = this.handler;

        ReviewJob preparedJob = jobRepository.findById(jobId).orElse(job);
        // A continuing writer captures nothing: it is staged from its admission's original inputs, and the job keeps
        // the evidence record that admission verified against.
        boolean continuation = ResultAdmissionService.isContinuation(preparedJob);
        ReviewCompositionContinuation continuing = compositionContinuation;
        if (continuation && continuing == null) {
            throw new JobPreparationException("The review cannot continue: this runtime cannot stage its writer");
        }
        PreparedJobInputs preparedInputs = continuation
                ? Objects.requireNonNull(continuing).prepareInputs(preparedJob)
                : handler.prepareInputs(preparedJob);

        try {
            stage.set(PreparationFailure.Stage.CREDENTIAL);
            // Sandboxes access providers through the LLM proxy with an attempt-scoped credential.
            Instant workDeadline =
                    Instant.now().plusSeconds(snapshot.timeoutSeconds()).truncatedTo(ChronoUnit.SECONDS);
            Instant tokenExpiry = workDeadline.plus(SandboxLayout.RESULT_UPLOAD_GRACE);
            String jobToken =
                    workerJwtIssuer.issueForJobUntil(jobId, job.getWorkspaceId(), job.getAttempt(), tokenExpiry);
            stage.set(PreparationFailure.Stage.SPEC);
            PracticeAgentRequest adapterRequest = new PracticeAgentRequest(
                    snapshot.apiProtocol(),
                    snapshot.upstreamModelId(),
                    snapshot.contextWindow(),
                    snapshot.maxOutputTokens(),
                    snapshot.reasoningEffort(),
                    jobToken,
                    snapshot.timeoutSeconds(),
                    snapshot.practiceScriptModels());

            PracticeSandboxSpec agentSpec = practiceAgent.buildSandboxSpec(adapterRequest);
            SandboxSpec sandboxSpec = buildSandboxSpec(
                    jobId,
                    Map.of(),
                    preparedInputs.filesOnDisk(),
                    preparedInputs.directories(),
                    agentSpec,
                    snapshot,
                    workDeadline,
                    job.getAttempt());
            stage.set(PreparationFailure.Stage.PROVENANCE);
            if (!continuation) {
                persistProvenanceDigests(
                        jobId,
                        agentSpec.promptDigest(),
                        sandboxSpec.inputFiles(),
                        preparedInputs.filesOnDisk(),
                        preparedInputs.directories(),
                        job.getAttempt(),
                        preparedInputs.automatedReviewReadinessReport());
            }
            return new PreparedSandbox(sandboxSpec, preparedInputs);
        } catch (RuntimeException exception) {
            preparedInputs.close();
            throw exception;
        }
    }

    private void persistRefusedEvidence(UUID jobId, int attempt, PreparedJobInputs preparedInputs) {
        persistProvenanceDigests(
                jobId,
                null,
                Map.of(),
                preparedInputs.filesOnDisk(),
                preparedInputs.directories(),
                attempt,
                preparedInputs.automatedReviewReadinessReport());
    }

    /**
     * Deliberately not best-effort: a result that cannot be tied to the inputs that produced it
     * is unfixable evaluation data, so a failed write fails the run before any LLM cost accrues.
     */
    private void persistProvenanceDigests(
            UUID jobId,
            @Nullable String promptDigest,
            Map<String, byte[]> inputFiles,
            Map<String, Path> inputPaths,
            List<EvidenceDirectory> inputDirectories,
            int attempt,
            @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport) {
        String inputsDigest = ProvenanceDigest.inputsDigestHex(inputFiles, inputPaths, inputDirectories, jobId);
        JsonNode capture = capture(snapshotMetadata(inputFiles, inputPaths), automatedReviewReadinessReport);
        var stamp = capture instanceof ObjectNode object ? object : objectMapper.createObjectNode();
        if (promptDigest != null) stamp.put("promptDigest", promptDigest);
        stamp.put("inputsDigest", inputsDigest);
        stamp.set(
                "directories",
                objectMapper.valueToTree(
                        inputDirectories.stream().map(EvidenceDirectory::target).toList()));
        Integer updated =
                transactionTemplate.execute(status -> jobRepository.updateCapture(jobId, workerId, attempt, stamp));
        if (updated == null || updated != 1) {
            throw new IllegalStateException("Provenance digest write matched no job row: jobId=" + jobId);
        }
        log.debug("Provenance digests: jobId={}, prompt={}, inputs={}", jobId, promptDigest, inputsDigest);
    }

    /** The manifest and admitted practices as the sandbox sees them, and the core of the work it staged. */
    private static Map<String, byte[]> snapshotMetadata(Map<String, byte[]> scaffolding, Map<String, Path> paths) {
        var metadata = new HashMap<>(scaffolding);
        for (String path : List.of(
                SandboxLayout.MANIFEST_PATH,
                SandboxLayout.PRACTICES_PREFIX + "index.json",
                SandboxLayout.CONTEXT_PREFIX + "metadata.json",
                SandboxLayout.CONTEXT_PREFIX + "issue_metadata.json")) {
            Path source = paths.get(path);
            if (source != null) {
                try {
                    metadata.put(path, Files.readAllBytes(source));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        }
        return metadata;
    }

    /**
     * {@code practices} is what the model was asked, the readiness report what was ready, and
     * {@code answeredPractices} the ready practices an earlier completed review had already answered on the same code.
     */
    private @Nullable JsonNode capture(
            Map<String, byte[]> inputFiles, @Nullable AutomatedReviewReadinessReport automatedReviewReadinessReport) {
        byte[] manifest = inputFiles.get(SandboxLayout.MANIFEST_PATH);
        byte[] practices = inputFiles.get(SandboxLayout.PRACTICES_PREFIX + "index.json");
        // Java null, not NullNode: NullNode serializes to the JSON value null, which is a non-SQL-NULL
        // jsonb and so passes every IS NOT NULL predicate a reader writes against this column.
        if (manifest == null && practices == null && automatedReviewReadinessReport == null) return null;
        if (manifest == null) {
            throw new IllegalStateException("Practice review inputs have an incomplete evidence snapshot");
        }
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("manifest", objectMapper.readTree(manifest));
        if (practices != null) {
            snapshot.set("practices", objectMapper.readTree(practices));
        }
        if (automatedReviewReadinessReport != null)
            snapshot.set("readiness", objectMapper.valueToTree(automatedReviewReadinessReport));
        return snapshot;
    }

    private static SandboxSpec buildSandboxSpec(
            UUID jobId,
            Map<String, byte[]> handlerFiles,
            Map<String, Path> handlerFilesOnDisk,
            List<EvidenceDirectory> handlerDirectories,
            PracticeSandboxSpec agentSpec,
            ConfigSnapshot snapshot,
            Instant workDeadline,
            int attempt) {
        Map<String, byte[]> allInputFiles = new HashMap<>(handlerFiles);
        allInputFiles.putAll(agentSpec.inputFiles());
        Map<String, String> environment = new HashMap<>(agentSpec.environment());
        environment.put("SANDBOX_WORK_DEADLINE_MS", Long.toString(workDeadline.toEpochMilli()));

        ResourceLimits limits = new ResourceLimits(
                ResourceLimits.DEFAULT.memoryBytes(),
                ResourceLimits.DEFAULT.cpus(),
                ResourceLimits.DEFAULT.pidsLimit(),
                Duration.ofSeconds(snapshot.timeoutSeconds()));

        return new SandboxSpec(
                jobId,
                agentSpec.image(),
                agentSpec.command(),
                environment,
                agentSpec.networkPolicy(),
                limits,
                agentSpec.securityProfile(),
                allInputFiles,
                handlerFilesOnDisk,
                handlerDirectories,
                agentSpec.outputPath(),
                attempt);
    }

    private boolean handleCancellation(UUID jobId, ReviewJob job) {
        Boolean cancelled = transactionTemplate.execute(status -> {
            int updated =
                    transitionTerminal(jobId, ReviewJobStatus.CANCELLED, Instant.now(), "Cancelled during execution");
            if (updated > 0) billTerminatedJob(job, "cancelled during execution");
            return updated > 0;
        });
        if (Boolean.TRUE.equals(cancelled)) {
            log.info("Agent job cancelled: jobId={}", jobId);
            return true;
        }
        return false;
    }

    /** Reads the counts itself, so this overload is safe only on terminal (non-requeue) paths. */
    private void billTerminatedJob(ReviewJob job, String reason) {
        billTerminatedJob(
                job, reason, jobRepository.findLlmUsageById(job.getId()).orElse(null));
    }

    /**
     * {@code counts} MUST be read BEFORE any requeue of this job: {@link
     * ReviewJobRepository#requeueOrphan} zeroes the row's token accumulators, so a caller that requeues
     * first would bill zero and silently drop this attempt's spend.
     */
    private void billTerminatedJob(ReviewJob job, String reason, @Nullable ReviewJobLlmUsage counts) {
        ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        LlmPriceSnapshot price = terminalPriceOrUnpriced(snapshot);
        // No runner report exists on this path by definition — the job never reached a clean finish.
        TerminalUsage usage = TerminalUsage.fromProxy(counts);
        boolean billed = usage.appendTo(usageRecorder, jobRepository, job.getWorkspaceId(), job, snapshot, price);
        if (billed) {
            log.info(
                    "Recorded confirmed usage for terminated job ({}): jobId={}, calls={}",
                    reason,
                    job.getId(),
                    usage.totalCalls());
        } else {
            log.info("Recorded UNPRICED usage ledger entry ({}): jobId={}", reason, job.getId());
        }
    }

    /**
     * @param sandboxExecutionStarted whether provider execution may have started; only then can an
     *     unverifiable usage event be truthful
     * @param preparation where preparation stopped, when the sandbox never started; the row then records its fixed
     *     message instead of the failure's own, and the closed stage and kind reach telemetry
     * @return the {@code agent.job.execution.duration} outcome tag
     */
    private String handleExecutionFailure(
            UUID jobId,
            ReviewJob job,
            Exception e,
            boolean sandboxExecutionStarted,
            @Nullable PreparationFailure preparation) {
        String outcome = settleExecutionFailure(jobId, job, e, sandboxExecutionStarted, preparation);
        if (preparation != null) {
            jobTelemetry.preparationFailed(job, preparation, outcome);
        }
        return outcome;
    }

    private String settleExecutionFailure(
            UUID jobId,
            ReviewJob job,
            Exception e,
            boolean sandboxExecutionStarted,
            @Nullable PreparationFailure preparation) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        String errorMessage = preparation == null ? truncateErrorMessage(e.getMessage()) : preparation.message();
        if (preparation == null) {
            log.error("Agent job failed: jobId={}, error={}", jobId, errorMessage, e);
        }

        if (workerId != null && isRetryableExecutionFailure(e)) {
            int currentRetryCount = job.getAttempt();
            if (requeueForAnotherAttempt(
                    jobId,
                    job,
                    e instanceof ReviewSourceNotReadyException ? "source-not-ready" : "infra-failure",
                    sandboxExecutionStarted)) {
                log.warn(
                        "Requeuing job {} after retryable preparation or infrastructure failure (attempt {}): {}",
                        jobId,
                        currentRetryCount + 1,
                        errorMessage);
                return "REQUEUED";
            }
            log.warn(
                    "Job {} hit a retryable failure but could not be requeued (retry cap exhausted or fence lost) —"
                            + " failing terminally",
                    jobId);
        }

        Integer failed = transactionTemplate.execute(status -> {
            int updated = transitionTerminal(jobId, ReviewJobStatus.FAILED, Instant.now(), errorMessage);
            if (updated > 0 && preparation != null) {
                var diagnosis = objectMapper
                        .createObjectNode()
                        .put("attempt", job.getAttempt())
                        .put("stage", preparation.stage().name())
                        .put("kind", preparation.kind().name())
                        .put("reason", preparation.reason().name());
                jobRepository.recordPreparationFailure(
                        jobId,
                        job.getWorkspaceId(),
                        job.getAttempt(),
                        workerId,
                        objectMapper.writeValueAsString(diagnosis));
            }
            if (updated > 0 && sandboxExecutionStarted) billTerminatedJob(job, "execution failure");
            return updated;
        });
        return failed != null && failed > 0 ? ReviewJobStatus.FAILED.name() : "OWNERSHIP_LOST";
    }

    /**
     * Deliberately narrower than {@link SandboxException}, which also covers deterministic
     * validation/config failures and a catch-all wrap of an unknown defect: retrying either would burn
     * the retry budget on a failure that was never going to resolve itself.
     */
    static boolean isRetryableExecutionFailure(Exception e) {
        return e instanceof ReviewSourceNotReadyException
                || e instanceof SandboxInfrastructureException
                || e instanceof IOException;
    }

    /**
     * Which unreachable service this exit names, or null when the exit says something about the work.
     * The name is what the usage ledger records the attempt under.
     */
    private static @Nullable String unreachableReason(int exitCode) {
        if (exitCode == SandboxLayout.EXIT_SERVER_UNREACHABLE) {
            return "server-unreachable";
        }
        if (exitCode == SandboxLayout.EXIT_PROVIDER_UNREACHABLE) {
            return "provider-unreachable";
        }
        return null;
    }

    /**
     * Whether this job's results reached the server after all — the admission committed and only
     * its answer was lost. Repeating the review would submit a different payload against the digest the
     * job already carries, which the admission refuses, so an attempt that got this far is finished
     * even though its runner could not tell.
     */
    private static boolean practiceResultsAdmitted(ReviewJob job) {
        return ResultAdmissionService.isAdmitted(job);
    }

    /**
     * Hands the job back to the queue for another attempt, billing what this attempt already spent.
     * The usage read happens inside the same transaction and before the requeue, which zeroes the
     * accumulators — a later read would bill zero.
     *
     * @param bill whether provider work happened at all — nothing accrues before the sandbox starts
     * @return whether the job is queued again; false means the retry cap is spent or the fence is
     *     lost, and the caller owns the terminal outcome
     */
    private boolean requeueForAnotherAttempt(UUID jobId, ReviewJob job, String reason, boolean bill) {
        if (workerId == null) {
            return false;
        }
        int currentRetryCount = job.getAttempt();
        Integer updated = transactionTemplate.execute(status -> {
            // Under the same row lock the admission takes, so the two decisions serialize: a review
            // whose results reached the server is finished, whatever its sandbox went on to do.
            // Running it again would submit a different payload against the digest the job already
            // carries, which the admission refuses — a second attempt could only lose what the first
            // recorded.
            ReviewJob locked = jobRepository.lockById(jobId).orElse(null);
            if (locked != null && practiceResultsAdmitted(locked)) {
                log.info("Not requeuing job {}: its results already reached this server", jobId);
                return 0;
            }
            ReviewJobLlmUsage attempts =
                    bill ? jobRepository.findLlmUsageById(jobId).orElse(null) : null;
            int rows = requeueOrphanWithRotation(jobId, workerId, currentRetryCount);
            if (rows == 0) {
                // The fence is lost: this row belongs to another attempt now, and nothing this one has
                // to say about itself may be written over it.
                return 0;
            }
            if (bill) {
                billTerminatedJob(job, reason + " retry (attempt " + (currentRetryCount + 1) + ")", attempts);
            }
            return rows;
        });
        if (updated == null || updated == 0) {
            return false;
        }
        infraRetryRequeued.increment();
        return true;
    }

    /**
     * The current retry count sizes the backoff only — the UPDATE's own {@code attempt <
     *     max-retries} WHERE clause is the authoritative cap, so a stale read here cannot let a job
     *     requeue past it
     */
    private int requeueForComposition(ReviewCompositionContinuation continuing, ReviewJob job, String owningWorkerId) {
        int currentRetryCount = job.getAttempt();
        int attemptNumber = currentRetryCount + 1;
        Instant now = Instant.now();
        String newToken = ReviewJob.generateJobToken();
        return continuing.requeue(
                job,
                owningWorkerId,
                new ReviewJobRepository.CompositionRequeue(
                        currentRetryCount,
                        agentProperties.maxRetries(),
                        now,
                        now.plus(ReviewJobBackoff.compute(attemptNumber)),
                        newToken,
                        ReviewJob.computeTokenHash(newToken)));
    }

    private int requeueOrphanWithRotation(UUID jobId, String owningWorkerId, int currentRetryCount) {
        int attemptNumber = currentRetryCount + 1;
        Instant availableAt = Instant.now().plus(ReviewJobBackoff.compute(attemptNumber));
        String newToken = ReviewJob.generateJobToken();
        String newTokenHash = ReviewJob.computeTokenHash(newToken);
        return jobRepository.requeueOrphan(
                jobId, owningWorkerId, agentProperties.maxRetries(), availableAt, newTokenHash);
    }

    /** What one claim attempt produced: either a claimed job or the reason there is none. */
    private sealed interface ClaimAttempt {}

    private enum ClaimOutcome implements ClaimAttempt {
        ALREADY_CLAIMED,
        CONCURRENCY_FULL,
        BUDGET_HELD,
    }

    private record ClaimResult(ReviewJob job, ConfigSnapshot snapshot) implements ClaimAttempt {}

    private record TerminalClaim(ReviewJob job, ReviewJobStatus status) implements ClaimAttempt {}

    private @Nullable ClaimAttempt claimJob(UUID jobId) {
        return transactionTemplate.execute(status -> {
            Optional<ReviewJob> locked = jobRepository.findByIdQueuedForUpdateSkipLocked(jobId, Instant.now());
            if (locked.isEmpty()) {
                log.debug("Job already claimed or not QUEUED: jobId={}", jobId);
                return ClaimOutcome.ALREADY_CLAIMED;
            }

            ReviewJob job = locked.get();

            if (memberAiPolicy.isProcessingSuppressed(job)) {
                job.setStatus(ReviewJobStatus.CANCELLED);
                job.setCompletedAt(Instant.now());
                job.setErrorMessage("A personal-data erasure request blocks this job.");
                job.setCancellationReason(ReviewJobCancellationReason.PERSON_DATA_ERASED);
                jobRepository.save(job);
                recordPracticeReviewRefusal("person_data_erased");
                return new TerminalClaim(job, ReviewJobStatus.CANCELLED);
            }

            // Queued again after its results were admitted: a new attempt would capture the work
            // anew, and its citations would not be the ones the admission verified. Its earlier attempt
            // was billed when it was requeued, and nothing has run since. Only a writer continuing from
            // the admission's kept inputs is claimed, under every check below.
            if (ResultAdmissionService.isAdmitted(job) && !ResultAdmissionService.isContinuation(job)) {
                job.setStatus(ReviewJobStatus.FAILED);
                job.setCompletedAt(Instant.now());
                job.setErrorMessage(ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION);
                jobRepository.save(job);
                return new TerminalClaim(job, ReviewJobStatus.FAILED);
            }

            if (!memberAiPolicy.permitsReview(job)) {
                job.setStatus(ReviewJobStatus.CANCELLED);
                job.setCompletedAt(Instant.now());
                job.setErrorMessage("The developer has not enabled AI practice reviews in this workspace.");
                job.setCancellationReason(ReviewJobCancellationReason.MEMBER_AI_DECLINED);
                jobRepository.save(job);
                recordPracticeReviewRefusal("member_ai_declined");
                return new TerminalClaim(job, ReviewJobStatus.CANCELLED);
            }

            LlmBudgetBlockReason blockReason =
                    llmBudgetService.decide(job.getWorkspaceId()).forFunding(claimedFundingSource(job));
            if (blockReason != LlmBudgetBlockReason.NONE) {
                return holdOrCancelOverBudget(job, blockReason);
            }

            // Live-revalidate the catalog binding immediately before RUNNING. Submit-time behaviour
            // stays frozen; only availability/grants and the price are refreshed, and a changed binding
            // is refused rather than silently switching the queued job to another model.
            AgentPurpose purpose = AgentPurpose.PRACTICE_REVIEW;
            WorkspaceAgentBinding binding = memberAiPolicy.binding(job).orElse(null);
            if (binding == null) {
                return refuseUnavailableModel(job);
            }
            ConfigSnapshot snapshot;
            try {
                ConfigSnapshot submitted = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
                methods.require(assignments.load(job).getFirst().practice().method(), "review", binding);
                if (Objects.requireNonNullElse(submitted.dataHandlingTier(), DataHandlingTier.UNDECLARED)
                        != binding.getDataHandlingTier()) return refuseUnavailableModel(job);
                if (llmAdmissionService != null) {
                    var admitted = llmAdmissionService.admit(binding);
                    if (!submitted.model().isAdmittedAs(admitted)) {
                        return refuseUnavailableModel(job);
                    }
                    snapshot = submitted
                            .withPriceSnapshot(
                                    submitted.priceSnapshot() == null ? admitted.price() : submitted.priceSnapshot())
                            .withPracticeScript(
                                    admitPracticeScript(job, submitted.practiceScript(), llmAdmissionService));
                } else {
                    snapshot = submitted;
                }
            } catch (IllegalStateException | ReviewJobStateConflictException e) {
                return refuseUnavailableModel(job);
            }

            // Admission holds this slot's binding row lock until the RUNNING transition commits.
            {
                long runningCount = jobRepository.countRunningByWorkspaceIdAndDataHandlingTier(
                        job.getWorkspaceId(), binding.getDataHandlingTier());
                if (runningCount >= binding.getMaxConcurrentJobs()) {
                    concurrencyRejected.increment();
                    log.info(
                            "Concurrency limit reached: jobId={}, workspaceId={}, purpose={}, running={}, max={}",
                            jobId,
                            job.getWorkspaceId(),
                            purpose,
                            runningCount,
                            binding.getMaxConcurrentJobs());
                    return ClaimOutcome.CONCURRENCY_FULL;
                }
            }

            Instant claimedAt = Instant.now();
            // A job held for a future availableAt has no queue latency to record — it was waiting on
            // the clock, not on a worker.
            if (!job.getAvailableAt().isAfter(claimedAt)) {
                claimLatency.record(Duration.between(job.getAvailableAt(), claimedAt));
            }
            job.setStatus(ReviewJobStatus.RUNNING);
            job.setStartedAt(claimedAt);
            job.setExecutionStartedAt(null);
            // The hold is over — clear its marker so it never outlives the hold it describes. A stale
            // 'BUDGET' marker would survive onto a later crash-retry requeue, and a cap raise would
            // then fast-forward a backoff that has nothing to do with the budget.
            job.setHoldReason(null);
            job.setWorkerId(workerId); // owner for cancel routing, orphan recovery, terminal-write fencing
            job.setConfigSnapshot(snapshot.toJson(objectMapper));
            jobRepository.save(job);

            return new ClaimResult(job, snapshot);
        });
    }

    /**
     * Re-admits each model for practice scripts frozen at submit and freezes its price, as for the review's own
     * model. A model that no longer resolves to the same catalog row, or that has no usable price, drops
     * out of this job. The review still runs without it.
     */
    private @Nullable Map<ModelKind, FrozenModel> admitPracticeScript(
            ReviewJob job, @Nullable Map<ModelKind, FrozenModel> submitted, LlmAdmissionService admission) {
        if (submitted == null) {
            return null;
        }
        Map<ModelKind, FrozenModel> admitted = new EnumMap<>(ModelKind.class);
        for (AgentPurpose purpose : AgentPurpose.practiceScript()) {
            FrozenModel slot = submitted.get(purpose.kind());
            if (slot == null) {
                continue;
            }
            DataHandlingTier tier = Objects.requireNonNullElse(slot.dataHandlingTier(), DataHandlingTier.UNDECLARED);
            Optional<FrozenModel> priced = memberAiPolicy
                    .practiceScriptBinding(job, purpose)
                    .filter(binding -> binding.getDataHandlingTier() == tier)
                    .map(binding -> {
                        methods.require(
                                assignments.load(job).getFirst().practice().method(),
                                purpose.kind().name().toLowerCase(Locale.ROOT),
                                binding);
                        return binding;
                    })
                    .flatMap(admission::admitIfAvailable)
                    .filter(slot::isAdmittedAs)
                    .map(model -> slot.withPriceSnapshot(
                            slot.priceSnapshot() == null ? model.price() : slot.priceSnapshot()));
            if (priced.isPresent()) {
                admitted.put(purpose.kind(), priced.get());
            } else {
                throw new IllegalStateException("A frozen direct model is no longer available");
            }
        }
        return admitted.isEmpty() ? null : admitted;
    }

    /**
     * The budget is re-checked at claim even though submit already gated, because a workspace can
     * pre-queue jobs faster than the cap updates. It is never re-checked past this point — there is no
     * mid-execution kill on budget alone.
     *
     * <p>Held rather than cancelled: a month rollover or a raised cap clears the block without the job
     * having failed, so {@code attempt} stays untouched. The age bound is what keeps that from
     * being unbounded when neither ever happens.
     */
    private ClaimAttempt holdOrCancelOverBudget(ReviewJob job, LlmBudgetBlockReason blockReason) {
        Instant now = Instant.now();
        boolean tooOldToHold = job.getAvailableAt() != null
                && Duration.between(job.getAvailableAt(), now).compareTo(BUDGET_HOLD_MAX_JOB_AGE) > 0;
        if (tooOldToHold) {
            job.setStatus(ReviewJobStatus.CANCELLED);
            job.setCompletedAt(now);
            job.setErrorMessage("Cancelled. The monthly AI budget is exceeded, and this job is more than "
                    + BUDGET_HOLD_MAX_JOB_AGE.toDays()
                    + " days old.");
            job.setCancellationReason(ReviewJobCancellationReason.BUDGET_EXHAUSTED);
            jobRepository.save(job);
            log.info(
                    "Cancelling claim — over budget and older than {} days: jobId={}, workspaceId={}, blockReason={}",
                    BUDGET_HOLD_MAX_JOB_AGE.toDays(),
                    job.getId(),
                    job.getWorkspaceId(),
                    blockReason);
            recordPracticeReviewRefusal("budget_exhausted");
            meterRegistry.counter(AgentMetrics.AGENT_JOB_BUDGET_REFUSED).increment();
            return new TerminalClaim(job, ReviewJobStatus.CANCELLED);
        }
        job.setAvailableAt(now.plus(BUDGET_HOLD_INTERVAL));
        // Marks this as a budget hold specifically, so raising the cap can release it at once instead
        // of leaving the admin to wait out BUDGET_HOLD_INTERVAL.
        job.setHoldReason(ReviewJob.HOLD_REASON_BUDGET);
        jobRepository.save(job);
        log.info(
                "Holding claim — monthly LLM budget {}: jobId={}, workspaceId={}, retryAt={}",
                blockReason == LlmBudgetBlockReason.EXHAUSTED ? "exhausted" : "unverifiable (cap set)",
                job.getId(),
                job.getWorkspaceId(),
                job.getAvailableAt());
        meterRegistry.counter(AgentMetrics.AGENT_JOB_BUDGET_HELD).increment();
        return ClaimOutcome.BUDGET_HELD;
    }

    private ClaimAttempt refuseUnavailableModel(ReviewJob job) {
        String message = "The configured model is not available.";
        job.setStatus(ReviewJobStatus.CANCELLED);
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(message);
        job.setCancellationReason(ReviewJobCancellationReason.MODEL_UNAVAILABLE);
        jobRepository.save(job);
        meterRegistry.counter(AgentMetrics.AGENT_JOB_MODEL_REFUSED).increment();
        recordPracticeReviewRefusal("model_unavailable");
        log.info("Refusing claim — configured model unavailable: jobId={}", job.getId());
        return new TerminalClaim(job, ReviewJobStatus.CANCELLED);
    }

    private void recordPracticeReviewRefusal(String reason) {
        practiceReviewRefusalMetrics.recordExecutionRefusal(reason);
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
    private int transitionTerminal(UUID jobId, ReviewJobStatus status, Instant now, @Nullable String error) {
        return workerId != null
                ? jobRepository.transitionStatusOwnedBy(
                        jobId, status, now, error, Set.of(ReviewJobStatus.RUNNING), workerId)
                : jobRepository.transitionStatus(jobId, status, now, error, Set.of(ReviewJobStatus.RUNNING));
    }

    private @Nullable ReviewJobStatus completeJob(UUID jobId, AgentResult agentResult, SandboxResult sandboxResult) {
        // A lost ownership fence must not deliver another attempt's result.
        ReviewJobStatus terminalStatus = persistTerminalState(jobId, agentResult, sandboxResult);
        if (terminalStatus != null) {
            return terminalStatus;
        } else {
            log.info("Skipping delivery: job no longer owned/RUNNING (requeued or cancelled): jobId={}", jobId);
            return null;
        }
    }

    /**
     * A failed runner can leave a local draft. Only results already admitted by this server
     * allow its remaining output to continue through delivery.
     */
    private ReviewJobStatus determineTerminalStatus(
            SandboxResult sandboxResult, AgentResult agentResult, ReviewJob job) {
        if (sandboxResult.timedOut()) {
            return ReviewJobStatus.TIMED_OUT;
        }
        if (sandboxResult.exitCode() == 0) {
            // A clean exit without a valid result is the runner's failure, not a completed review.
            return agentResult != null && agentResult.success() ? ReviewJobStatus.COMPLETED : ReviewJobStatus.FAILED;
        }
        // Distinguish envelope drift (exit 42) from generic failure — the runner emits this when
        // the task.json schemaVersion doesn't match this image. Operators need to see
        // this distinctly from agent crashes; the secondary metric also alerts on image drift.
        if (sandboxResult.exitCode() == SandboxLayout.EXIT_ENVELOPE_MISMATCH) {
            log.error(
                    "Pi runner rejected task envelope (exit {}) — server/image schemaVersion drift. "
                            + "Rebuild the agent-pi image or roll back the server.",
                    SandboxLayout.EXIT_ENVELOPE_MISMATCH);
            meterRegistry.counter(AgentMetrics.AGENT_PI_ENVELOPE_MISMATCH).increment();
            return ReviewJobStatus.FAILED;
        }
        if (agentResult != null && agentResult.output() != null) {
            Object rawOutput = agentResult.output().get("rawOutput");
            if (rawOutput instanceof String raw && !raw.isBlank() && practiceResultsAdmitted(job)) {
                log.info(
                        "Agent exited with code {} after result admission — continuing delivery",
                        sandboxResult.exitCode());
                return ReviewJobStatus.COMPLETED;
            }
            if (rawOutput != null && !(rawOutput instanceof String)) {
                log.warn(
                        "Agent exited with code {} and rawOutput is present but not a String (type={})",
                        sandboxResult.exitCode(),
                        rawOutput.getClass().getSimpleName());
            }
        }
        return ReviewJobStatus.FAILED;
    }

    /**
     * The accounting write for provider work that has ALREADY happened, re-attempted while the failure
     * is one the database may recover from.
     *
     * <p>Giving up deliberately leaves the row RUNNING: the provider work is already done and paid for,
     * so re-running would charge for it twice. {@link ReviewJobZombieSweeper} terminalises the row
     * instead and books the proxy-recorded spend.
     *
     * @return the committed terminal status, or {@code null} when ownership was lost and the caller
     *     must not deliver.
     */
    private @Nullable ReviewJobStatus persistTerminalState(
            UUID jobId, AgentResult agentResult, SandboxResult sandboxResult) {
        JsonNode output = objectMapper.valueToTree(agentResult.output());
        boolean storable = textRepresentableInJsonb(output);
        if (!storable) {
            log.warn("Runner result has text jsonb cannot represent; recording it as not stored: jobId={}", jobId);
        }
        try {
            return terminalPersistRetries.execute(named(
                    "Terminal persistence for jobId=" + jobId,
                    () -> persistTerminalStateOnce(jobId, agentResult, sandboxResult, output, storable)));
        } catch (RetryException e) {
            throw new TerminalPersistenceException(e);
        }
    }

    private @Nullable ReviewJobStatus persistTerminalStateOnce(
            UUID jobId, AgentResult agentResult, SandboxResult sandboxResult, JsonNode output, boolean storable) {
        return transactionTemplate.execute(status -> {
            ReviewJob locked = jobRepository.lockById(jobId).orElse(null);
            if (locked == null) return null;
            ReviewJobStatus determined = determineTerminalStatus(sandboxResult, agentResult, locked);
            ReviewJobStatus terminalStatus =
                    !storable && determined == ReviewJobStatus.COMPLETED ? ReviewJobStatus.FAILED : determined;
            String failureMessage = sandboxResult.exitCode() == 0
                    ? "The runner did not return a valid result."
                    : "The container exited with code " + sandboxResult.exitCode() + ".";
            String errorMessage =
                    switch (terminalStatus) {
                        case TIMED_OUT -> "The container timed out.";
                        case FAILED -> storable ? failureMessage : UNSTORABLE_RESULT_MESSAGE;
                        case COMPLETED -> null;
                        default -> null;
                    };
            Instant finishedAt = Instant.now();
            int updated = transitionTerminal(jobId, terminalStatus, finishedAt, errorMessage);
            if (updated == 0) {
                log.info("Job no longer owned/RUNNING, skipping output persist: jobId={}", jobId);
                return null;
            }

            ReviewJob freshJob = jobRepository.findById(jobId).orElseThrow();
            // Read the proxy's committed per-call accumulations BEFORE the runner-reported totals are
            // written over them below — they are the fallback when the runner reported nothing.
            ReviewJobLlmUsage proxyCounts =
                    jobRepository.findLlmUsageById(jobId).orElse(null);
            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(freshJob.getConfigSnapshot(), objectMapper);
            LlmPriceSnapshot price = admittedPrice(snapshot);

            // The whole result or none of it: a claim stripped of the text it rested on would be a different
            // claim than the runner made.
            freshJob.setOutput(
                    storable ? output : objectMapper.createObjectNode().put("resultNotStored", UNSTORABLE_TEXT));
            freshJob.setExitCode(sandboxResult.exitCode());

            if (terminalStatus == ReviewJobStatus.COMPLETED) {
                freshJob.setProcessingStatus(ProcessingStatus.PENDING);
            }

            var runnerUsage = agentResult.usage();
            // TerminalUsage retains one coherent observed token vector, including a proxy-only fallback
            // when the runner never wrote usage.json.
            TerminalUsage usage = TerminalUsage.resolve(
                    runnerUsage,
                    proxyCounts,
                    jobRepository.sumPracticeScriptUsageByKind(
                            freshJob.getWorkspaceId(), jobId, freshJob.getAttempt()));

            // Use the ledger totals: the runner may omit usage details counted by the proxy.
            if (runnerUsage != null && runnerUsage.totalCalls() > 0) {
                freshJob.setLlmCalls(usage.totalCalls());
                freshJob.setLlmInputTokens(clampToInt(usage.inputTokens()));
                freshJob.setLlmOutputTokens(clampToInt(usage.outputTokens()));
                freshJob.setLlmReasoningTokens(clampToInt(usage.reasoningTokens()));
                freshJob.setLlmCacheReadTokens(clampToInt(usage.cacheReadTokens()));
                freshJob.setLlmCacheWriteTokens(clampToInt(usage.cacheWriteTokens()));
            }
            // Provider output is telemetry only. The admitted snapshot is authoritative identity.

            jobRepository.saveAndFlush(freshJob);

            usage.appendTo(usageRecorder, jobRepository, freshJob.getWorkspaceId(), freshJob, snapshot, price);
            practiceScriptRuns.record(
                    freshJob,
                    sandboxResult.outputFiles().get(SandboxLayout.PRACTICE_SCRIPT_REPORT_FILE),
                    snapshot.practiceScriptModels(),
                    finishedAt);
            return terminalStatus;
        });
    }

    static final String UNSTORABLE_RESULT_MESSAGE =
            "The runner returned a result with text the database cannot store, so the result was not kept.";
    private static final String UNSTORABLE_TEXT = "The text contains a NUL character or an unpaired surrogate.";

    /**
     * Whether every string value and object key of this tree is text jsonb can represent: no decoded NUL, and
     * well-formed UTF-16. The six characters of an escape written as text are ordinary text and pass. Numbers and
     * other storage limits are not judged here.
     */
    private static boolean textRepresentableInJsonb(JsonNode node) {
        if (node.isString()) return StorableText.isStorable(node.asString());
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                if (!StorableText.isStorable(property.getKey()) || !textRepresentableInJsonb(property.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (!textRepresentableInJsonb(element)) return false;
            }
        }
        return true;
    }

    private static final class TerminalPersistenceException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private TerminalPersistenceException(Throwable cause) {
            super("Could not durably persist terminal job result and usage", cause);
        }
    }

    /**
     * A job whose purpose or binding is gone yields {@code null}, which
     * {@link LlmBudgetDecision#forFunding} judges against BOTH caps: an unattributable job must not be
     * a way around either one.
     */
    private @Nullable UsageFundingSource claimedFundingSource(ReviewJob job) {
        AgentPurpose purpose = AgentPurpose.PRACTICE_REVIEW;
        if (purpose == null) {
            return null;
        }
        return memberAiPolicy
                .binding(job)
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
     * A missing price is billed UNPRICED, matching {@link ReviewJobZombieSweeper}'s reaper.
     */
    private LlmPriceSnapshot terminalPriceOrUnpriced(ConfigSnapshot snapshot) {
        LlmPriceSnapshot price = snapshot.priceSnapshot();
        return price != null ? price : LlmPriceSnapshot.unpricedInstance();
    }

    /** Runs outside any transaction — it calls external APIs. */
    private static String truncateErrorMessage(@Nullable String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "... [truncated]"
                : message;
    }

    private static int clampToInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }
}
