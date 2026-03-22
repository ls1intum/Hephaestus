package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.adapter.AgentAdapterRegistry;
import de.tum.in.www1.hephaestus.agent.adapter.AgentResult;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import de.tum.in.www1.hephaestus.agent.handler.JobTypeHandlerRegistry;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
@WorkspaceAgnostic("NATS consumer processes jobs across all workspaces")
public class AgentJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentJobExecutor.class);

    private static final String MDC_JOB_ID = "agent.jobId";
    private static final String MDC_JOB_TYPE = "agent.jobType";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final Connection natsConnection;
    private final AgentNatsProperties natsProperties;
    private final AgentJobRepository jobRepository;
    private final AgentConfigRepository configRepository;
    private final JobTypeHandlerRegistry handlerRegistry;
    private final AgentAdapterRegistry adapterRegistry;
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

    public AgentJobExecutor(
        @Qualifier("agentNatsConnection") Connection natsConnection,
        AgentNatsProperties natsProperties,
        AgentJobRepository jobRepository,
        AgentConfigRepository configRepository,
        JobTypeHandlerRegistry handlerRegistry,
        AgentAdapterRegistry adapterRegistry,
        SandboxManager sandboxManager,
        @Qualifier("sandboxExecutor") AsyncTaskExecutor sandboxExecutor,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.natsConnection = natsConnection;
        this.natsProperties = natsProperties;
        this.jobRepository = jobRepository;
        this.configRepository = configRepository;
        this.handlerRegistry = handlerRegistry;
        this.adapterRegistry = adapterRegistry;
        this.sandboxManager = sandboxManager;
        this.sandboxExecutor = sandboxExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;

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
    @Order(2) // Must run after AgentNatsConfiguration.ensureStreamAndConsumer() which uses @Order(1)
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

    @PreDestroy
    public void stop() {
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
                            sandboxExecutor.execute(() -> executeJob(finalMsg));
                        } catch (RejectedExecutionException e) {
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
        try {
            claimAndExecute(jobId, msg);
        } catch (SandboxCancelledException e) {
            handleCancellation(jobId, msg);
        } catch (CannotAcquireLockException e) {
            msg.nakWithDelay(Duration.ofSeconds(5));
            log.debug("Lock timeout during claim for job {}, NAK'd with 5s delay", jobId);
        } catch (Exception e) {
            handleExecutionFailure(jobId, msg, e);
        } finally {
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
     */
    private void claimAndExecute(UUID jobId, Message msg) {
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
            return; // Already ack'd / nak'd / left for redelivery
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
            AgentResult agentResult = adapterRegistry.getAdapter(snapshot.agentType()).parseResult(result);

            JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());
            completeJob(jobId, agentResult, result, handler, job);
            msg.ack();

            Duration duration = Duration.between(startTime, Instant.now());
            executionDuration.record(duration);
            log.info("Agent job completed: jobId={}, duration={}", jobId, duration);
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
        AgentAdapter adapter = adapterRegistry.getAdapter(snapshot.agentType());

        // Wrap in a read-only transaction so prepareInputFiles/buildPrompt can
        // resolve lazy JPA proxies (e.g. PullRequest.author) on this sandbox thread.
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        readOnlyTx.setReadOnly(true);
        record PrepareResult(Map<String, byte[]> files, String prompt) {}
        PrepareResult prepared = readOnlyTx.execute(status -> {
            Map<String, byte[]> files = handler.prepareInputFiles(job);
            String p = handler.buildPrompt(job);
            return new PrepareResult(files, p);
        });

        AgentAdapterRequest adapterRequest = new AgentAdapterRequest(
            snapshot.agentType(),
            snapshot.llmProvider(),
            snapshot.credentialMode(),
            snapshot.modelName(),
            prepared.prompt(),
            job.getLlmApiKey(),
            job.getJobToken(),
            snapshot.allowInternet(),
            snapshot.timeoutSeconds()
        );

        AgentSandboxSpec agentSpec = adapter.buildSandboxSpec(adapterRequest);
        SandboxSpec sandboxSpec = buildSandboxSpec(jobId, prepared.files(), agentSpec, snapshot);
        return sandboxManager.execute(sandboxSpec);
    }

    /** Build the final {@link SandboxSpec} by merging handler and adapter inputs. */
    private static SandboxSpec buildSandboxSpec(
        UUID jobId,
        Map<String, byte[]> handlerFiles,
        AgentSandboxSpec agentSpec,
        ConfigSnapshot snapshot
    ) {
        // Merge handler + adapter input files (adapter takes precedence on collision)
        Map<String, byte[]> allInputFiles = new HashMap<>(handlerFiles);
        allInputFiles.putAll(agentSpec.inputFiles());

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
            agentSpec.outputPath()
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

            return new ClaimResult(job, snapshot);
        });
    }

    // ── Complete: micro-transaction #2 ──

    private void completeJob(
        UUID jobId,
        AgentResult agentResult,
        SandboxResult sandboxResult,
        JobTypeHandler handler,
        AgentJob job
    ) {
        AgentJobStatus terminalStatus = determineTerminalStatus(sandboxResult);
        persistTerminalState(jobId, agentResult, sandboxResult, terminalStatus);
        deliverResults(jobId, terminalStatus, handler);
    }

    /**
     * Determine the terminal status based on sandbox execution outcome.
     *
     * @return TIMED_OUT, FAILED, or COMPLETED
     */
    private AgentJobStatus determineTerminalStatus(SandboxResult sandboxResult) {
        if (sandboxResult.timedOut()) {
            return AgentJobStatus.TIMED_OUT;
        } else if (sandboxResult.exitCode() != 0) {
            return AgentJobStatus.FAILED;
        } else {
            return AgentJobStatus.COMPLETED;
        }
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
                // Persist container logs for introspection (truncate to 64KB to avoid bloat)
                if (sandboxResult.logs() != null && !sandboxResult.logs().isBlank()) {
                    String logs = sandboxResult.logs();
                    freshJob.setContainerLogs(logs.length() > 65536 ? logs.substring(logs.length() - 65536) : logs);
                }
                if (terminalStatus == AgentJobStatus.COMPLETED) {
                    // Mark delivery as PENDING so crash recovery can distinguish
                    // "delivery not attempted yet" from "no delivery needed"
                    freshJob.setDeliveryStatus(DeliveryStatus.PENDING);
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
