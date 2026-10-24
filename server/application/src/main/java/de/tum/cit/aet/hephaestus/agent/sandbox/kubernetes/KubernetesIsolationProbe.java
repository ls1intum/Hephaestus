package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves at the worker's start that the cluster enforces what the sandbox boundary needs and a Pod spec cannot state
 * (ADR 0041 § Verification gates). The worker starts no sandbox before the probe passed, and a probe that fails stops
 * the worker: an operator sees the reason in its log, and Kubernetes starts it again.
 *
 * <ul>
 *   <li><b>Restricted Pod Security.</b> The API server refuses, in a dry run, a Pod that the Baseline standard admits
 *       and the Restricted one does not.
 *   <li><b>Egress to the gateway only.</b> A Pod with a review sandbox's labels reaches this worker's gateway, which is
 *       the control, and none of: this worker's other port, the Kubernetes API, the cluster DNS server. A Pod of no
 *       sandbox kind, which the policies treat as a turn sandbox, does not reach the gateway either.
 *   <li><b>A process limit.</b> The review Pod cannot start {@link KubernetesSandboxProperties#podPidsLimit} processes.
 * </ul>
 *
 * <p>Both probe Pods have the sandbox's own configuration and image, and the probe deletes them when it ends. Each
 * prints one line of booleans and counts, which is all the worker reads of them.
 */
public class KubernetesIsolationProbe {

    private static final Logger log = LoggerFactory.getLogger(KubernetesIsolationProbe.class);

    /** Scheduling, the pull of the agent image and the script. */
    static final Duration PROBE_TIME = Duration.ofMinutes(5);

    static final String CLOSED_KIND = "isolation-probe";

    /**
     * The probe's script, which runs after the Pod's isolation init container. Argument: what to reach and how many
     * processes to start. A target counts as reached once a TCP connection opens; every target has a listener, so a
     * refused or silent one is a denied one. A target that must be reached gets ten seconds: the worker's ingress
     * policy can admit a new Pod a moment after the Pod's own policy is in force.
     */
    static final String SCRIPT = """
            const net = require('node:net');
            const { spawn } = require('node:child_process');
            const spec = JSON.parse(process.argv[1]);
            const reach = (host, port) => new Promise((done) => {
              const socket = net.connect({ host, port, timeout: 3000 });
              socket.on('connect', () => { socket.destroy(); done(true); });
              socket.on('timeout', () => { socket.destroy(); done(false); });
              socket.on('error', () => done(false));
            });
            (async () => {
              const out = { reached: {} };
              for (const target of spec.targets) {
                const until = Date.now() + (target.reachable ? 10000 : 0);
                let reached = await reach(target.host, target.port);
                while (!reached && Date.now() < until) {
                  await new Promise((wait) => setTimeout(wait, 250));
                  reached = await reach(target.host, target.port);
                }
                out.reached[target.name] = reached;
              }
              if (spec.processes > 0) {
                const children = [];
                out.spawnError = null;
                for (; children.length < spec.processes; ) {
                  try {
                    const child = spawn('sleep', ['60'], { stdio: 'ignore' });
                    await new Promise((started, failed) => { child.once('spawn', started); child.once('error', failed); });
                    children.push(child);
                  } catch (error) { out.spawnError = error.code ?? 'unknown'; break; }
                }
                out.spawned = children.length;
                for (const child of children) child.kill('SIGKILL');
              }
              console.log(JSON.stringify(out));
            })();
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final KubernetesClient client;
    private final KubernetesPods pods;
    private final KubernetesWorker worker;
    private final String image;
    private final int serverPort;

    public KubernetesIsolationProbe(
            KubernetesClient client, KubernetesPods pods, KubernetesWorker worker, String image, int serverPort) {
        this.client = client;
        this.pods = pods;
        this.worker = worker;
        this.image = image;
        this.serverPort = serverPort;
    }

    /** One address that a probe Pod tries, and whether it must reach it. */
    record Target(String name, String host, int port, boolean reachable) {}

    /**
     * Runs the probe and lets the sandboxes start.
     *
     * @throws IllegalStateException naming each control that failed
     */
    public void prove() {
        List<String> failures = new ArrayList<>();
        refusesUnrestrictedPods().ifPresent(failures::add);
        List<Target> denied = denied();
        List<Target> review = new ArrayList<>(denied);
        review.addFirst(new Target("gateway", worker.properties().podIp(), worker.gatewayPort(), true));
        List<Target> closed = List.of(new Target("gateway", worker.properties().podIp(), worker.gatewayPort(), false));
        int processes = worker.properties().podPidsLimit();
        CompletableFuture<List<String>> reviewRun =
                CompletableFuture.supplyAsync(() -> run(KubernetesWorker.KIND_REVIEW, review, processes));
        CompletableFuture<List<String>> closedRun = CompletableFuture.supplyAsync(() -> run(CLOSED_KIND, closed, 0));
        failures.addAll(reviewRun.join());
        failures.addAll(closedRun.join());
        if (!failures.isEmpty()) {
            String message = "The cluster does not hold the sandbox boundary, so this worker runs no sandbox: "
                    + String.join("; ", failures)
                    + ". docs/admin/kubernetes.mdx § Sandbox prerequisites";
            // The shared log keeps no exception message, so the operator's reason is logged on its own.
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info(
                "The sandbox isolation probe passed: Restricted Pod Security, gateway-only egress, at most {} processes"
                        + " per Pod",
                processes);
        worker.isolationProved();
    }

    /**
     * A Pod that meets the Baseline standard and not the Restricted one: it neither drops capabilities, nor sets a
     * seccomp profile, nor runs as non-root. A dry run creates nothing.
     */
    Optional<String> refusesUnrestrictedPods() {
        Pod baseline = new PodBuilder()
                .withNewMetadata()
                .withName("hephaestus-isolation-probe-" + UUID.randomUUID())
                .withNamespace(worker.namespace())
                .endMetadata()
                .withNewSpec()
                .withAutomountServiceAccountToken(false)
                .withContainers(new ContainerBuilder()
                        .withName("baseline")
                        .withImage(image)
                        .build())
                .endSpec()
                .build();
        try {
            client.pods()
                    .inNamespace(worker.namespace())
                    .resource(baseline)
                    .dryRun()
                    .create();
            return Optional.of("the sandbox namespace admits a Pod that the Restricted Pod Security Standard refuses");
        } catch (KubernetesClientException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (e.getCode() == 403 && message.contains("PodSecurity \"restricted")) {
                return Optional.empty();
            }
            return Optional.of("the dry run of an unrestricted Pod ended with HTTP " + e.getCode()
                    + ", not with a Restricted Pod Security refusal");
        }
    }

    /** What a sandbox must not reach: this worker's other port, the API server, the DNS server. */
    private List<Target> denied() {
        List<Target> targets = new ArrayList<>();
        targets.add(new Target("worker server port", worker.properties().podIp(), serverPort, false));
        URI api = URI.create(client.getMasterUrl().toString());
        int apiPort = api.getPort() > 0 ? api.getPort() : 443;
        address(api.getHost()).ifPresent(host -> targets.add(new Target("Kubernetes API", host, apiPort, false)));
        nameserver().ifPresent(host -> targets.add(new Target("DNS server", host, 53, false)));
        return targets;
    }

    /** A sandbox has no DNS, so it is given addresses. */
    private static Optional<String> address(@Nullable String host) {
        if (host == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(host).getHostAddress());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** This worker's first DNS server, as its Pod has it. */
    private static Optional<String> nameserver() {
        try {
            return Files.readAllLines(Path.of("/etc/resolv.conf")).stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith("nameserver "))
                    .map(line -> line.substring("nameserver ".length()).strip())
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Runs one probe Pod and returns what failed. */
    private List<String> run(String kind, List<Target> targets, int processes) {
        // A sandbox's own labels: the chart's gateway policy admits a review Pod by its kind and its worker Pod.
        Map<String, String> labels = new LinkedHashMap<>(worker.labels(kind));
        labels.put(KubernetesWorker.PROBE, "true");
        String name = "hephaestus-isolation-probe-" + kind + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        var spec = JSON.createObjectNode();
        var list = spec.putArray("targets");
        targets.forEach(target -> list.addObject()
                .put("name", target.name())
                .put("host", target.host())
                .put("port", target.port())
                .put("reachable", target.reachable()));
        spec.put("processes", processes);
        ResourceLimits limits = new ResourceLimits(
                512L * 1024 * 1024, 0.5, Math.max(processes, worker.properties().podPidsLimit()), PROBE_TIME);
        Pod pod = pods.probePod(
                new KubernetesPods.Workload(name, labels, image, "probe"),
                List.of("node", "-e", SCRIPT, spec.toString()),
                limits,
                PROBE_TIME.toSeconds());
        try {
            pods.create(pod);
            KubernetesPods.Waited ended = pods.waitFor(
                    name,
                    candidate -> KubernetesPods.terminal(candidate) || KubernetesPods.fatalWaiting(candidate) != null,
                    PROBE_TIME);
            Pod last = ended.pod();
            if (last != null && KubernetesPods.notIsolated(last)) {
                return List.of("the network policy did not filter the " + kind + " probe Pod within "
                        + KubernetesPods.ISOLATION_WAIT.toSeconds() + " seconds");
            }
            if (ended.timedOut() || last == null || KubernetesPods.exitCode(last, KubernetesPods.RUNTIME) == null) {
                String waiting = last == null ? null : KubernetesPods.fatalWaiting(last);
                return List.of("the " + kind + " probe Pod did not run" + (waiting == null ? "" : ": " + waiting));
            }
            return judge(kind, targets, processes, pods.logs(name, KubernetesPods.RUNTIME));
        } catch (RuntimeException e) {
            return List.of("the " + kind + " probe Pod failed: " + e.getMessage());
        } finally {
            try {
                pods.delete(name, 0);
            } catch (RuntimeException e) {
                log.warn(
                        "Could not delete isolation probe Pod {}: {}",
                        name,
                        e.getClass().getSimpleName());
            }
        }
    }

    /** What the probe's line says against what each target and the process limit require. */
    static List<String> judge(String kind, List<Target> targets, int processes, String output) {
        JsonNode result;
        try {
            result = JSON.readTree(
                    output.strip().lines().reduce((first, last) -> last).orElse(""));
        } catch (RuntimeException e) {
            return List.of("the " + kind + " probe Pod printed no result");
        }
        List<String> failures = new ArrayList<>();
        for (Target target : targets) {
            JsonNode reached = result.path("reached").path(target.name());
            if (!reached.isBoolean()) {
                failures.add("the " + kind + " probe did not try the " + target.name());
            } else if (reached.booleanValue() != target.reachable()) {
                failures.add("a " + kind + " Pod " + (target.reachable() ? "does not reach" : "reaches") + " the "
                        + target.name() + " (" + target.host() + ":" + target.port() + ")");
            }
        }
        if (processes > 0) {
            int spawned = result.path("spawned").asInt(Integer.MAX_VALUE);
            if (spawned >= processes
                    || !"EAGAIN".equals(result.path("spawnError").asString(""))) {
                failures.add("a " + kind + " Pod started " + spawned
                        + " processes; the nodes must bound a Pod to at most " + processes);
            }
        }
        return failures;
    }
}
