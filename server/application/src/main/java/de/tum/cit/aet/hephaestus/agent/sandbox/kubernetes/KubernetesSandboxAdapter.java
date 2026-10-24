package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewaySessions;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.SandboxEnvironment;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.fabric8.kubernetes.api.model.Pod;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Practice reviews in Kubernetes, one {@code batch/v1} Job per attempt ({@link KubernetesReviewJobs}). The Job's one
 * Pod is the review Pod: its init container fills the attempt workspace from the gateway session, as Docker's
 * initializer container does; the runtime container then runs the review with {@code /workspace} read-only and uploads
 * its result through the same session. The gateway session, its token and the attempt it is bound to stay the
 * authority over what a result belongs to: this class neither reads nor trusts anything else the Pod writes.
 *
 * <p>Each {@link #execute} is self-contained, blocks its caller, and in {@code finally} deletes its Job with every Pod
 * the Job controls and closes its session. A step that fails there is logged and left to the Job's own deadline and TTL
 * and to {@link KubernetesSandboxReconciler}. The Job name holds the job and attempt, so a second Job of the same
 * attempt cannot be created.
 */
public class KubernetesSandboxAdapter implements SandboxManager {

    private static final Logger log = LoggerFactory.getLogger(KubernetesSandboxAdapter.class);

    private static final String HOSTNAME = "agent";
    private static final String MDC_JOB_ID = "sandbox.jobId";
    private static final String MDC_JOB = "sandbox.kubernetesJob";
    private static final String MDC_POD = "sandbox.pod";

    /** The exit a container reports when SIGKILL ended it, as Docker's wait reports. */
    private static final int SIGKILL_EXIT_CODE = 137;

    /** Beyond the upload deadline: the Job controller stops an attempt whose worker no longer does. */
    private static final Duration DEADLINE_BACKSTOP = Duration.ofMinutes(2);

    private final KubernetesPods pods;
    private final KubernetesReviewJobs reviewJobs;
    private final KubernetesWorker worker;
    private final SandboxWorkspaceManager workspaceManager;
    private final SandboxGatewaySessions gatewaySessions;
    private final MeterRegistry meterRegistry;

    private final Counter executionsSuccess;
    private final Counter executionsFailed;
    private final Counter executionsTimedOut;
    private final Counter executionsCancelled;
    private final Timer executionDuration;

    /** Presence of a flag is what marks a job as running. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    /** The Job of a running attempt, and its Pod once the Job controller has created it. */
    private record Active(String job, @Nullable String pod) {}

    /** Only a registered Job, and its registered Pod, can be deleted by {@link #cancel}. */
    private final ConcurrentHashMap<UUID, Active> active = new ConcurrentHashMap<>();

    public KubernetesSandboxAdapter(
            KubernetesPods pods,
            KubernetesReviewJobs reviewJobs,
            KubernetesWorker worker,
            SandboxWorkspaceManager workspaceManager,
            SandboxGatewaySessions gatewaySessions,
            MeterRegistry meterRegistry) {
        this.pods = pods;
        this.reviewJobs = reviewJobs;
        this.worker = worker;
        this.workspaceManager = workspaceManager;
        this.gatewaySessions = gatewaySessions;
        this.meterRegistry = meterRegistry;
        this.executionsSuccess = execution(meterRegistry, "success", "Successful sandbox executions");
        this.executionsFailed = execution(meterRegistry, "failure", "Failed sandbox executions");
        this.executionsTimedOut = execution(meterRegistry, "timeout", "Timed-out sandbox executions");
        this.executionsCancelled = execution(meterRegistry, "cancelled", "Cancelled sandbox executions");
        this.executionDuration = Timer.builder(AgentMetrics.SANDBOX_EXECUTION_DURATION)
                .description("Duration of sandbox executions")
                .register(meterRegistry);
        meterRegistry.gaugeMapSize(AgentMetrics.SANDBOX_CONTAINERS_ACTIVE, Tags.empty(), active);
    }

    private static Counter execution(MeterRegistry registry, String outcome, String description) {
        return Counter.builder(AgentMetrics.SANDBOX_EXECUTIONS)
                .tag("outcome", outcome)
                .description(description)
                .register(registry);
    }

    @Override
    public SandboxResult execute(SandboxSpec spec) throws SandboxException {
        UUID jobId = spec.jobId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        if (cancellationFlags.putIfAbsent(jobId, cancelled) != null) {
            throw new SandboxException("Job already executing: " + jobId);
        }
        String createdJob = null;
        String podName = null;
        SandboxGatewaySessions.Session session = null;
        Instant startTime = Instant.now();
        MDC.put(MDC_JOB_ID, jobId.toString());
        log.info("Starting sandbox execution: image={}", spec.image());
        try {
            checkCancelled(cancelled, jobId);
            worker.requireIsolation();
            NetworkPolicy policy = spec.networkPolicy();
            if (policy != null && policy.internetAccess()) {
                // The chart's policies give a sandbox Pod its gateway and nothing else.
                throw new SandboxException("Kubernetes sandboxes have no internet access");
            }
            if (!spec.outputPath().equals(SandboxLayout.OUTPUT_PATH)) {
                throw new SandboxException("Sandbox results must use the runtime output directory");
            }
            pods.requireLimits(spec.resourceLimits());
            Integer claimedAttempt = spec.attempt();
            Map<String, String> labels = worker.labels(KubernetesWorker.KIND_REVIEW);
            labels.put(KubernetesWorker.JOB_ID, jobId.toString());
            if (claimedAttempt != null) {
                labels.put(KubernetesWorker.JOB_ATTEMPT, claimedAttempt.toString());
            }

            String token = requireToken(policy == null ? null : policy.llmProxyToken());
            Path inputTar = workspaceManager.createInputTar(
                    spec.inputFiles(), spec.inputFilesOnDisk(), spec.inputDirectories());
            session = openSession(
                    inputTar,
                    () -> claimedAttempt == null
                            ? gatewaySessions.register(jobId, token, inputTar, "out")
                            : gatewaySessions.registerAttempt(
                                    jobId, claimedAttempt, spec.image(), token, inputTar, "out"));

            Map<String, String> environment =
                    SandboxEnvironment.build(spec, worker.gatewayHost(), worker.gatewayPort());
            environment.put("SANDBOX_RUNTIME_URL", runtimeUrl(worker, session));
            Instant workDeadline = startTime.plus(spec.resourceLimits().maxRuntime());
            String declaredDeadline = spec.environment().get("SANDBOX_WORK_DEADLINE_MS");
            if (declaredDeadline != null) {
                Instant requestedDeadline = Instant.ofEpochMilli(Long.parseLong(declaredDeadline));
                if (requestedDeadline.isBefore(workDeadline)) {
                    workDeadline = requestedDeadline;
                }
            }
            Instant uploadDeadline = workDeadline.plus(SandboxLayout.RESULT_UPLOAD_GRACE);
            environment.put("SANDBOX_WORK_DEADLINE_MS", Long.toString(workDeadline.toEpochMilli()));
            environment.put("SANDBOX_UPLOAD_DEADLINE_MS", Long.toString(uploadDeadline.toEpochMilli()));

            Duration untilWorkDeadline = Duration.between(Instant.now(), workDeadline);
            if (untilWorkDeadline.isNegative() || untilWorkDeadline.isZero()) {
                throw new SandboxException("Sandbox execution deadline exceeded before initialization");
            }
            // The wrapper holds the gateway token: a process of the sandbox must not open its inspector with SIGUSR1.
            List<String> command = new ArrayList<>(List.of("node", "--disable-sigusr1", "/opt/pi-sdk/gateway-run.ts"));
            command.addAll(spec.command());
            long backstop = Duration.between(Instant.now(), uploadDeadline)
                    .plus(DEADLINE_BACKSTOP)
                    .toSeconds();
            SecurityProfile requested = spec.securityProfile();
            Pod template = pods.workspacePod(
                    new KubernetesPods.Workload(jobName(jobId, claimedAttempt), labels, spec.image(), HOSTNAME),
                    environment,
                    command,
                    spec.resourceLimits(),
                    requested != null ? requested : SecurityProfile.DEFAULT);
            String job = template.getMetadata().getName();
            reviewJobs.create(reviewJobs.job(template, backstop));
            createdJob = job;
            active.put(jobId, new Active(job, null));
            MDC.put(MDC_JOB, job);
            log.info("Sandbox Job created");
            // Cancellation during creation cannot delete the Job until it is registered.
            checkCancelled(cancelled, jobId);

            // The work deadline bounds this wait too: a Pod created after it could do no work. A wait that ends without
            // a Pod is the runtime's failure, not one of the review.
            Duration childStart = Duration.between(Instant.now(), workDeadline);
            if (childStart.compareTo(KubernetesReviewJobs.CHILD_START) > 0) {
                childStart = KubernetesReviewJobs.CHILD_START;
            }
            Pod child = reviewJobs.awaitPod(job, childStart, cancelled::get);
            String name = child.getMetadata().getName();
            podName = name;
            active.put(jobId, new Active(job, name));
            MDC.put(MDC_POD, name);
            log.info("Sandbox Job created its Pod");
            // Cancellation before the Pod was registered deleted the Job only.
            checkCancelled(cancelled, jobId);

            KubernetesPods.Waited initialized = pods.waitFor(
                    name, KubernetesPods::initializationEnded, Duration.between(Instant.now(), workDeadline));
            checkCancelled(cancelled, jobId);
            if (initialized.timedOut()) {
                Pod stopped = pods.stop(name);
                Integer exit = stopped == null ? null : KubernetesPods.exitCode(stopped, KubernetesPods.INITIALIZER);
                executionsTimedOut.increment();
                return new SandboxResult(
                        exit == null ? SIGKILL_EXIT_CODE : exit,
                        Map.of(),
                        pods.logs(name, KubernetesPods.INITIALIZER),
                        true,
                        Duration.between(startTime, Instant.now()));
            }
            Pod afterInit = present(initialized.pod());
            if (KubernetesPods.notIsolated(afterInit)) {
                throw new SandboxInfrastructureException("The network policy did not filter the sandbox Pod in time");
            }
            Integer initExit = KubernetesPods.exitCode(afterInit, KubernetesPods.INITIALIZER);
            if (initExit == null || initExit != 0) {
                logTranscriptTail(pods.logs(name, KubernetesPods.INITIALIZER));
                String waiting = KubernetesPods.fatalWaiting(afterInit);
                throw new SandboxException(
                        "Sandbox workspace initialization failed" + (waiting == null ? "" : ": " + waiting));
            }
            if (!Instant.now().isBefore(workDeadline)) {
                pods.stop(name);
                executionsTimedOut.increment();
                return new SandboxResult(
                        SandboxLayout.EXIT_WORK_TIMEOUT,
                        Map.of(),
                        "",
                        true,
                        Duration.between(startTime, Instant.now()));
            }

            Duration timeout = Duration.between(Instant.now(), uploadDeadline);
            if (timeout.isNegative() || timeout.isZero()) {
                throw new SandboxException("Sandbox result upload deadline exceeded");
            }
            KubernetesPods.Waited ran = pods.waitFor(name, KubernetesPods::runtimeEnded, timeout);
            // A cancellation-induced exit must remain cancellation, not a normal result.
            checkCancelled(cancelled, jobId);
            boolean waitTimedOut = ran.timedOut();
            Pod ended = waitTimedOut ? pods.stop(name) : present(ran.pod());
            Integer runtimeExit = ended == null ? null : KubernetesPods.exitCode(ended, KubernetesPods.RUNTIME);
            if (runtimeExit == null && !waitTimedOut) {
                String waiting = KubernetesPods.fatalWaiting(present(ended));
                throw new SandboxException(
                        "The sandbox Pod ended before its runtime ran" + (waiting == null ? "" : ": " + waiting));
            }
            int exitCode = runtimeExit == null ? SIGKILL_EXIT_CODE : runtimeExit;

            Map<String, byte[]> outputFiles;
            try {
                outputFiles = session.result();
            } catch (RuntimeException exception) {
                if (!waitTimedOut
                        && exitCode != SandboxLayout.EXIT_WORK_TIMEOUT
                        && exitCode != SandboxLayout.EXIT_UPLOAD_FAILED
                        && exitCode != SandboxLayout.EXIT_ENVELOPE_MISMATCH) {
                    throw exception;
                }
                outputFiles = Map.of();
            }
            String logs = pods.logs(name, KubernetesPods.RUNTIME);

            boolean timedOut = waitTimedOut || exitCode == SandboxLayout.EXIT_WORK_TIMEOUT;
            if (timedOut) {
                executionsTimedOut.increment();
            } else {
                executionsSuccess.increment();
            }
            Duration duration = Duration.between(startTime, Instant.now());
            log.info(
                    "Sandbox execution complete: exitCode={}, timedOut={}, outputFiles={}, duration={}",
                    exitCode,
                    timedOut,
                    outputFiles.size(),
                    duration);
            return new SandboxResult(exitCode, outputFiles, logs, timedOut, duration);
        } catch (SandboxCancelledException e) {
            executionsCancelled.increment();
            log.info("Sandbox execution cancelled");
            throw e;
        } catch (SandboxException e) {
            executionsFailed.increment();
            log.error("Sandbox execution failed: errorType={}", e.getClass().getName(), e);
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            executionsFailed.increment();
            log.error("Unexpected error during sandbox execution", e);
            throw new SandboxException("Sandbox execution failed for job: " + jobId, e);
        } finally {
            // Unregister before deletion so cancellation cannot race cleanup.
            active.remove(jobId);
            executionDuration.record(Duration.between(startTime, Instant.now()));
            if (createdJob != null) {
                // First the Job, so that its controller creates no other Pod; its deletion takes all its Pods.
                String created = createdJob;
                suppressAndLog("delete job", jobId, () -> reviewJobs.delete(created));
            }
            if (podName != null) {
                // The Pod that this attempt watched goes at once, not after the garbage collector's turn.
                String started = podName;
                suppressAndLog("delete pod", jobId, () -> pods.delete(started, 0));
            }
            if (session != null) {
                var opened = session;
                suppressAndLog("close gateway session", jobId, () -> {
                    try {
                        opened.close();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            }
            cancellationFlags.remove(jobId);
            MDC.remove(MDC_JOB_ID);
            MDC.remove(MDC_JOB);
            MDC.remove(MDC_POD);
        }
    }

    @Override
    public void cancel(UUID jobId) {
        AtomicBoolean flag = cancellationFlags.get(jobId);
        if (flag == null) {
            log.debug("Cancel called for unknown/completed job: jobId={}", jobId);
            return;
        }
        flag.set(true);
        log.info("Cancellation requested: jobId={}", jobId);
        // Keep the deletion atomic with unregistration by cleanup. The Job goes first, so that its controller creates
        // no
        // other Pod; the registered Pod is deleted too, so that the attempt's wait on it ends without the collector.
        active.computeIfPresent(jobId, (id, running) -> {
            stop(running);
            return running;
        });
    }

    /**
     * Deletes the attempt's Job, then its registered Pod. Each deletion is attempted even when the other fails: a Pod
     * that stays would keep the attempt waiting until its deadline. The first failure is thrown, with the second.
     */
    private void stop(Active running) {
        RuntimeException failure = null;
        try {
            reviewJobs.delete(running.job());
        } catch (RuntimeException e) {
            failure = e;
        }
        String pod = running.pod();
        if (pod != null) {
            try {
                pods.delete(pod, pods.stopGraceSeconds());
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isHealthy() {
        return worker.isolated();
    }

    /**
     * One Job name per attempt: the API refuses a second Job of an attempt that already has one. The Job controller
     * labels each Pod with this name, so it stays within the 63 characters of a label value.
     */
    static String jobName(UUID jobId, @Nullable Integer attempt) {
        String suffix = attempt == null ? "x" + UUID.randomUUID().toString().substring(0, 7) : attempt.toString();
        return "hephaestus-review-" + jobId + "-" + suffix;
    }

    static String runtimeUrl(KubernetesWorker worker, SandboxGatewaySessions.Session session) {
        return worker.gatewayUrl() + "/internal/llm/runtime/" + session.id();
    }

    /** Registers a session as {@code SandboxAttemptLauncher} does: its input archive goes with a failed registration. */
    static SandboxGatewaySessions.Session openSession(Path inputTar, Registration registration) throws IOException {
        try {
            return registration.register();
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(inputTar);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    interface Registration {
        SandboxGatewaySessions.Session register() throws IOException;
    }

    static String requireToken(@Nullable String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Gateway credential required");
        }
        return token;
    }

    private static Pod present(@Nullable Pod pod) {
        if (pod == null) {
            throw new SandboxException("The sandbox Pod was deleted");
        }
        return pod;
    }

    private static void checkCancelled(AtomicBoolean flag, UUID jobId) {
        if (flag.get()) {
            throw new SandboxCancelledException("Job cancelled: " + jobId);
        }
    }

    /**
     * The transcript holds what the run was asked, what it read and the provider errors it saw, so no log level
     * receives it; the log gets its size only.
     */
    private static void logTranscriptTail(@Nullable String logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        log.atWarn()
                .addKeyValue("event.name", "sandbox.transcript.withheld")
                .addKeyValue("sandbox.transcript.characters", logs.length())
                .addKeyValue("sandbox.transcript.lines", logs.lines().count())
                .log("Sandbox logs before cleanup are withheld from the shared log");
    }

    private void suppressAndLog(String operation, UUID jobId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            meterRegistry
                    .counter(AgentMetrics.SANDBOX_CLEANUP_FAILURES, "step", operation)
                    .increment();
            log.warn(
                    "Cleanup failed ({}): jobId={}, errorType={}",
                    operation,
                    jobId,
                    e.getClass().getName());
        }
    }
}
