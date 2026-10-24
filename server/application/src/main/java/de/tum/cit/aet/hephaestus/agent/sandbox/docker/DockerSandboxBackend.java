package de.tum.cit.aet.hephaestus.agent.sandbox.docker;

import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxBackend;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sandboxes as Docker containers. Each sandbox gets a network of its own that only this worker joins, so the
 * worker's gateway is its one destination, and attempt volumes for {@code /workspace} ({@link DockerAttemptWorkspace}).
 * The initializer container fills them; the runtime container is created on the same volumes, with {@code /workspace}
 * read-only, once the initializer succeeded and the sandbox is next observed. {@link ContainerSecurityPolicy} owns
 * the hardening of both.
 *
 * <p>A worker outside any container joins no network. Its Heph host then reaches it through the host gateway, which
 * only a network with a route out reaches; a review never runs on such a network.
 */
public class DockerSandboxBackend implements SandboxBackend {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxBackend.class);

    private static final String CONTAINER_USER = "1000:1000";
    private static final List<String> INITIALIZER_COMMAND = List.of("node", "/opt/pi-sdk/gateway-init.ts");

    /** The exit a container reports when SIGKILL ended it, as Docker's wait reports it. */
    private static final int SIGKILL_EXIT_CODE = 137;

    /**
     * Docker's tail is applied by the daemon, before anything here sees a line, so a transcript asks for every line: a
     * tail would drop the beginning — what the run was asked and what it read. The collector bounds it instead.
     */
    private static final int WHOLE_TRANSCRIPT = 0;

    private static final String UNIDENTIFIED_CONTAINER =
            "This worker runs in a container that it cannot identify, so it cannot join the internal network of its"
                    + " Heph host. Keep the worker container's default hostname.";

    private final SandboxNetworkManager networkManager;
    private final SandboxContainerManager containerManager;
    private final ContainerSecurityPolicy securityPolicy;
    private final DockerVolumeOperations volumeOperations;
    private final SandboxCreator creator;
    private final String owner;
    private final int gatewayPort;
    private final MeterRegistry meterRegistry;
    private final boolean reviewsOnTheMachine;
    private final Map<String, Sandbox> sandboxes = new ConcurrentHashMap<>();

    public DockerSandboxBackend(
            SandboxNetworkManager networkManager,
            SandboxContainerManager containerManager,
            ContainerSecurityPolicy securityPolicy,
            DockerVolumeOperations volumeOperations,
            SandboxCreator creator,
            String owner,
            int gatewayPort,
            MeterRegistry meterRegistry) {
        this(
                networkManager,
                containerManager,
                securityPolicy,
                volumeOperations,
                creator,
                owner,
                gatewayPort,
                meterRegistry,
                false);
    }

    /**
     * @param reviewsOnTheMachine whether a review also gets the Heph host's route out when this process runs outside any
     *     container. Only the Docker live tests set it: their gateway runs in the test process on the machine itself.
     */
    DockerSandboxBackend(
            SandboxNetworkManager networkManager,
            SandboxContainerManager containerManager,
            ContainerSecurityPolicy securityPolicy,
            DockerVolumeOperations volumeOperations,
            SandboxCreator creator,
            String owner,
            int gatewayPort,
            MeterRegistry meterRegistry,
            boolean reviewsOnTheMachine) {
        this.reviewsOnTheMachine = reviewsOnTheMachine;
        this.networkManager = networkManager;
        this.containerManager = containerManager;
        this.securityPolicy = securityPolicy;
        this.volumeOperations = volumeOperations;
        this.creator = creator;
        this.owner = owner;
        this.gatewayPort = gatewayPort;
        this.meterRegistry = meterRegistry;
    }

    /** A launched sandbox: what it runs on, and its two containers. */
    private static final class Sandbox {
        private final String id;
        private final UUID owner;
        private final List<String> command;
        private @Nullable String networkId;
        private @Nullable DockerAttemptWorkspace workspace;
        private DockerOperations.@Nullable ContainerSpec template;
        private @Nullable String initializerId;
        private boolean initializerEnded;
        private String initializerTranscript = "";
        private @Nullable String runtimeId;
        private boolean killed;

        private Sandbox(String id, UUID owner, List<String> command) {
            this.id = id;
            this.owner = owner;
            this.command = command;
        }
    }

    @Override
    public Handle launch(Launch launch) {
        Sandbox sandbox = new Sandbox(UUID.randomUUID().toString(), launch.id(), launch.command());
        try {
            boolean host = launch.kind() == Kind.HOST;
            Map<String, String> labels = host ? hostLabels(launch.id()) : reviewLabels(launch);
            // A worker on the machine itself joins no network: only its Heph host gets the route out it needs.
            boolean outsideDocker = host
                    ? !labels.containsKey(SandboxLabels.CREATOR_CONTAINER)
                    : reviewsOnTheMachine && creator.outsideContainer();
            sandbox.networkId = networkManager.createJobNetwork(launch.id(), outsideDocker, labels);
            String workerAddress = networkManager.connectAppServer(sandbox.networkId);
            List<String> extraHosts = List.of();
            if (workerAddress == null) {
                // host.docker.internal is reachable only from a non-internal network.
                if (!outsideDocker) {
                    throw new SandboxException(
                            "Practice reviews run on an internal network. Only a worker that runs in a Docker "
                                    + "container with its default hostname can join this network.");
                }
                log.warn(
                        "This worker runs outside a container, so its host {} is on a network with internet access",
                        launch.id());
                workerAddress = "host.docker.internal";
                extraHosts = List.of("host.docker.internal:host-gateway");
            }
            sandbox.workspace = new DockerAttemptWorkspace(volumeOperations, UUID.fromString(sandbox.id), labels);
            Map<String, String> environment = launch.environment().at("http://" + workerAddress + ":" + gatewayPort);
            sandbox.template = new DockerOperations.ContainerSpec(
                    launch.image(),
                    List.of(),
                    environment,
                    sandbox.networkId,
                    host ? "host" : "agent",
                    CONTAINER_USER,
                    labels,
                    securityPolicy.buildHostConfig(SecurityProfile.DEFAULT, launch.limits(), outsideDocker),
                    extraHosts);
            sandboxes.put(sandbox.id, sandbox);
            synchronized (sandbox) {
                sandbox.initializerId = containerManager.createContainer(container(sandbox, INITIALIZER_COMMAND, true));
                containerManager.startContainer(sandbox.initializerId);
            }
            return new Handle(sandbox.id);
        } catch (RuntimeException e) {
            remove(sandbox);
            throw e;
        }
    }

    @Override
    public Observation observe(Handle handle, Stage stage, Instant deadline) {
        Sandbox sandbox = sandbox(handle);
        return switch (stage) {
            case INITIALIZED -> initialized(sandbox, deadline);
            case STARTED ->
                startRuntime(sandbox) ? new Observation(false, null) : new Observation(false, SIGKILL_EXIT_CODE);
            case ENDED -> {
                if (!startRuntime(sandbox)) {
                    yield new Observation(false, null);
                }
                var outcome = containerManager.waitForCompletion(requireRuntime(sandbox), until(deadline));
                yield new Observation(outcome.timedOut(), outcome.exitCode());
            }
        };
    }

    /** Waits for the initializer, keeps its transcript when it did not succeed, and removes it whatever happens. */
    private Observation initialized(Sandbox sandbox, Instant deadline) {
        String initializerId = sandbox.initializerId;
        if (initializerId == null) {
            throw new SandboxException("The sandbox has no initializer");
        }
        try {
            var outcome = containerManager.waitForCompletion(initializerId, until(deadline));
            if (outcome.timedOut() || outcome.exitCode() != 0) {
                sandbox.initializerTranscript = containerManager.getLogs(initializerId, WHOLE_TRANSCRIPT);
            }
            return new Observation(outcome.timedOut(), outcome.exitCode());
        } finally {
            synchronized (sandbox) {
                sandbox.initializerEnded = true;
            }
            try {
                containerManager.forceRemove(initializerId);
            } catch (RuntimeException exception) {
                log.warn(
                        "Could not remove workspace initializer {}; left for reconciliation", initializerId, exception);
            }
        }
    }

    /**
     * Creates and starts the runtime container unless it runs already. A killed sandbox starts none: the creation holds
     * the sandbox's lock, which {@link #kill} takes too.
     */
    private boolean startRuntime(Sandbox sandbox) {
        synchronized (sandbox) {
            if (sandbox.runtimeId != null) {
                return true;
            }
            if (sandbox.killed) {
                return false;
            }
            String runtimeId = containerManager.createContainer(container(sandbox, sandbox.command, false));
            sandbox.runtimeId = runtimeId;
            log.info("Container created: containerId={}", runtimeId);
            containerManager.startContainer(runtimeId);
            log.info("Container started");
            return true;
        }
    }

    @Override
    public boolean running(Handle handle) {
        String runtimeId = sandbox(handle).runtimeId;
        return runtimeId != null
                && containerManager.listManagedContainers().stream()
                        .anyMatch(container -> container.id().equals(runtimeId) && "running".equals(container.state()));
    }

    @Override
    public String transcript(Handle handle, Container container) {
        Sandbox sandbox = sandbox(handle);
        if (container == Container.INITIALIZER) {
            return sandbox.initializerTranscript;
        }
        String runtimeId = sandbox.runtimeId;
        return runtimeId == null ? "" : containerManager.getLogs(runtimeId, WHOLE_TRANSCRIPT);
    }

    /** Stops the container that runs, and keeps the runtime from starting. A failed stop throws; a retry stops again. */
    @Override
    public void kill(Handle handle) {
        Sandbox sandbox = sandboxes.get(handle.id());
        if (sandbox == null) {
            return;
        }
        synchronized (sandbox) {
            sandbox.killed = true;
            if (sandbox.runtimeId != null) {
                containerManager.stopContainer(sandbox.runtimeId);
            } else if (sandbox.initializerId != null && !sandbox.initializerEnded) {
                containerManager.stopContainer(sandbox.initializerId);
            }
        }
    }

    @Override
    public void remove(Handle handle) {
        Sandbox sandbox = sandboxes.get(handle.id());
        if (sandbox != null) {
            remove(sandbox);
        }
    }

    /** Each step is independent, so one that fails still leaves the others to run. */
    private void remove(Sandbox sandbox) {
        sandboxes.remove(sandbox.id);
        String runtimeId = sandbox.runtimeId;
        if (runtimeId != null) {
            suppressAndLog("remove container", sandbox, () -> containerManager.forceRemove(runtimeId));
        }
        String initializerId = sandbox.initializerId;
        if (initializerId != null && !sandbox.initializerEnded) {
            suppressAndLog("remove container", sandbox, () -> containerManager.forceRemove(initializerId));
        }
        String networkId = sandbox.networkId;
        if (networkId != null) {
            suppressAndLog("disconnect app-server", sandbox, () -> networkManager.disconnectAppServer(networkId));
            suppressAndLog("remove network", sandbox, () -> networkManager.removeNetwork(networkId));
        }
        DockerAttemptWorkspace workspace = sandbox.workspace;
        if (workspace != null) {
            suppressAndLog("remove attempt volumes", sandbox, workspace::close);
        }
        log.debug("Cleanup complete: sandbox={}", sandbox.id);
    }

    private Map<String, String> reviewLabels(Launch launch) {
        Integer attempt = launch.attempt();
        Map<String, String> labels = attempt == null
                ? securityPolicy.buildLabels(launch.id())
                : securityPolicy.buildLabels(launch.id(), attempt);
        if (attempt != null && attempt > 0) {
            removeSupersededAttempts(launch.id(), attempt, labels.get(SandboxLabels.OWNER));
        }
        return labels;
    }

    /**
     * The labels of a Heph host: its creator, so that cleanup can wait until that creator is gone. A worker in a
     * container that it cannot identify starts no host rather than give the host a route out.
     */
    private Map<String, String> hostLabels(UUID id) {
        Map<String, String> labels = new HashMap<>(creator.labels());
        if (labels.isEmpty() && !creator.outsideContainer()) {
            // Logged here, where the cause is known: callers log an exception's class only.
            log.warn(UNIDENTIFIED_CONTAINER);
            throw new SandboxException(UNIDENTIFIED_CONTAINER);
        }
        labels.put(SandboxLabels.OWNER, owner);
        labels.put(SandboxLabels.KIND, SandboxLabels.KIND_HEPH_HOST);
        labels.put(SandboxLabels.SESSION_ID, id.toString());
        return labels;
    }

    /**
     * Removes this job's containers from attempts the claim has superseded, so the job network they hold can be
     * recreated. Only a container with this installation's owner, this job and a lower recorded attempt is removed;
     * anything else stays, and the network guard refuses it as before. A failed removal fails this attempt.
     */
    private void removeSupersededAttempts(UUID jobId, int attempt, @Nullable String owner) {
        for (DockerOperations.ContainerInfo container : containerManager.listManagedContainers()) {
            Map<String, String> labels = container.labels();
            String recorded = labels.get(SandboxLabels.JOB_ATTEMPT);
            if (owner == null
                    || !owner.equals(labels.get(SandboxLabels.OWNER))
                    || !jobId.toString().equals(labels.get(SandboxLabels.JOB_ID))
                    || labels.containsKey(SandboxLabels.SESSION_ID)
                    || recorded == null
                    || !recorded.matches("[0-9]{1,10}")
                    || Long.parseLong(recorded) >= attempt) {
                continue;
            }
            log.info("Removing superseded sandbox container: containerId={}, attempt={}", container.id(), recorded);
            containerManager.forceRemove(container.id());
        }
    }

    private DockerOperations.ContainerSpec container(Sandbox sandbox, List<String> command, boolean initialization) {
        DockerOperations.ContainerSpec template = sandbox.template;
        DockerAttemptWorkspace workspace = sandbox.workspace;
        if (template == null || workspace == null) {
            throw new SandboxException("The sandbox is not prepared");
        }
        return new DockerOperations.ContainerSpec(
                template.image(),
                command,
                template.environment(),
                template.networkId(),
                template.hostname(),
                template.user(),
                template.labels(),
                workspace.configure(template.hostConfig(), initialization),
                template.extraHosts());
    }

    private Sandbox sandbox(Handle handle) {
        Sandbox sandbox = sandboxes.get(handle.id());
        if (sandbox == null) {
            throw new SandboxException("The sandbox is gone");
        }
        return sandbox;
    }

    private static String requireRuntime(Sandbox sandbox) {
        String runtimeId = sandbox.runtimeId;
        if (runtimeId == null) {
            throw new SandboxException("The sandbox has no runtime");
        }
        return runtimeId;
    }

    /** What is left until the deadline; none once it passed, so a wait times out at once. */
    private static Duration until(Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }

    private void suppressAndLog(String operation, Sandbox sandbox, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            meterRegistry
                    .counter(AgentMetrics.SANDBOX_CLEANUP_FAILURES, "step", operation)
                    .increment();
            log.warn(
                    "Cleanup failed ({}): jobId={}, errorType={}",
                    operation,
                    sandbox.owner,
                    e.getClass().getName());
        }
    }
}
