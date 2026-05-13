package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.ContainerSecurityPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.DockerOperations;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxContainerManager;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxEnvBlocklist;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxLabels;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxNetworkManager;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * {@link InteractiveSandboxService} implementation backed by docker-java for container lifecycle
 * and a subprocess {@code docker exec -i} for stdin/stdout streaming. See {@code package-info} for
 * the rationale on the subprocess vs docker-java exec choice.
 */
public class DockerInteractiveSandboxAdapter implements InteractiveSandboxService {

    private static final Logger log = LoggerFactory.getLogger(DockerInteractiveSandboxAdapter.class);

    private static final String CONTAINER_USER = "1000:1000";
    private static final String CONTAINER_ROOT_USER = "0:0";
    private static final String CONTAINER_HOSTNAME = "mentor";
    private static final String PROXY_URL_PLACEHOLDER = "{appServerIp}";
    private static final String MDC_SESSION_ID = "mentor.sessionId";

    private static final List<String> SLEEPER_CMD = List.of("tail", "-f", "/dev/null");

    /**
     * Prep run as root: {@code mkdir /workspace/*} on images that ship no {@code WORKDIR} layer
     * (e.g. {@code node:22-slim}). Permissions use mode bits ({@code 1777} / {@code 1755}) rather
     * than ownership because the container's security policy drops {@code CAP_CHOWN}, so even
     * uid 0 inside the namespace cannot {@code chown}. Tar injection (next phase) preserves its
     * own 1000:1000 ownership for each entry.
     */
    private static final String PREP_MKDIR_CMD =
        "mkdir -p /workspace/.runner /workspace/context/target /workspace/context/user /workspace/scratch && " +
        "chmod 1777 /workspace /workspace/.runner /workspace/context/user /workspace/scratch && " +
        "chmod 1755 /workspace/context /workspace/context/target";

    /** Post-injection chmod on the RO side of context. Per-dir rather than {@code -R} so {@code context/user} stays writable. */
    private static final String PREP_CHMOD_CMD =
        "chmod -R a-w /workspace/context/target 2>/dev/null || true; " +
        "chmod a-w /workspace/context 2>/dev/null || true";

    /** Truncation cap for {@code docker exec} stderr surfaced via {@code InteractiveSandboxException}. */
    private static final int PREP_OUTPUT_PREVIEW_CAP = 512;

    /**
     * Upper bound on a single workspace-prep {@code docker exec}. Stops a hung Docker daemon from
     * pinning every attach() caller indefinitely. mkdir + chmod is fast in healthy steady state.
     */
    private static final Duration PREP_EXEC_TIMEOUT = Duration.ofSeconds(30);

    private final InteractiveSandboxProperties properties;
    private final SandboxProperties sandboxProperties;
    private final SandboxNetworkManager networkManager;
    private final SandboxWorkspaceManager workspaceManager;
    private final SandboxContainerManager containerManager;
    private final ContainerSecurityPolicy securityPolicy;
    private final InteractiveSandboxRegistry registry;
    private final InteractiveSandboxMetrics metrics;
    private final ObjectMapper mapper;
    private final String dockerCli;
    private final int serverPort;
    private final Executor closeExecutor;

    public DockerInteractiveSandboxAdapter(
        InteractiveSandboxProperties properties,
        SandboxProperties sandboxProperties,
        SandboxNetworkManager networkManager,
        SandboxWorkspaceManager workspaceManager,
        SandboxContainerManager containerManager,
        ContainerSecurityPolicy securityPolicy,
        InteractiveSandboxRegistry registry,
        InteractiveSandboxMetrics metrics,
        ObjectMapper mapper,
        Executor closeExecutor,
        String dockerCli,
        int serverPort
    ) {
        this.properties = properties;
        this.sandboxProperties = sandboxProperties;
        this.networkManager = networkManager;
        this.workspaceManager = workspaceManager;
        this.containerManager = containerManager;
        this.securityPolicy = securityPolicy;
        this.registry = registry;
        this.metrics = metrics;
        this.mapper = mapper;
        this.closeExecutor = closeExecutor;
        this.dockerCli = dockerCli;
        this.serverPort = serverPort;
    }

