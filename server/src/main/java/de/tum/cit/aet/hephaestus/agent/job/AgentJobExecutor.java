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
import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.core.runtime.worker.WorkerProperties;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.lang.Nullable;
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
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
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
    private final java.util.Optional<WorkerCapacityState> capacityState;
    private final java.util.Optional<WorkerProperties> workerProperties;

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
        java.util.Optional<WorkerCapacityState> capacityState,
        java.util.Optional<WorkerProperties> workerProperties
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
        this.capacityState = capacityState;
        this.workerProperties = workerProperties;

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
            "Agent job executor started: consumer={}, maxAckPending={}",
            natsProperties.consumerName(),
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
     * Transition every job currently {@link AgentJobStatus#RUNNING} on this worker to
     * {@link AgentJobStatus#CANCELLED} with the given reason. The in-flight sandbox tasks
     * continue running locally but their terminal-state write will fail the {@code IN :fromStatuses}
     * guard and the result will be discarded; NATS holds the message unack'd and redelivers to
     * the next worker.
     *
     * <p>{@code RUNNING} is shared across workers in principle, but in practice the JetStream
     * WorkQueue + {@code maxAckPending} bound ensures only this worker holds RUNNING messages.
     */
    public void cancelInFlight(AgentJobCancellationReason reason) {
        java.util.List<AgentJob> running = jobRepository.findByStatus(AgentJobStatus.RUNNING);
        if (running.isEmpty()) {
            return;
        }
        log.info("Cancelling {} in-flight job(s) with reason {}", running.size(), reason);
        Instant now = Instant.now();
        String error = "worker draining";
        for (AgentJob job : running) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    jobRepository.transitionToCancelled(
                        job.getId(),
                        now,
                        error,
                        reason,
                        Set.of(AgentJobStatus.RUNNING)
                    )
                );
            } catch (Exception e) {
                log.warn("Failed to cancel in-flight job {}: {}", job.getId(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Legacy single-method stop used when no {@code WorkerDrainCoordinator} owns the worker
     * lifecycle (e.g., non-worker monolith mode). Slim, drain-equivalent default — fixes the
     * latent bug where the previous {@code @PreDestroy} never awaited in-flight sandbox tasks.
     */
    @PreDestroy
    public void stop() {
        stopAcceptingNewJobs();
        awaitInFlight(Duration.ofSeconds(30));
        // Any survivors will be NAK'd on connection close.
    }

    // ── Pull loop ──

    private void pullLoop() {
        while (running.get()) {
            try {
                StreamContext streamContext = natsConnection.getStreamContext(natsProperties.streamName());
                ConsumerContext consumerContext = streamContext.getConsumerContext(natsProperties.consumerName());

                FetchConsumeOptions fetchOptions = FetchConsumeOptions.builder()
                    .maxMessages(natsProperties.maxAckPending())
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

    // ── Job execution ──

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
        // ── CLAIM (micro-transaction #1) — may throw CannotAcquireLockException ──
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
            // ── PREPARE + EXECUTE + COMPLETE ──
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
        if (claimResult == ClaimOutcome.ALREADY_CLAIMED) {
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

        // BYO worker pod: when the operator configures hephaestus.worker.llm.{base-url,api-key},
        // override the per-job credential mode + endpoint so agent-pi reaches the operator's LLM
        // directly. The app-pod's bundled LLM proxy is not reachable from a worker host. ADR 0009.
        WorkerProperties.Llm workerLlm = workerProperties.map(WorkerProperties::llm).orElse(null);
        boolean workerLlmActive = workerLlm != null && workerLlm.isConfigured();
        CredentialMode credentialMode = workerLlmActive ? CredentialMode.API_KEY : snapshot.credentialMode();
        String credential = workerLlmActive ? workerLlm.apiKey() : job.getLlmApiKey();
        String baseUrl = workerLlmActive ? workerLlm.baseUrl() : null;

        PracticeAgentRequest adapterRequest = new PracticeAgentRequest(
            snapshot.llmProvider(),
            credentialMode,
            snapshot.modelName(),
            credential,
            baseUrl,
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
        return sandboxManager.execute(sandboxSpec);
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
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.transitionStatus(
                jobId,
                AgentJobStatus.CANCELLED,
                Instant.now(),
                "Cancelled during execution",
                Set.of(AgentJobStatus.RUNNING)
            );
        });
        msg.ack();
        log.info("Agent job cancelled: jobId={}", jobId);
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

        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.transitionStatus(
                jobId,
                AgentJobStatus.FAILED,
                Instant.now(),
                errorMessage,
                Set.of(AgentJobStatus.RUNNING)
            );
        });
        msg.ack();
    }

    // ── Claim: micro-transaction #1 ──

    /** Sentinel values for claimJob results that require post-transaction NATS actions. */
    private enum ClaimOutcome {
        ALREADY_CLAIMED,
        CONCURRENCY_FULL,
    }

    private record ClaimResult(AgentJob job, ConfigSnapshot snapshot) {}

    /**
     * Attempt to claim a job within a micro-transaction. Returns:
     * - {@link ClaimResult} on success
     * - {@link ClaimOutcome#ALREADY_CLAIMED} if job is not QUEUED (caller should ACK)
     * - {@link ClaimOutcome#CONCURRENCY_FULL} if concurrency limit reached (caller should NAK with delay)
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

            // Transition to RUNNING
            job.setStatus(AgentJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            ConfigSnapshot snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);

            capacityState.ifPresent(WorkerCapacityState::claimReview);
            return new ClaimResult(job, snapshot);
        });
    }

    /** Release the review-capacity slot on any terminal transition (success/failure/cancel/timeout). */
    private void releaseCapacity() {
        capacityState.ifPresent(WorkerCapacityState::releaseReview);
    }

    // ── Complete: micro-transaction #2 ──

    private void completeJob(
        UUID jobId,
        AgentResult agentResult,
        SandboxResult sandboxResult,
        JobTypeHandler handler,
        AgentJob job
    ) {
        AgentJobStatus terminalStatus = determineTerminalStatus(sandboxResult, agentResult);
        persistTerminalState(jobId, agentResult, sandboxResult, terminalStatus);
        deliverResults(jobId, terminalStatus, handler);
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
        if (sandboxResult.exitCode() == WorkspaceAbi.EXIT_ENVELOPE_MISMATCH) {
            log.error(
                "Pi runner rejected task envelope (exit {}) — server/image schemaVersion or kind drift. " +
                    "Rebuild the agent-pi image or roll back the server.",
                WorkspaceAbi.EXIT_ENVELOPE_MISMATCH
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
     * Persist terminal status and output within a single transaction.
     * Ensures cancelled jobs don't get output persisted.
     */
    private void persistTerminalState(
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

        transactionTemplate.executeWithoutResult(status -> {
            int updated = jobRepository.transitionStatus(
                jobId,
                terminalStatus,
                Instant.now(),
                errorMessage,
                Set.of(AgentJobStatus.RUNNING)
            );

            if (updated == 0) {
                // Job was cancelled during execution — skip output persist
                log.info("Job was cancelled during execution, skipping output persist: jobId={}", jobId);
                return;
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
                if (agentUsage != null && agentUsage.totalCalls() > 0) {
                    freshJob.setLlmTotalCalls(agentUsage.totalCalls());
                    freshJob.setLlmTotalInputTokens(agentUsage.inputTokens());
                    freshJob.setLlmTotalOutputTokens(agentUsage.outputTokens());
                    freshJob.setLlmTotalReasoningTokens(agentUsage.reasoningTokens());
                    freshJob.setLlmCacheReadTokens(agentUsage.cacheReadTokens());
                    freshJob.setLlmCacheWriteTokens(agentUsage.cacheWriteTokens());
                    freshJob.setLlmCostUsd(agentUsage.costUsd());
                    // Use typed snapshot so a future field rename fails compile rather than writing null.
                    String model = agentUsage.model();
                    ConfigSnapshot snap = null;
                    var snapshotNode = freshJob.getConfigSnapshot();
                    if (snapshotNode != null) {
                        try {
                            snap = ConfigSnapshot.fromJson(snapshotNode, objectMapper);
                        } catch (Exception e) {
                            log.warn("Could not deserialise config snapshot for usage metadata: {}", e.getMessage());
                        }
                    }
                    if ((model == null || model.isBlank()) && snap != null) {
                        model = snap.modelName();
                    }
                    freshJob.setLlmModel(model);
                    if (snap != null) {
                        freshJob.setLlmModelVersion(snap.modelVersion());
                    }
                    log.info(
                        "LLM usage (agent-reported): model={}, calls={}, in={}, out={}, reasoning={}, cost={}, jobId={}",
                        model,
                        agentUsage.totalCalls(),
                        agentUsage.inputTokens(),
                        agentUsage.outputTokens(),
                        agentUsage.reasoningTokens(),
                        agentUsage.costUsd(),
                        jobId
                    );
                }
                jobRepository.saveAndFlush(freshJob);
            }
        });
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
