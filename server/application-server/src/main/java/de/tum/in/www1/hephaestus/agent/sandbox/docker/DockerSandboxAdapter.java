package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Docker-based implementation of {@link SandboxManager}.
 *
 * <p>Orchestrates the 4-phase container lifecycle:
 *
 * <ol>
 *   <li><b>PREPARE</b> — create network, connect app-server, create container, inject files
 *   <li><b>EXECUTE</b> — start container, wait for completion with timeout
 *   <li><b>COLLECT</b> — extract output files, capture logs
 *   <li><b>CLEANUP</b> — remove container, disconnect app-server, remove network
 * </ol>
 *
 * <p>Each call to {@link #execute} is self-contained and blocking. Callers submit to {@code
 * sandboxExecutor} (bounded platform thread pool). Cleanup runs in {@code finally} regardless of
 * outcome — partial failures are logged and left for reconciliation.
 *
 * <p>Cancellation uses a simple {@link AtomicBoolean} flag per job. The execute loop checks the
 * flag between phases and stops the container if set.
 */
public class DockerSandboxAdapter implements SandboxManager {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxAdapter.class);
    private static final String CONTAINER_USER = "1000:1000";
    private static final String CONTAINER_HOSTNAME = "agent";
    private static final String OUTPUT_PATH_DEFAULT = "/workspace/.output";
    private static final int LOG_TAIL_LINES = 500;
    private static final String PROXY_URL_PLACEHOLDER = "{appServerIp}";

    private static final String MDC_JOB_ID = "sandbox.jobId";
    private static final String MDC_CONTAINER_ID = "sandbox.containerId";

    /**
     * Maximum container log size to emit in a single log event (prevents log aggregator overflow).
     * This is the <em>emission</em> limit; see {@link DockerClientOperations#MAX_LOG_BYTES} for the
     * upstream <em>collection</em> limit (1 MB).
     */
    private static final int MAX_LOG_EVENT_BYTES = 32 * 1024; // 32 KB

    /**
     * Exact environment variable names that must never be set by callers. Covers library injection,
     * path manipulation, and proxy hijacking vectors.
     *
     * @see #BLOCKED_ENV_PREFIXES
     * @see #isBlockedEnvVar(String)
     */
    static final Set<String> BLOCKED_ENV_VARS = Set.of(
        "LD_PRELOAD",
        "LD_LIBRARY_PATH",
        "PATH",
        "HOME",
        "SHELL",
        "USER",
        "http_proxy",
        "https_proxy",
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "no_proxy",
        "NO_PROXY"
    );

    /**
     * Environment variable prefixes that must never be set by callers. Blocks entire credential
     * families (AWS, GCP, Azure, Docker) rather than individual keys — catches new credential vars
     * automatically (e.g. {@code AWS_ROLE_ARN}, {@code GOOGLE_CLOUD_PROJECT}).
     *
     * @see #BLOCKED_ENV_VARS
     * @see #isBlockedEnvVar(String)
     */
    static final java.util.List<String> BLOCKED_ENV_PREFIXES = java.util.List.of(
        "AWS_",
        "GOOGLE_",
        "GCP_",
        "AZURE_",
        "DOCKER_",
        "ALIBABA_CLOUD_"
    );

    private final SandboxNetworkManager networkManager;
    private final SandboxWorkspaceManager workspaceManager;
    private final SandboxContainerManager containerManager;
    private final ContainerSecurityPolicy securityPolicy;
    private final SandboxProperties properties;

    // Metrics
    private final Counter executionsSuccess;
    private final Counter executionsFailed;
    private final Counter executionsTimedOut;
    private final Counter executionsCancelled;
    private final MeterRegistry meterRegistry;
    private final Timer executionDuration;

    /** Active cancellation flags — presence indicates a running job. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    /** Active container IDs — allows cancel() to stop running containers. */
    private final ConcurrentHashMap<UUID, String> activeContainers = new ConcurrentHashMap<>();

    public DockerSandboxAdapter(
        SandboxNetworkManager networkManager,
        SandboxWorkspaceManager workspaceManager,
        SandboxContainerManager containerManager,
        ContainerSecurityPolicy securityPolicy,
        SandboxProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.networkManager = networkManager;
        this.workspaceManager = workspaceManager;
        this.containerManager = containerManager;
        this.securityPolicy = securityPolicy;
        this.properties = properties;

        this.executionsSuccess = Counter.builder("sandbox.executions")
            .tag("outcome", "success")
            .description("Successful sandbox executions")
            .register(meterRegistry);
        this.executionsFailed = Counter.builder("sandbox.executions")
            .tag("outcome", "failure")
            .description("Failed sandbox executions")
            .register(meterRegistry);
        this.executionsTimedOut = Counter.builder("sandbox.executions")
            .tag("outcome", "timeout")
            .description("Timed-out sandbox executions")
            .register(meterRegistry);
        this.executionsCancelled = Counter.builder("sandbox.executions")
            .tag("outcome", "cancelled")
            .description("Cancelled sandbox executions")
            .register(meterRegistry);
        this.meterRegistry = meterRegistry;
        this.executionDuration = Timer.builder("sandbox.execution.duration")
            .description("Duration of sandbox executions")
            .register(meterRegistry);

        // Gauge for active containers
        meterRegistry.gaugeMapSize("sandbox.containers.active", Tags.empty(), this.activeContainers);
    }

    @Override
    public SandboxResult execute(SandboxSpec spec) throws SandboxException {
        UUID jobId = spec.jobId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        if (cancellationFlags.putIfAbsent(jobId, cancelled) != null) {
            throw new SandboxException("Job already executing: " + jobId);
        }

        String networkId = null;
        String containerId = null;
        Instant startTime = Instant.now();

        MDC.put(MDC_JOB_ID, jobId.toString());

        log.info("Starting sandbox execution: image={}", spec.image());

        try {
            // ── PHASE 1: PREPARE ──
            checkCancelled(cancelled, jobId);

            // Create isolated network
            boolean allowInternet = spec.networkPolicy() != null && spec.networkPolicy().internetAccess();
            networkId = networkManager.createJobNetwork(jobId, allowInternet);

            // Connect app-server to the job network (multi-homing) and get its IP
            String appServerIp = networkManager.connectAppServer(networkId);

            checkCancelled(cancelled, jobId);

            // Build environment with LLM proxy URL
            Map<String, String> environment = buildEnvironment(spec, appServerIp);

            // Build container spec with security hardening
            var secProfile = spec.securityProfile() != null ? spec.securityProfile() : SecurityProfile.DEFAULT;
            DockerOperations.HostConfigSpec hostConfig = securityPolicy.buildHostConfig(
                secProfile,
                spec.resourceLimits(),
                spec.networkPolicy()
            );
            Map<String, String> labels = securityPolicy.buildLabels(jobId);

            DockerOperations.ContainerSpec containerSpec = new DockerOperations.ContainerSpec(
                spec.image(),
                spec.command(),
                environment,
                networkId,
                CONTAINER_HOSTNAME,
                CONTAINER_USER,
                labels,
                hostConfig
            );

            containerId = containerManager.createContainer(containerSpec);
            activeContainers.put(jobId, containerId);
            MDC.put(MDC_CONTAINER_ID, containerId);
            log.info("Container created: containerId={}", containerId);

            // Check cancellation immediately after container registration —
            // if cancel() was called during createContainer(), the flag is set
            // but the container wasn't in activeContainers yet so cancel couldn't
            // stop it. Now we catch it before doing unnecessary file injection.
            checkCancelled(cancelled, jobId);

            // Inject input files via docker cp
            if (!spec.inputFiles().isEmpty()) {
                workspaceManager.injectFiles(containerId, spec.inputFiles());
                log.debug("Injected {} input files", spec.inputFiles().size());
            }

            // ── PHASE 2: EXECUTE ──
            containerManager.startContainer(containerId);
            log.info("Container started");

            Duration timeout = spec.resourceLimits().maxRuntime();
            SandboxContainerManager.WaitOutcome waitOutcome = containerManager.waitForCompletion(containerId, timeout);

            // Check cancellation after wait — cancel() stops the container, so
            // waitForCompletion returns with exit code 137. Without this check,
            // the caller would see a normal result instead of SandboxCancelledException.
            checkCancelled(cancelled, jobId);

            // ── PHASE 3: COLLECT ──
            // Collect output regardless of exit code or timeout — agent may have written partial results
            String outputPath = spec.outputPath() != null ? spec.outputPath() : OUTPUT_PATH_DEFAULT;
            Map<String, byte[]> outputFiles = workspaceManager.collectOutput(containerId, outputPath);

            // Capture logs before cleanup
            String logs = containerManager.getLogs(containerId, LOG_TAIL_LINES);

            if (waitOutcome.timedOut()) {
                executionsTimedOut.increment();
            } else {
                executionsSuccess.increment();
            }

            Duration duration = Duration.between(startTime, Instant.now());
            log.info(
                "Sandbox execution complete: exitCode={}, timedOut={}, outputFiles={}, duration={}",
                waitOutcome.exitCode(),
                waitOutcome.timedOut(),
                outputFiles.size(),
                duration
            );

            return new SandboxResult(waitOutcome.exitCode(), outputFiles, logs, waitOutcome.timedOut(), duration);
        } catch (SandboxCancelledException e) {
            executionsCancelled.increment();
            log.info("Sandbox execution cancelled");
            throw e;
        } catch (SandboxException e) {
            executionsFailed.increment();
            captureLogsOnError(containerId);
            log.error("Sandbox execution failed: error={}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            executionsFailed.increment();
            captureLogsOnError(containerId);
            log.error("Unexpected error during sandbox execution", e);
            throw new SandboxException("Sandbox execution failed for job: " + jobId, e);
        } finally {
            // ── PHASE 4: CLEANUP ──
            // Remove from activeContainers FIRST to prevent cancel() from calling
            // stopContainer() on a container that cleanup is about to force-remove.
            activeContainers.remove(jobId);
            executionDuration.record(Duration.between(startTime, Instant.now()));
            cleanup(jobId, containerId, networkId);
            cancellationFlags.remove(jobId);
            MDC.remove(MDC_JOB_ID);
            MDC.remove(MDC_CONTAINER_ID);
        }
    }

    @Override
    public void cancel(UUID jobId) {
        AtomicBoolean flag = cancellationFlags.get(jobId);
        if (flag != null) {
            flag.set(true);
            log.info("Cancellation requested: jobId={}", jobId);

            // Stop the running container so waitForCompletion returns immediately.
            // Use computeIfPresent to atomically read the containerId only if
            // the job is still active (avoids race with cleanup removing the entry).
            activeContainers.computeIfPresent(jobId, (id, containerId) -> {
                try {
                    containerManager.stopContainer(containerId);
                } catch (Exception e) {
                    log.warn("Failed to stop container for cancelled job: jobId={}, error={}", jobId, e.getMessage());
                }
                return containerId;
            });
        } else {
            log.debug("Cancel called for unknown/completed job: jobId={}", jobId);
        }
    }

    @Override
    public boolean isHealthy() {
        return containerManager.ping();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Map<String, String> buildEnvironment(SandboxSpec spec, String appServerIp) {
        Map<String, String> env = new HashMap<>();

        // Copy user-provided environment, filtering out blocked variables
        for (var entry : spec.environment().entrySet()) {
            if (isBlockedEnvVar(entry.getKey())) {
                log.warn("Blocked dangerous environment variable: {}", entry.getKey());
            } else {
                env.put(entry.getKey(), entry.getValue());
            }
        }

        // Inject LLM proxy configuration
        if (spec.networkPolicy() != null) {
            if (spec.networkPolicy().llmProxyUrl() != null) {
                // Resolve template placeholder or use as-is
                String proxyUrl = spec.networkPolicy().llmProxyUrl();
                if (proxyUrl.contains(PROXY_URL_PLACEHOLDER) && appServerIp != null) {
                    proxyUrl = proxyUrl.replace(PROXY_URL_PLACEHOLDER, appServerIp);
                }
                env.put("LLM_PROXY_URL", proxyUrl);
            } else if (appServerIp != null) {
                env.put("LLM_PROXY_URL", "http://" + appServerIp + ":" + properties.llmProxyPort());
            }

            if (spec.networkPolicy().llmProxyToken() != null) {
                env.put("LLM_PROXY_TOKEN", spec.networkPolicy().llmProxyToken());
            }
        }

        return env;
    }

    /**
     * Best-effort log capture on error paths — container is about to be removed by cleanup, so grab
     * logs while we can. Logs are emitted at WARN for post-mortem debugging.
     */
    private void captureLogsOnError(String containerId) {
        if (containerId == null) {
            return;
        }
        try {
            String logs = containerManager.getLogs(containerId, LOG_TAIL_LINES);
            if (logs != null && !logs.isEmpty()) {
                // Truncate to prevent log aggregator overflow from large container output
                String truncated =
                    logs.length() > MAX_LOG_EVENT_BYTES
                        ? logs.substring(0, MAX_LOG_EVENT_BYTES) +
                          "\n... [truncated, " +
                          logs.length() +
                          " bytes total]"
                        : logs;
                log.warn("Container logs before cleanup:\n{}", truncated);
            }
        } catch (Exception e) {
            log.debug("Could not capture container logs on error path: {}", e.getMessage());
        }
    }

    /**
     * Check if an environment variable name is blocked (exact match or prefix match).
     *
     * <p>Package-private for testing.
     */
    static boolean isBlockedEnvVar(String name) {
        if (BLOCKED_ENV_VARS.contains(name)) {
            return true;
        }
        for (String prefix : BLOCKED_ENV_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void checkCancelled(AtomicBoolean flag, UUID jobId) {
        if (flag.get()) {
            throw new SandboxCancelledException("Job cancelled: " + jobId);
        }
    }

    /**
     * Best-effort cleanup of all resources. Each step is independent — failures are logged but don't
     * prevent other cleanup steps.
     */
    private void cleanup(UUID jobId, String containerId, String networkId) {
        // 1. Remove container
        if (containerId != null) {
            suppressAndLog("remove container", jobId, () -> containerManager.forceRemove(containerId));
        }

        // 2. Disconnect app-server from job network
        if (networkId != null) {
            suppressAndLog("disconnect app-server", jobId, () -> networkManager.disconnectAppServer(networkId));
        }

        // 3. Remove job network
        if (networkId != null) {
            suppressAndLog("remove network", jobId, () -> networkManager.removeNetwork(networkId));
        }

        log.debug("Cleanup complete: jobId={}", jobId);
    }

    private void suppressAndLog(String operation, UUID jobId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            meterRegistry.counter("sandbox.cleanup.failures", "step", operation).increment();
            log.warn("Cleanup failed ({}): jobId={}, error={}", operation, jobId, e.getMessage());
        }
    }
}