    @Override
    public AttachedSandbox attach(InteractiveSandboxSpec spec) {
        if (!properties.enabled()) {
            metrics.attachFailureOther.increment();
            throw new InteractiveSandboxException(
                "hephaestus.mentor.enabled=false — interactive sandbox is dark-launched"
            );
        }

        // Share-semantics fast path: an existing alive session for this (userId, workspaceId) wins.
        // The registry's tryRegister CAS below is the authoritative race resolver — this check is
        // an optimisation that avoids spawning a container we'd immediately throw away.
        DockerAttachedSandboxAdapter existing = registry.findLive(spec.userId(), spec.workspaceId());
        if (existing != null) {
            log.debug("attach() returning existing sandbox: sessionId={}", existing.sessionId());
            return existing;
        }
        log.debug(
            "attach() spawning new sandbox: userId={}, workspaceId={}",
            LogSafe.sanitise(spec.userId()),
            LogSafe.sanitise(spec.workspaceId())
        );

        MDC.put(MDC_SESSION_ID, spec.sessionId().toString());
        Timer.Sample sample = Timer.start();
        String networkId = null;
        String containerId = null;
        PiProcessHandle process = null;
        DockerAttachedSandboxAdapter sandbox = null;
        boolean registered = false;
        try {
            // ── 1. NETWORK ──
            boolean allowInternet = spec.networkPolicy() != null && spec.networkPolicy().internetAccess();
            networkId = networkManager.createJobNetwork(spec.sessionId(), allowInternet);
            String appServerIp = networkManager.connectAppServer(networkId);
            List<String> extraHosts = List.of();
            if (appServerIp == null) {
                if (!allowInternet) {
                    throw new InteractiveSandboxException(
                        "App-server is not in Docker and network is internal. Set allow_internet=true."
                    );
                }
                appServerIp = "host.docker.internal";
                extraHosts = List.of("host.docker.internal:host-gateway");
            }

            // ── 2. HOST CONFIG ── (same hardening floor as sync)
            SecurityProfile secProfile =
                spec.securityProfile() != null ? spec.securityProfile() : SecurityProfile.DEFAULT;
            DockerOperations.HostConfigSpec hostConfig = securityPolicy.buildHostConfig(
                secProfile,
                spec.resourceLimits(),
                spec.networkPolicy()
            );
            Map<String, String> labels = Map.of(
                SandboxLabels.MANAGED,
                "true",
                SandboxLabels.KIND,
                SandboxLabels.KIND_INTERACTIVE,
                SandboxLabels.SESSION_ID,
                spec.sessionId().toString()
            );

            // ── 3. ENVIRONMENT ── (filter via blocklist; substitute LLM-proxy placeholder)
            Map<String, String> runnerEnv = buildRunnerEnvironment(spec, appServerIp);

            // ── 4. CREATE + START ──
            DockerOperations.ContainerSpec containerSpec = new DockerOperations.ContainerSpec(
                spec.image(),
                SLEEPER_CMD,
                Map.of(),
                networkId,
                CONTAINER_HOSTNAME,
                CONTAINER_USER,
                labels,
                hostConfig,
                extraHosts
            );
            try {
                containerId = containerManager.createContainer(containerSpec);
            } catch (Exception e) {
                metrics.attachFailureImage.increment();
                throw new InteractiveSandboxException("createContainer failed: " + e.getMessage(), e);
            }
            log.info("Mentor container created: containerId={}, image={}", containerId, LogSafe.sanitise(spec.image()));

            try {
                containerManager.startContainer(containerId);
            } catch (Exception e) {
                metrics.attachFailureStart.increment();
                throw new InteractiveSandboxException("startContainer failed: " + e.getMessage(), e);
            }

            // ── 5. WORKSPACE PREP ── (root for mkdir on imageless /workspace; mode-bit perms because CAP_CHOWN is dropped)
            runExec(containerId, CONTAINER_ROOT_USER, PREP_MKDIR_CMD, "workspace mkdir");

            // ── 6. INJECT FILES / DIRS ── (tar entries carry 1000:1000 ownership)
            if (!spec.inputFiles().isEmpty()) {
                workspaceManager.injectFiles(containerId, spec.inputFiles());
            }
            if (!spec.volumeMounts().isEmpty()) {
                workspaceManager.injectDirectories(containerId, spec.volumeMounts());
            }

            // ── 7. ENFORCE RO ON context/target ──
            runExec(containerId, CONTAINER_USER, PREP_CHMOD_CMD, "workspace chmod");

            // ── 8. EXEC THE RUNNER ── (explicit -u 1000:1000 to harden against future USER drift)
            try {
                process = PiProcessHandle.spawn(dockerCli, containerId, CONTAINER_USER, spec.command(), runnerEnv);
            } catch (InteractiveSandboxException e) {
                metrics.attachFailureStdin.increment();
                throw e;
            }

            // ── 9. CONSTRUCT SANDBOX (off-registry; capacity check + register happens AFTER first frame) ──
            sandbox = buildSandbox(spec, containerId, networkId, process);
            sandbox.start();

            // ── 10. AWAIT FIRST FRAME ── (still off-registry — a stillborn runner never becomes user-visible)
            Duration firstFrameTimeout = Duration.ofSeconds(properties.attachFirstFrameTimeoutSeconds());
            try {
                if (!sandbox.awaitFirstFrame(firstFrameTimeout)) {
                    metrics.attachFailureFirstFrameTimeout.increment();
                    throw new InteractiveSandboxException(
                        "Runner did not emit first frame within " + firstFrameTimeout.toSeconds() + "s"
                    );
                }
            } catch (InteractiveSandboxException terminated) {
                // The pump or writer terminated the session before any frame (broken pipe, runner
                // crash, daemon death). Charge a distinct reason — not first_frame_timeout — so
                // operators can tell flow-control issues from runner-side regressions.
                metrics.attachFailureFirstFrameFailed.increment();
                throw terminated;
            }

            // ── 11. REGISTER ── (atomic capacity check + slot claim)
            InteractiveSandboxRegistry.RegistrationOutcome outcome = registry.tryRegister(sandbox);
            switch (outcome) {
                case DUPLICATE -> {
                    // A concurrent attach() raced and won — return their sandbox, tear ours down.
                    log.debug("Concurrent attach lost the race; returning existing sandbox");
                    DockerAttachedSandboxAdapter winner = registry.findLive(spec.userId(), spec.workspaceId());
                    if (winner != null) {
                        return winner;
                    }
                    throw new InteractiveSandboxException("Race lost but no winner found in registry");
                }
                case MAX_SESSIONS_PER_USER, MAX_SESSIONS_TOTAL -> {
                    metrics.attachFailureMaxSessions.increment();
                    throw new InteractiveSandboxException(
                        outcome == InteractiveSandboxRegistry.RegistrationOutcome.MAX_SESSIONS_PER_USER
                            ? "Per-user session cap exceeded"
                            : "Per-replica session cap exceeded"
                    );
                }
                case REGISTERED -> registered = true;
            }

            sample.stop(metrics.attachDuration);
            log.info("Mentor attached: sessionId={}", spec.sessionId());
            return sandbox;
        } catch (InteractiveSandboxException e) {
            if (!registered) {
                tearDownPartial(sandbox, process, networkId, containerId);
            }
            throw e;
        } catch (Exception e) {
            metrics.attachFailureOther.increment();
            if (!registered) {
                tearDownPartial(sandbox, process, networkId, containerId);
            }
            throw new InteractiveSandboxException("attach() failed: " + e.getMessage(), e);
        } finally {
            MDC.remove(MDC_SESSION_ID);
        }
    }

