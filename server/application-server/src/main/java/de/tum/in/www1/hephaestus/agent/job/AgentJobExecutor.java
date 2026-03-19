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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
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
@ConditionalOnBean(name = "agentNatsConnection")
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
        UUID jobId;
        try {
            jobId = UUID.fromString(new String(msg.getData(), StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            log.warn("Invalid NATS message payload, acking to discard: {}", e.getMessage());
            msg.ack();
            return;
        }

        MDC.put(MDC_JOB_ID, jobId.toString());
        Instant startTime = Instant.now();
        ScheduledFuture<?> heartbeat = null;
        boolean claimed = false;

        try {
            // ── CLAIM (micro-transaction #1) ──
            Object claimResult = claimJob(jobId);
            if (claimResult == ClaimOutcome.ALREADY_CLAIMED) {
                msg.ack();
                return;
            }
            if (claimResult == ClaimOutcome.CONCURRENCY_FULL) {
                // NAK outside transaction — avoids NAK before commit
                msg.nakWithDelay(Duration.ofSeconds(30));
                return;
            }
            if (!(claimResult instanceof ClaimResult claim)) {
                log.warn("Unexpected claim result for job {}, leaving for redelivery", jobId);
                return;
            }
            claimed = true;

            AgentJob job = claim.job;
            ConfigSnapshot snapshot = claim.snapshot;
            MDC.put(MDC_JOB_TYPE, job.getJobType().name());

            log.info("Executing agent job: jobId={}, jobType={}", jobId, job.getJobType());

            // ── HEARTBEAT — first beat immediate, then every heartbeatInterval ──
            heartbeat = heartbeatScheduler.scheduleAtFixedRate(
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

            // ── PREPARE ──
            JobTypeHandler handler = handlerRegistry.getHandler(job.getJobType());
            AgentAdapter adapter = adapterRegistry.getAdapter(snapshot.agentType());

            Map<String, byte[]> handlerFiles = handler.prepareInputFiles(job);
            String prompt = handler.buildPrompt(job);

            AgentAdapterRequest adapterRequest = new AgentAdapterRequest(
                snapshot.agentType(),
                snapshot.llmProvider(),
                snapshot.credentialMode(),
                snapshot.modelName(),
                prompt,
                job.getLlmApiKey(),
                job.getJobToken(),
                snapshot.allowInternet(),
                snapshot.timeoutSeconds()
            );

            AgentSandboxSpec agentSpec = adapter.buildSandboxSpec(adapterRequest);

            // Merge handler + adapter input files (adapter takes precedence on collision)
            Map<String, byte[]> allInputFiles = new HashMap<>(handlerFiles);
            allInputFiles.putAll(agentSpec.inputFiles());

            ResourceLimits limits = new ResourceLimits(
                ResourceLimits.DEFAULT.memoryBytes(),
                ResourceLimits.DEFAULT.cpus(),
                ResourceLimits.DEFAULT.pidsLimit(),
                Duration.ofSeconds(snapshot.timeoutSeconds())
            );

            SandboxSpec sandboxSpec = new SandboxSpec(
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

            // ── EXECUTE ──
            SandboxResult result = sandboxManager.execute(sandboxSpec);

            // ── COLLECT + COMPLETE ──
            AgentResult agentResult = adapter.parseResult(result);

            completeJob(jobId, agentResult, result, handler, job);
            msg.ack();

            Duration duration = Duration.between(startTime, Instant.now());
            executionDuration.record(duration);
            log.info("Agent job completed: jobId={}, duration={}", jobId, duration);
        } catch (SandboxCancelledException e) {
            // Job was cancelled — conditional update handles the race
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
        } catch (CannotAcquireLockException e) {
            // Config row lock timeout — NAK with delay for faster retry than ack_wait (70min)
            msg.nakWithDelay(Duration.ofSeconds(5));
            log.debug("Lock timeout during claim for job {}, NAK'd with 5s delay", jobId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            if (!claimed) {
                // Claim failed (DB timeout, connection pool exhaustion) — don't ack,
                // let NATS redeliver. Job is still QUEUED.
                log.warn("Claim failed for job {}, will be redelivered: {}", jobId, e.getMessage());
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
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            MDC.remove(MDC_JOB_ID);
            MDC.remove(MDC_JOB_TYPE);
        }
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
        // Determine terminal status
        AgentJobStatus terminalStatus;
        String errorMessage = null;
        if (sandboxResult.timedOut()) {
            terminalStatus = AgentJobStatus.TIMED_OUT;
            errorMessage = "Container timed out";
        } else if (sandboxResult.exitCode() != 0) {
            terminalStatus = AgentJobStatus.FAILED;
            errorMessage = "Container exited with code " + sandboxResult.exitCode();
        } else {
            terminalStatus = AgentJobStatus.COMPLETED;
        }

        // Persist terminal status first, then output — ensures cancelled jobs don't get output
        String finalErrorMessage = errorMessage;
        AgentJobStatus finalStatus = terminalStatus;
        transactionTemplate.executeWithoutResult(status -> {
            int updated = jobRepository.transitionStatus(
                jobId,
                finalStatus,
                Instant.now(),
                finalErrorMessage,
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
                jobRepository.saveAndFlush(freshJob);
            }
        });

        // Deliver results (outside transaction — may call external APIs)
        if (terminalStatus == AgentJobStatus.COMPLETED) {
            try {
                // Reload to get the freshly persisted output
                AgentJob deliverJob = jobRepository.findById(jobId).orElse(null);
                if (deliverJob != null) {
                    handler.deliver(deliverJob);
                }
            } catch (Exception e) {
                log.warn("Delivery failed for job {} (output saved, job still COMPLETED): {}", jobId, e.getMessage());
                meterRegistry.counter("agent.job.delivery.failure").increment();
            }
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
