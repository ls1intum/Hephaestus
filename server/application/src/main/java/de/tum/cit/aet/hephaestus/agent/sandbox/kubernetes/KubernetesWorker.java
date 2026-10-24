package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This worker as its sandboxes record it: the labels of every sandbox it creates, the address of its gateway, and
 * whether the cluster passed the isolation probe.
 *
 * <p>The chart runs one worker, a StatefulSet of at most one replica, in an installation that owns its sandbox
 * namespace. Its next Pod starts only once the last one ended. So a sandbox that this process did not create belongs
 * to no running worker: its gateway session ended with the process that opened it. That is the whole custody rule; a
 * second worker needs one of its own (ADR 0041). None of it is the review ledger's worker id ({@code WorkerProperties}),
 * which stays per process.
 */
public class KubernetesWorker {

    public static final String PART_OF = "app.kubernetes.io/part-of";
    public static final String SANDBOX_KIND = "hephaestus.build/sandbox-kind";

    /** The worker Pod that created a sandbox: a restart of the worker container keeps it. */
    public static final String WORKER_POD = "hephaestus.build/worker-pod";

    /** The process that created a sandbox: a restart of the worker container is a new one. */
    public static final String WORKER_PROCESS = "hephaestus.build/worker-process";

    public static final String KIND_REVIEW = "review";
    public static final String KIND_HOST = "host";
    public static final String KIND_TURN = "turn";

    /** A Pod of the isolation probe: the chart's policies treat it as a review Pod or a turn Pod, by its kind. */
    public static final String PROBE = "hephaestus.build/isolation-probe";

    public static final String JOB_ID = "hephaestus.build/job-id";
    public static final String JOB_ATTEMPT = "hephaestus.build/job-attempt";
    public static final String SESSION_ID = "hephaestus.build/session-id";
    public static final String SANDBOX_ID = "hephaestus.build/sandbox-id";

    private final KubernetesSandboxProperties properties;
    private final int gatewayPort;
    private final String process = UUID.randomUUID().toString();
    private volatile boolean isolated;

    public KubernetesWorker(KubernetesSandboxProperties properties, int gatewayPort) {
        this.properties = properties;
        this.gatewayPort = gatewayPort;
    }

    public KubernetesSandboxProperties properties() {
        return properties;
    }

    public String namespace() {
        return properties.namespace();
    }

    /** The gateway as this worker's sandboxes reach it: the worker Pod's own address, never a Service. */
    public String gatewayUrl() {
        return "http://" + properties.gatewayHost() + ":" + gatewayPort;
    }

    /** The host part of {@link #gatewayUrl}, for a URL template's {@code {appServerIp}}. */
    public String gatewayHost() {
        return properties.gatewayHost();
    }

    public int gatewayPort() {
        return gatewayPort;
    }

    /** The labels of a new sandbox of the kind. */
    public Map<String, String> labels(String kind) {
        Map<String, String> labels = new HashMap<>(selector(kind));
        labels.put(WORKER_POD, properties.podName());
        labels.put(WORKER_PROCESS, process);
        return labels;
    }

    /** The labels that select the sandboxes of the kind. */
    public Map<String, String> selector(String kind) {
        Map<String, String> selector = new HashMap<>();
        selector.put(PART_OF, "hephaestus");
        selector.put(SANDBOX_KIND, kind);
        return selector;
    }

    /** Whether this process created the sandbox of these labels. */
    public boolean createdBySelf(Map<String, String> labels) {
        return process.equals(labels.get(WORKER_PROCESS));
    }

    /** Records that the cluster passed the isolation probe ({@link KubernetesIsolationProbe}). */
    void isolationProved() {
        isolated = true;
    }

    public boolean isolated() {
        return isolated;
    }

    /**
     * Refuses every sandbox until the isolation probe passed. The refusal is the runtime's, so the work is tried again
     * later; a probe that fails stops the worker.
     */
    public void requireIsolation() {
        if (!isolated) {
            throw new SandboxInfrastructureException("The cluster has not yet passed the sandbox isolation probe");
        }
    }
}