    @Override
    public boolean isHealthy() {
        return containerManager.ping();
    }

    private Map<String, String> buildRunnerEnvironment(InteractiveSandboxSpec spec, String appServerIp) {
        Map<String, String> env = new HashMap<>();
        for (var entry : spec.environment().entrySet()) {
            if (SandboxEnvBlocklist.isBlocked(entry.getKey())) {
                log.warn("Blocked dangerous environment variable: {}", entry.getKey());
                continue;
            }
            env.put(entry.getKey(), entry.getValue());
        }
        if (spec.networkPolicy() != null && spec.networkPolicy().llmProxyUrl() != null) {
            String url = spec.networkPolicy().llmProxyUrl();
            if (url.contains(PROXY_URL_PLACEHOLDER)) {
                url = url.replace(PROXY_URL_PLACEHOLDER, appServerIp);
            }
            env.put("LLM_PROXY_URL", url);
        } else if (spec.networkPolicy() != null && appServerIp != null) {
            String proxyBase =
                "http://" + appServerIp + ":" + sandboxProperties.resolvedLlmProxyPort(serverPort) + "/internal/llm";
            String path = spec.networkPolicy().llmProxyProviderPath();
            env.put("LLM_PROXY_URL", path != null ? proxyBase + "/" + path : proxyBase);
        }
        if (spec.networkPolicy() != null && spec.networkPolicy().llmProxyToken() != null) {
            env.put("LLM_PROXY_TOKEN", spec.networkPolicy().llmProxyToken());
        }
        return env;
    }

