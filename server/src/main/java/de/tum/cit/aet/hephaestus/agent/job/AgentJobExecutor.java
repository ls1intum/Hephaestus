package de.tum.cit.aet.hephaestus.agent.job;

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
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUnpricedUsageBlockedException;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.FetchConsumeOptions;
import io.nats.client.FetchConsumer;
import io.nats.client.Message;
import io.nats.client.StreamContext;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * NATS pull consumer that executes agent jobs through the full pipeline.
 *
 * <p>Uses a pull-based consumer pattern to avoid the push+NAK anti-pattern. The pull loop
 * fetches up to {@code maxAckPending} messages and dispatches each to the {@code sandboxExecutor}
 * (bounded platform thread pool). When the pool is full, we simply don't fetch more messages —
 * NATS holds them until we ask.
 *
 * <p>Transaction boundaries are deliberately narrow:
 * <ul>
 *   <li><b>Claim</b> (~5ms): SKIP LOCKED → set RUNNING → commit</li>
 *   <li><b>Execute</b> (minutes): No transaction, no DB connection held</li>
 *   <li><b>Complete</b> (~5ms): Set terminal status → commit</li>
 * </ul>
 */
@Component
// Wires when agent NATS is enabled AND the worker role isn't explicitly disabled. Combined into
// @ConditionalOnExpression because Spring honors only ONE @ConditionalOnProperty per element.
@ConditionalOnExpression("${hephaestus.agent.nats.enabled:false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}")
@WorkspaceAgnostic("NATS consumer processes jobs across all workspaces")
public class AgentJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentJobExecutor.class);

    private static final String MDC_JOB_ID = "agent.jobId";
    private static final String MDC_JOB_TYPE = "agent.jobType";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONTAINER_LOGS_CHARS = 65536; // 64KB

    private final Connection natsConnection;
    private final AgentNatsProperties natsProperties;
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
    private final ScheduledExecutorService heartbeatScheduler;

    private final Timer executionDuration;
    private final Counter concurrencyRejected;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread pullThread;
    /**
     * Tracks in-flight {@link #executeJob(Message)} submissions for the drain coordinator's
     * {@link #awaitInFlight(Duration)} call. Phaser is the right primitive here: register on
     * submit, arriveAndDeregister on completion, await-advance to wait for all parties to
     * deregister. Survives thread restarts and exception paths via the try-finally.
     */
    private final Phaser inFlight = new Phaser(1); // 1 = the executor itself; deregistered on stop
    /**
     * Job ids this worker is currently executing. Source of truth for job-scoped cancellation
     * (#1138): drain and hub-initiated cancels act only on jobs in this set, never on sibling
     * workers' jobs. Populated on claim, removed in the {@code executeJob} finally block.
     */
    private final Set<UUID> localRunningJobs = ConcurrentHashMap.newKeySet();
    private final Optional<WorkerCapacityState> capacityState;
    private final Optional<WorkerProperties> workerProperties;
    /** This worker's identity (null only when the worker role is off); stamped on claimed jobs to fence terminal writes. */
    private final String workerId;

    public AgentJobExecutor(
        @Qualifier("agentNatsConnection") Connection natsConnection,
        AgentNatsProperties natsProperties,
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
        Optional<WorkerCapacityState> capacityState,
        Optional<WorkerProperties> workerProperties
    ) {
        this.natsConnection = natsConnection;
        this.natsProperties = natsProperties;
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
        this.capacityState = capacityState;
        this.workerProperties = workerProperties;
        this.workerId = workerProperties.map(WorkerProperties::resolvedWorkerId).orElse(null);

        // Internal scheduler for NATS InProgress heartbeats — not a @Bean to avoid
        // blocking Spring's TaskScheduler auto-configuration (ConditionalOnMissingBean).
        // Single thread suffices: heartbeat tasks are sub-microsecond fire-and-forget calls.
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-heartbeat");
            t.setDaemon(true);
            return t;
        });

        this.executionDuration = Timer.builder("agent.job.execution.duration")
            .description("Total duration of agent job execution")
            .register(meterRegistry);
        this.concurrencyRejected = Counter.builder("agent.job.concurrency.rejected")
            .description("Jobs rejected due to concurrency limits")
            .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // Must run after AgentNatsConsumerConfig.ensureStreamAndConsumer() which uses @Order(1)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        pullThread = Thread.ofPlatform().name("agent-nats-pull").daemon(true).start(this::pullLoop);

        log.info(
            "Agent job executor started: consumer={}, workerId={}, maxAckPending={}",
            natsProperties.consumerName(),
            workerId,
            natsProperties.maxAckPending()
        );
    }

    /**
     * Stop the pull loop so no new jobs are claimed. Idempotent. Composable with
     * {@link #awaitInFlight(Duration)} and {@link #cancelInFlight(AgentJobCancellationReason)}
     * from the worker drain coordinator; called standalone from {@link #stop()} for non-worker
     * monolith mode.
     */
    public void stopAcceptingNewJobs() {
        running.set(false);
        if (pullThread != null) {
            pullThread.interrupt();
        }
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait up to {@code timeout} for in-flight {@link #executeJob(Message)} submissions to
     * complete. Must be called after {@link #stopAcceptingNewJobs()} so no new parties register.
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
     * Transition the jobs THIS worker is currently running to {@link AgentJobStatus#CANCELLED}
     * with the given reason, and stop their containers promptly. Scoped to {@link #localRunningJobs}
     * so sibling workers' jobs are untouched — this is the fix for #1138 that makes running more
     * than one worker replica safe.
     */
    public void cancelInFlight(AgentJobCancellationReason reason) {
        Set<UUID> snapshot = Set.copyOf(localRunningJobs);
        if (snapshot.isEmpty()) {
            return;
        }
        log.info("Cancelling {} in-flight job(s) owned by this worker with reason {}", snapshot.size(), reason);
        Instant now = Instant.now();
        String error = "worker draining";
        for (UUID jobId : snapshot) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    jobRepository.transitionToCancelled(jobId, now, error, reason, Set.of(AgentJobStatus.RUNNING))
                );
                // Stop the container so drain doesn't wait for the agent to finish naturally.
                sandboxManager.cancel(jobId);
            } catch (Exception e) {
                log.warn("Failed to cancel in-flight job {}: {}", jobId, e.getClass().getSimpleName());
            }
            // #1368 fix wave: every job in localRunningJobs is, by construction, one this worker has
            // claimed and started executing — so it may carry real, un-costed spend regardless of
            // whether the CAS above won (a concurrent user-cancel or the executor's own
            // handleCancellation may already have moved it). Attempt the ledger write unconditionally;
            // duplicates are swallowed by LlmUsageRecorder#persist's unique source_id constraint.
            recordUnverifiableUsage(jobId, "worker draining");
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
        // Any survivors will be NAK'd on connection close.
    }

    // Pull loop

    private void pullLoop() {
        while (running.get()) {
            try {
                StreamContext streamContext = natsConnection.getStreamContext(natsProperties.streamName());
                ConsumerContext consumerContext = streamContext.getConsumerContext(natsProperties.consumerName());

                // Pull only a worker-local batch (not the cluster-wide maxAckPending) so a single
                // replica doesn't claim the whole unacked budget and starve siblings (#1138). The
                // pool-full path NAKs-with-delay, returning surplus to other replicas.
                FetchConsumeOptions fetchOptions = FetchConsumeOptions.builder()
                    .maxMessages(natsProperties.fetchBatchSize())
                    .expiresIn(Duration.ofSeconds(30).toMillis())
                    .build();

                try (FetchConsumer consumer = consumerContext.fetch(fetchOptions)) {
                    Message msg;
                    while (running.get() && (msg = consumer.nextMessage()) != null) {
                        Message finalMsg = msg;
                        try {
                            inFlight.register();
                            sandboxExecutor.execute(() -> {
                                try {
                                    executeJob(finalMsg);
                                } finally {
                                    inFlight.arriveAndDeregister();
                                }
                            });
                        } catch (RejectedExecutionException e) {
                            inFlight.arriveAndDeregister();
                            // Pool is full — NAK with delay to avoid tight redelivery loop
                            finalMsg.nakWithDelay(Duration.ofSeconds(10));
                            log.debug("Sandbox executor full, NAK'd with 10s delay for redelivery");
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("Pull loop error, retrying in 5s: {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("Agent job executor pull loop stopped");
    }

    // Job execution

    void executeJob(Message msg) {
        Optional<UUID> parsed = parseJobId(msg);
        if (parsed.isEmpty()) {
            msg.ack();
            return;
        }

        UUID jobId = parsed.get();
        MDC.put(MDC_JOB_ID, jobId.toString());
        boolean claimed = false;
        try {
            claimed = claimAndExecute(jobId, msg);
        } catch (SandboxCancelledException e) {
            claimed = true;
            handleCancellation(jobId, msg);
        } catch (CannotAcquireLockException e) {
            msg.nakWithDelay(Duration.ofSeconds(5));
            log.debug("Lock timeout during claim for job {}, NAK'd with 5s delay", jobId);
        } catch (Exception e) {
            claimed = true;
            handleExecutionFailure(jobId, msg, e);
        } finally {
            if (claimed) {
                releaseCapacity();
            }
            localRunningJobs.remove(jobId);
            MDC.remove(MDC_JOB_ID);
            MDC.remove(MDC_JOB_TYPE);
        }
    }

    /**
     * Extract and validate a job UUID from the NATS message payload.
     *
     * @return the parsed UUID, or empty if the payload is invalid
     */
    private Optional<UUID> parseJobId(Message msg) {
        try {
            UUID jobId = UUID.fromString(new String(msg.getData(), StandardCharsets.UTF_8).trim());
            return Optional.of(jobId);
        } catch (Exception e) {
            log.warn("Invalid NATS message payload, acking to discard: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Claim the job, set up heartbeat, prepare sandbox inputs, execute, and complete.
     * Propagates exceptions to {@link #executeJob(Message)} for centralized error handling.
     *
     * @return {@code true} if the job was claimed (so the caller should release capacity);
     *     {@code false} otherwise.
     */
    private boolean claimAndExecute(UUID jobId, Message msg) {
        // CLAIM (micro-transaction #1) — may throw CannotAcquireLockException
        Optional<ClaimResult> claimed;
        try {
            claimed = dispatchClaimResult(jobId, msg, claimJob(jobId));
        } catch (CannotAcquireLockException | SandboxCancelledException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaimFailedException(e);
        }
        if (claimed.isEmpty()) {
            return false; // Already ack'd / nak'd / left for redelivery
        }

        ClaimResult claim = claimed.get();
        AgentJob job = claim.job;
        ConfigSnapshot snapshot = claim.snapshot;
        MDC.put(MDC_JOB_TYPE, job.getJobType().name());
        log.info("Executing agent job: jobId={}, jobType={}", jobId, job.getJobType());

        Instant startTime = Instant.now();
        ScheduledFuture<?> heartbeat = startHeartbeat(msg);
        try {
            // PREPARE + EXECUTE + COMPLETE
            SandboxResult result = prepareAndExecute(jobId, job, snapshot);
            AgentResult agentResult = practiceAgent.parseResult(result);

            JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());
            completeJob(jobId, agentResult, result, handler, job);
            msg.ack();

            Duration duration = Duration.between(startTime, Instant.now());
            executionDuration.record(duration);
            log.info("Agent job completed: jobId={}, duration={}", jobId, duration);
            return true;
        } finally {
            heartbeat.cancel(false);
        }
    }

    /**
     * Dispatch the claim result: ack/nak the message for non-success outcomes,
     * or return the {@link ClaimResult} for the caller to proceed with execution.
     */
    private Optional<ClaimResult> dispatchClaimResult(UUID jobId, Message msg, Object claimResult) {
        if (claimResult == ClaimOutcome.ALREADY_CLAIMED || claimResult == ClaimOutcome.BUDGET_BLOCKED) {
            // BUDGET_BLOCKED already transitioned the job to a terminal state inside the claim
            // transaction (see claimJob) — ack so NATS never redelivers a job that is already done.
            msg.ack();
            return Optional.empty();
        }
        if (claimResult == ClaimOutcome.CONCURRENCY_FULL) {
            msg.nakWithDelay(Duration.ofSeconds(30));
            return Optional.empty();
        }
        if (claimResult instanceof ClaimResult claim) {
            return Optional.of(claim);
        }
        log.warn("Unexpected claim result for job {}, leaving for redelivery", jobId);
        return Optional.empty();
    }

    /** Schedule periodic NATS InProgress heartbeats for the given message. */
    private ScheduledFuture<?> startHeartbeat(Message msg) {
        return heartbeatScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    msg.inProgress();
                } catch (Exception e) {
                    log.debug("InProgress heartbeat failed: {}", e.getMessage());
                }
            },
            0,
            natsProperties.heartbeatInterval().toSeconds(),
            TimeUnit.SECONDS
        );
    }

    /**
     * Prepare sandbox inputs (handler files + adapter spec) and execute in sandbox.
     *
     * @return the sandbox execution result
     */
    private SandboxResult prepareAndExecute(UUID jobId, AgentJob job, ConfigSnapshot snapshot) {
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
            snapshot.cacheControlFormat(),
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
        return sandboxManager.execute(sandboxSpec);
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

    /** Wrapper that signals the claim phase failed — the job is still QUEUED. */
    private static class ClaimFailedException extends RuntimeException {

        ClaimFailedException(Exception cause) {
            super(cause);
        }
    }

    /** Handle a job cancelled during sandbox execution. */
    private void handleCancellation(UUID jobId, Message msg) {
        transactionTemplate.executeWithoutResult(status ->
            transitionTerminal(jobId, AgentJobStatus.CANCELLED, Instant.now(), "Cancelled during execution")
        );
        msg.ack();
        log.info("Agent job cancelled: jobId={}", jobId);
        // #1368 fix wave: reaching this handler at all means the job HAD started executing (claimed,
        // RUNNING) — SandboxCancelledException is only thrown from mid-execution, never from the claim
        // phase — so there may be real, un-costed spend behind it. Record it as UNPRICED (outside the
        // transition transaction, matching LlmUsageRecorder's after-commit contract) unconditionally,
        // regardless of whether OUR transition above actually won the CAS: a concurrent user-cancel
        // (AgentJobLifecycleService.cancel) or worker-drain (cancelInFlight) may have already moved the
        // job to CANCELLED first, and previously that made this write silently never happen. The
        // ledger's unique source_id constraint makes a duplicate attempt safe (first write wins,
        // swallowed by LlmUsageRecorder#persist) — so recording unconditionally here is strictly safer
        // than gating on a race this handler cannot reliably observe.
        recordUnverifiableUsage(jobId, "cancelled during execution");
    }

    /**
     * Append an UNPRICED ledger row for a job that started executing (claimed, RUNNING) but ended
     * with no verifiable usage — see {@link LlmUsageRecorder#recordUnverifiable}. Best-effort:
     * re-reads the job row for workspace + frozen catalog-binding provenance; a lookup miss just
     * skips the ledger write (the job's terminal status is unaffected either way).
     */
    private void recordUnverifiableUsage(UUID jobId, String reason) {
        jobRepository
            .findByIdWithWorkspace(jobId)
            .ifPresentOrElse(
                job -> {
                    ConfigSnapshot snap = parseSnapshotQuietly(job);
                    LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
                        LlmUsageJobType.from(job.getJobType()),
                        job.getId(),
                        snap != null ? snap.upstreamModelId() : null,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0,
                        snap != null ? snap.connectionScope() : null,
                        snap != null ? snap.connectionId() : null,
                        Instant.now()
                    );
                    usageRecorder.recordUnverifiable(job.getWorkspace().getId(), sample);
                    log.info("Recorded UNPRICED usage ledger entry ({}): jobId={}", reason, jobId);
                },
                () -> log.warn("Could not record unverifiable usage — job row missing: jobId={}", jobId)
            );
    }

    /** Best-effort {@link ConfigSnapshot} parse; a malformed/missing snapshot just yields no provenance. */
    private @Nullable ConfigSnapshot parseSnapshotQuietly(AgentJob job) {
        var snapshotNode = job.getConfigSnapshot();
        if (snapshotNode == null) {
            return null;
        }
        try {
            return ConfigSnapshot.fromJson(snapshotNode, objectMapper);
        } catch (Exception e) {
            log.warn("Could not deserialise config snapshot for usage ledger provenance: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Handle generic execution failures. If the job was never claimed (still QUEUED),
     * the message is left for NATS redelivery. Otherwise the job is transitioned to FAILED.
     */
    private void handleExecutionFailure(UUID jobId, Message msg, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        if (e instanceof ClaimFailedException) {
            // Claim failed (DB timeout, connection pool exhaustion) — don't ack,
            // let NATS redeliver. Job is still QUEUED.
            log.warn("Claim failed for job {}, will be redelivered: {}", jobId, e.getCause().getMessage());
            return;
        }

        String errorMessage = truncateErrorMessage(e.getMessage());
        log.error("Agent job failed: jobId={}, error={}", jobId, errorMessage, e);

        transactionTemplate.executeWithoutResult(status ->
            transitionTerminal(jobId, AgentJobStatus.FAILED, Instant.now(), errorMessage)
        );
        msg.ack();
    }

    // Claim: micro-transaction #1

    /** Sentinel values for claimJob results that require post-transaction NATS actions. */
    private enum ClaimOutcome {
        ALREADY_CLAIMED,
        CONCURRENCY_FULL,
        BUDGET_BLOCKED,
    }

    private record ClaimResult(AgentJob job, ConfigSnapshot snapshot) {}

    /**
     * Attempt to claim a job within a micro-transaction. Returns:
     * - {@link ClaimResult} on success
     * - {@link ClaimOutcome#ALREADY_CLAIMED} if job is not QUEUED (caller should ACK)
     * - {@link ClaimOutcome#CONCURRENCY_FULL} if concurrency limit reached (caller should NAK with delay)
     * - {@link ClaimOutcome#BUDGET_BLOCKED} if the workspace's LLM budget gate refused the job (caller should ACK)
     * - {@code null} if transaction returned null unexpectedly
     */
    private Object claimJob(UUID jobId) {
        return transactionTemplate.execute(status -> {
            // SKIP LOCKED: if another executor has this row locked, returns empty
            Optional<AgentJob> locked = jobRepository.findByIdQueuedForUpdateSkipLocked(jobId);
            if (locked.isEmpty()) {
                log.debug("Job already claimed or not QUEUED: jobId={}", jobId);
                return ClaimOutcome.ALREADY_CLAIMED;
            }

            AgentJob job = locked.get();

            // Claim-time budget recheck (#1368 fix wave): AgentJobService.submit already gated
            // submission, but a workspace can pre-queue jobs faster than the cap updates — every
            // job queued before the cap was crossed would otherwise still run. Recheck here, right
            // before the job would start, and refuse it terminally rather than let it execute.
            // Never re-checked once a job is past this point (no mid-execution kill on budget alone).
            LlmBudgetBlockReason blockReason = llmBudgetService.blockReason(job.getWorkspace().getId());
            if (blockReason != LlmBudgetBlockReason.NONE) {
                String message =
                    blockReason == LlmBudgetBlockReason.EXHAUSTED
                        ? "Budget reached."
                        : LlmUnpricedUsageBlockedException.MESSAGE;
                job.setStatus(AgentJobStatus.CANCELLED);
                job.setCompletedAt(Instant.now());
                job.setErrorMessage(message);
                job.setCancellationReason(AgentJobCancellationReason.BUDGET_EXHAUSTED);
                jobRepository.save(job);
                log.info(
                    "Refusing claim — {}: jobId={}, workspaceId={}, blockReason={}",
                    message,
                    jobId,
                    job.getWorkspace().getId(),
                    blockReason
                );
                meterRegistry.counter("agent.job.budget.refused").increment();
                return ClaimOutcome.BUDGET_BLOCKED;
            }

            // Concurrency gate: lock config row, check running count
            if (job.getConfig() != null) {
                Optional<AgentConfig> configOpt = configRepository.findByIdForUpdate(job.getConfig().getId());
                if (configOpt.isPresent()) {
                    AgentConfig config = configOpt.get();
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
            }

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
            job.setStatus(AgentJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setWorkerId(workerId); // owner for cancel routing, orphan recovery, and terminal-write fencing (#1138)
            jobRepository.save(job);

            // Track locally so drain / hub-initiated cancels target only this worker's jobs.
            localRunningJobs.add(jobId);
            capacityState.ifPresent(WorkerCapacityState::claimReview);
            return new ClaimResult(job, snapshot);
        });
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

    private void completeJob(
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

        // Captured inside the terminal-write transaction, appended to the LLM usage ledger only
        // after it commits — so a lost fence race (updated == 0) never bills, and the recorder's
        // own REQUIRES_NEW transaction doesn't hold a second pool connection under this one.
        AtomicReference<Long> ledgerWorkspaceId = new AtomicReference<>();
        AtomicReference<LlmUsageRecorder.LlmUsageSample> ledgerSample = new AtomicReference<>();
        // #1368 fix wave: true when ledgerSample must be written via recordUnverifiable (forced
        // UNPRICED) rather than record (normal catalog price resolution) — see the else-branch below.
        AtomicBoolean ledgerUnverifiable = new AtomicBoolean(false);

        Boolean persisted = transactionTemplate.execute(status -> {
            int updated = transitionTerminal(jobId, terminalStatus, Instant.now(), errorMessage);

            if (updated == 0) {
                // No longer RUNNING-and-ours: cancelled during execution, or orphan-requeued to a
                // sibling (fence). Skip output persist so we don't clobber the new owner's run.
                log.info("Job no longer owned/RUNNING, skipping output persist: jobId={}", jobId);
                return false;
            }

            AgentJob freshJob = jobRepository.findById(jobId).orElse(null);
            if (freshJob != null) {
                freshJob.setOutput(objectMapper.valueToTree(agentResult.output()));
                freshJob.setExitCode(sandboxResult.exitCode());
                // Persist container logs for introspection (truncate to limit to avoid bloat)
                if (sandboxResult.logs() != null && !sandboxResult.logs().isBlank()) {
                    String logs = sandboxResult.logs();
                    freshJob.setContainerLogs(
                        logs.length() > MAX_CONTAINER_LOGS_CHARS
                            ? logs.substring(logs.length() - MAX_CONTAINER_LOGS_CHARS)
                            : logs
                    );
                }
                if (terminalStatus == AgentJobStatus.COMPLETED) {
                    // Mark delivery as PENDING so crash recovery can distinguish
                    // "delivery not attempted yet" from "no delivery needed"
                    freshJob.setDeliveryStatus(DeliveryStatus.PENDING);
                }
                // Primary: agent-reported usage (from the Pi runner's usage.json)
                var agentUsage = agentResult.usage();
                ConfigSnapshot snap = parseSnapshotQuietly(freshJob);
                if (agentUsage != null && agentUsage.totalCalls() > 0) {
                    freshJob.setLlmTotalCalls(agentUsage.totalCalls());
                    freshJob.setLlmTotalInputTokens(agentUsage.inputTokens());
                    freshJob.setLlmTotalOutputTokens(agentUsage.outputTokens());
                    freshJob.setLlmTotalReasoningTokens(agentUsage.reasoningTokens());
                    freshJob.setLlmCacheReadTokens(agentUsage.cacheReadTokens());
                    freshJob.setLlmCacheWriteTokens(agentUsage.cacheWriteTokens());
                    // llmCostUsd deliberately left unset (#1368 slice 6): the runner no longer registers a
                    // per-token price table with the Pi SDK (see pi-provider.mjs), so agentUsage.costUsd() is
                    // now a structural constant (always 0.0), never a real measurement. Writing it would make
                    // this diagnostic column silently lie ("$0.00" reads as "free", not "not measured"). The
                    // authoritative, catalog-derived cost lives on the llm_usage_event ledger row below.
                    // Use typed snapshot so a future field rename fails compile rather than writing null.
                    String model = agentUsage.model();
                    if ((model == null || model.isBlank()) && snap != null) {
                        model = snap.upstreamModelId();
                    }
                    freshJob.setLlmModel(model);
                    if (snap != null) {
                        freshJob.setLlmModelVersion(snap.modelVersion());
                    }
                    ledgerWorkspaceId.set(freshJob.getWorkspace().getId());
                    // Cost is derived server-side by LlmUsageRecorder from the frozen catalog binding
                    // (#1368 slice 6) — the connectionScope/connectionId below identify WHICH binding,
                    // mirroring what ConfigSnapshot froze at dispatch time. Both null (a legacy,
                    // pre-catalog config) falls back to the recorder's ModelPricingService path.
                    ledgerSample.set(
                        new LlmUsageRecorder.LlmUsageSample(
                            LlmUsageJobType.from(freshJob.getJobType()),
                            freshJob.getId(),
                            model,
                            nullToZero(agentUsage.inputTokens()),
                            nullToZero(agentUsage.outputTokens()),
                            nullToZero(agentUsage.cacheReadTokens()),
                            nullToZero(agentUsage.cacheWriteTokens()),
                            nullToZero(agentUsage.reasoningTokens()),
                            agentUsage.totalCalls(),
                            snap != null ? snap.connectionScope() : null,
                            snap != null ? snap.connectionId() : null,
                            Instant.now()
                        )
                    );
                    log.info(
                        "LLM usage (agent-reported): model={}, calls={}, in={}, out={}, reasoning={}, jobId={}",
                        model,
                        agentUsage.totalCalls(),
                        agentUsage.inputTokens(),
                        agentUsage.outputTokens(),
                        agentUsage.reasoningTokens(),
                        jobId
                    );
                } else {
                    // #1368 fix wave: the job DID start executing (past claim+RUNNING) but produced no
                    // parseable usage — a missing or malformed usage.json. Recording nothing here would
                    // make this month's spend silently look fully accounted for; record an UNPRICED
                    // ledger entry instead so the budget verdict turns UNVERIFIABLE rather than staying
                    // falsely green. Never resolves a price — see LlmUsageRecorder#recordUnverifiable.
                    ledgerWorkspaceId.set(freshJob.getWorkspace().getId());
                    ledgerUnverifiable.set(true);
                    ledgerSample.set(
                        new LlmUsageRecorder.LlmUsageSample(
                            LlmUsageJobType.from(freshJob.getJobType()),
                            freshJob.getId(),
                            snap != null ? snap.upstreamModelId() : null,
                            0L,
                            0L,
                            0L,
                            0L,
                            0L,
                            0,
                            snap != null ? snap.connectionScope() : null,
                            snap != null ? snap.connectionId() : null,
                            Instant.now()
                        )
                    );
                    log.info(
                        "LLM usage unresolved (missing/malformed usage.json) — recording UNPRICED to keep budget " +
                            "verification visible: jobId={}, terminalStatus={}",
                        jobId,
                        terminalStatus
                    );
                }
                jobRepository.saveAndFlush(freshJob);
            }
            return true;
        });

        boolean won = Boolean.TRUE.equals(persisted);
        if (won && ledgerSample.get() != null) {
            if (ledgerUnverifiable.get()) {
                usageRecorder.recordUnverifiable(ledgerWorkspaceId.get(), ledgerSample.get());
            } else {
                usageRecorder.record(ledgerWorkspaceId.get(), ledgerSample.get());
            }
        }
        return won;
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
