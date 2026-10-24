package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewaySessions;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.HostSandbox;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a {@link HostSandbox} host in a Kubernetes Pod of the sandbox namespace, on the same Pod shape a practice review
 * uses: its workspace filled from its files through a host gateway session, then its process with {@code /workspace}
 * read-only. The chart's policy lets the host reach this worker's gateway and nothing else. The Pod runs until it is
 * removed; {@link KubernetesSandboxReconciler} removes a host that an earlier worker process left.
 */
public class KubernetesHostSandbox implements HostSandbox {

    private static final Logger log = LoggerFactory.getLogger(KubernetesHostSandbox.class);

    private static final String HOSTNAME = "host";

    private final KubernetesPods pods;
    private final KubernetesWorker worker;
    private final SandboxWorkspaceManager workspaces;
    private final SandboxGatewaySessions gatewaySessions;
    private final String image;
    private final Map<UUID, Resources> running = new ConcurrentHashMap<>();

    public KubernetesHostSandbox(
            KubernetesPods pods,
            KubernetesWorker worker,
            SandboxWorkspaceManager workspaces,
            SandboxGatewaySessions gatewaySessions,
            String image) {
        this.pods = pods;
        this.worker = worker;
        this.workspaces = workspaces;
        this.gatewaySessions = gatewaySessions;
        this.image = image;
    }

    /** What a host runs on, for its removal. */
    private record Resources(@Nullable String podName, SandboxGatewaySessions.@Nullable Session session) {}

    @Override
    public Host launch(HostSpec spec) throws IOException {
        UUID id = UUID.randomUUID();
        String token = ReviewJob.generateJobToken();
        String name = "hephaestus-host-" + id;
        String podName = null;
        SandboxGatewaySessions.Session session = null;
        try {
            worker.requireIsolation();
            pods.requireLimits(spec.limits());
            Map<String, String> labels = worker.labels(KubernetesWorker.KIND_HOST);
            labels.put(KubernetesWorker.SESSION_ID, id.toString());
            Path inputTar = workspaces.createInputTar(spec.files(), Map.of());
            session = KubernetesSandboxAdapter.openSession(
                    inputTar, () -> gatewaySessions.registerHost(id, token, inputTar, "out"));
            Map<String, String> environment = new LinkedHashMap<>(spec.environment());
            environment.put("LLM_PROXY_URL", worker.gatewayUrl() + "/internal/llm");
            environment.put("SANDBOX_RUNTIME_URL", KubernetesSandboxAdapter.runtimeUrl(worker, session));
            environment.put("LLM_PROXY_TOKEN", token);
            Pod pod = pods.workspacePod(
                    new KubernetesPods.Workload(name, labels, image, HOSTNAME),
                    environment,
                    spec.command(),
                    spec.limits(),
                    SecurityProfile.DEFAULT);
            pods.create(pod);
            podName = name;

            // The limits' maxRuntime bounds only the workspace's filling and the start of the host's process.
            Instant deadline = Instant.now().plus(spec.limits().maxRuntime());
            KubernetesPods.Waited initialized =
                    pods.waitFor(name, KubernetesPods::initializationEnded, remaining(deadline));
            Pod afterInit = initialized.pod();
            if (afterInit != null && KubernetesPods.notIsolated(afterInit)) {
                throw new IOException("The network policy did not filter the Pod of host " + id + " in time");
            }
            Integer exit = afterInit == null ? null : KubernetesPods.exitCode(afterInit, KubernetesPods.INITIALIZER);
            if (initialized.timedOut() || exit == null || exit != 0) {
                log.warn(
                        "The workspace of host {} could not be initialized: exitCode={}, timedOut={}",
                        id,
                        exit,
                        initialized.timedOut());
                throw new IOException("The workspace of host " + id + " could not be initialized (exit " + exit
                        + (initialized.timedOut() ? ", timed out" : "") + ")");
            }
            KubernetesPods.Waited started = pods.waitFor(
                    name,
                    candidate -> KubernetesPods.running(candidate, KubernetesPods.RUNTIME)
                            || KubernetesPods.runtimeEnded(candidate),
                    remaining(deadline));
            Pod afterStart = started.pod();
            if (started.timedOut()
                    || afterStart == null
                    || !KubernetesPods.running(afterStart, KubernetesPods.RUNTIME)) {
                throw new IOException("The process of host " + id + " did not start");
            }
            running.put(id, new Resources(podName, session));
            log.info("Started host {}", id);
            return new Host(id);
        } catch (IOException | RuntimeException e) {
            remove(id, new Resources(podName, session));
            throw e;
        }
    }

    @Override
    public boolean isRunning(Host host) {
        Resources resources = running.get(host.id());
        String podName = resources == null ? null : resources.podName();
        if (podName == null) {
            return false;
        }
        Pod pod = pods.get(podName);
        return pod != null
                && pod.getMetadata().getDeletionTimestamp() == null
                && KubernetesPods.running(pod, KubernetesPods.RUNTIME);
    }

    @Override
    public void remove(Host host) {
        Resources resources = running.remove(host.id());
        if (resources != null) {
            remove(host.id(), resources);
        }
    }

    /**
     * Removes what the host runs on. The host's output is not read: anything in the host can write to it, so it can
     * hold the conversation, a provider's answer or a credential. What the worker logs of a host is its id and the
     * class of a failed removal step.
     */
    private void remove(UUID id, Resources resources) {
        log.info("Removing host {}", id);
        String podName = resources.podName();
        if (podName != null) {
            try {
                pods.delete(podName, 0);
            } catch (SandboxException e) {
                log.warn(
                        "Could not remove the Pod of host {}: {}",
                        id,
                        e.getClass().getSimpleName());
            }
        }
        SandboxGatewaySessions.Session session = resources.session();
        if (session != null) {
            try {
                session.close();
            } catch (IOException | RuntimeException e) {
                log.warn(
                        "Could not close the gateway session of host {}: {}",
                        id,
                        e.getClass().getSimpleName());
            }
        }
    }

    private static Duration remaining(Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