    private DockerAttachedSandboxAdapter buildSandbox(
        InteractiveSandboxSpec spec,
        String containerId,
        String networkId,
        PiProcessHandle process
    ) {
        FrameRingBuffer ring = new FrameRingBuffer(properties.ringBufferFrames(), metrics.ringBufferDropped);
        DockerAttachedSandboxAdapter.LifecycleOps lifecycleOps = new DockerAttachedSandboxAdapter.LifecycleOps() {
            @Override
            public void stopContainer(String cid, int graceSeconds) {
                containerManager.stopContainer(cid, graceSeconds);
            }

            @Override
            public void removeContainer(String cid) {
                containerManager.forceRemove(cid);
            }

            @Override
            public void disconnectAndRemoveNetwork(String nid) {
                try {
                    networkManager.disconnectAppServer(nid);
                } catch (Exception ignored) {}
                networkManager.removeNetwork(nid);
            }
        };
        return new DockerAttachedSandboxAdapter(
            spec.sessionId(),
            spec.userId(),
            spec.workspaceId(),
            containerId,
            networkId,
            process,
            mapper,
            ring,
            properties.subscriberQueueCapacity(),
            properties.stdinWriteTimeoutMs(),
            properties.sendQueueCapacity(),
            properties.maxFrameChars(),
            Duration.ofSeconds(properties.graceTimeoutSeconds()),
            metrics,
            lifecycleOps,
            closeExecutor,
            registry::onSandboxClosed
        );
    }

    /**
     * Synchronous {@code docker exec -u USER CONTAINER sh -c SCRIPT}, bounded by
     * {@link #PREP_EXEC_TIMEOUT}. Output is drained on a background thread to keep the pipe
     * from filling while we wait. Throws if the exec returns non-zero, times out, or fails to
     * start so callers see a typed failure.
     */
    private void runExec(String containerId, String user, String script, String description) {
        ProcessBuilder pb = new ProcessBuilder(dockerCli, "exec", "-u", user, containerId, "sh", "-c", script);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            metrics.attachFailureOther.increment();
            throw new InteractiveSandboxException(description + " failed: " + e.getMessage(), e);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        Thread drainer = Thread.ofVirtual().start(() -> {
            try (var in = p.getInputStream()) {
                in.transferTo(out);
            } catch (IOException ignored) {
                // Stream closed because process was destroyed — fine.
            }
        });
        try {
            boolean exited = p.waitFor(PREP_EXEC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!exited) {
                p.destroyForcibly();
                drainer.join(500);
                metrics.attachFailureOther.increment();
                throw new InteractiveSandboxException(
                    description + " timed out after " + PREP_EXEC_TIMEOUT.toSeconds() + "s"
                );
            }
            drainer.join(500);
            int exit = p.exitValue();
            if (exit != 0) {
                String preview = out.toString(StandardCharsets.UTF_8);
                if (preview.length() > PREP_OUTPUT_PREVIEW_CAP) {
                    preview = preview.substring(0, PREP_OUTPUT_PREVIEW_CAP) + "…";
                }
                metrics.attachFailureOther.increment();
                throw new InteractiveSandboxException(description + " failed: exit=" + exit + ", output=" + preview);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            metrics.attachFailureOther.increment();
            throw new InteractiveSandboxException(description + " failed: interrupted", ie);
        }
    }

    private void tearDownPartial(
        DockerAttachedSandboxAdapter sandbox,
        PiProcessHandle process,
        String networkId,
        String containerId
    ) {
        // If we built the sandbox but haven't registered, the pump/writer/dispatcher threads
        // may already be running. terminate() drives the full close path, which closes them.
        if (sandbox != null) {
            sandbox.terminate(EvictionReason.ERROR);
            sandbox.awaitClosed(Duration.ofSeconds(properties.graceTimeoutSeconds() + 5L));
            return;
        }
        if (process != null) {
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {}
        }
        if (containerId != null) {
            try {
                containerManager.forceRemove(containerId);
            } catch (Exception e) {
                log.warn("Partial cleanup: forceRemove failed for {}: {}", containerId, e.getMessage());
            }
        }
        if (networkId != null) {
            try {
                networkManager.disconnectAppServer(networkId);
            } catch (Exception ignored) {}
            try {
                networkManager.removeNetwork(networkId);
            } catch (Exception e) {
                log.warn("Partial cleanup: removeNetwork failed for {}: {}", networkId, e.getMessage());
            }
        }
    }
}
