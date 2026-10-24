package de.tum.cit.aet.hephaestus.agent.sandbox.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewaySessions;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ExecSandboxes;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxCancelledException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxInfrastructureException;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxSpec;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.http.MockResponse;
import io.fabric8.mockwebserver.http.RecordedRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Kubernetes runtime against the client's CRUD mock API server: the Pods it writes, what it removes and keeps,
 * and how a review attempt ends. The mock runs no container, so what the Pods enforce on a node (the RuntimeClass,
 * seccomp, the read-only mounts, the PID namespace, the chart's NetworkPolicies) is proved on a live cluster only.
 */
@Tag("unit")
class KubernetesSandboxRuntimeTest {

    private static final String SANDBOXES = "hephaestus-sandboxes";
    private static final String IMAGE = "ghcr.io/example/agent@sha256:" + "a".repeat(64);
    private static final ResourceLimits LIMITS =
            new ResourceLimits(1024L * 1024 * 1024, 1.0, 256, Duration.ofMinutes(5));

    private @Nullable KubernetesMockServer server;
    private @Nullable KubernetesClient client;

    /**
     * The CRUD mock keeps no history of resource versions: a watch gets the objects that exist when it registers and
     * the events after that, not the events since the list that it follows. A Pod deleted between a wait's list and
     * its watch therefore never ends that wait. A test that deletes a Pod under a wait first waits for this latch,
     * which counts down once the mock has registered the watch of {@link #watchedPod}.
     */
    private final CountDownLatch watching = new CountDownLatch(1);

    private final AtomicReference<@Nullable String> watchedPod = new AtomicReference<>();

    private final ExecutorService background = Executors.newCachedThreadPool();
    private final SandboxGatewaySessions sessions = new SandboxGatewaySessions();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private String workerName;
    private KubernetesSandboxProperties properties;
    private KubernetesWorker worker;
    private KubernetesPods pods;
    private KubernetesReviewJobs reviewJobs;

    private KubernetesClient api() {
        return Objects.requireNonNull(client, "setUp creates the client");
    }

    private KubernetesMockServer mockServer() {
        return Objects.requireNonNull(server, "setUp starts the server");
    }

    @BeforeEach
    void setUp() {
        Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
        var dispatcher = new KubernetesMixedDispatcher(responses) {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                // The CRUD dispatcher registers a watch's listener before it returns the watch's response.
                MockResponse response = super.dispatch(request);
                String pod = watchedPod.get();
                String path = request.getPath();
                if (pod != null
                        && path.contains("watch=true")
                        && path.contains("/namespaces/" + SANDBOXES + "/pods")
                        && path.contains(pod)) {
                    watching.countDown();
                }
                return response;
            }
        };
        KubernetesMockServer started =
                new KubernetesMockServer(new Context(), new MockWebServer(), responses, dispatcher, true);
        server = started;
        started.init();
        client = started.createClient();

        workerName = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        properties = new KubernetesSandboxProperties(
                SANDBOXES, workerName, "fd00::7", 256, "gvisor", "hephaestus/agent-seccomp-profile.json");
        worker = new KubernetesWorker(properties, 8081);
        worker.isolationProved();
        pods = new KubernetesPods(api(), worker, 10, ImagePullPolicy.IF_NOT_PRESENT);
        reviewJobs = new KubernetesReviewJobs(api(), worker);
    }

    @AfterEach
    void tearDown() {
        background.shutdownNow();
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.destroy();
            }
        }
    }

    @Test
    void shouldBuildReviewPodWithRestrictedContainersAndReadOnlyRuntimeWorkspace() {
        Pod pod = pods.workspacePod(
                new KubernetesPods.Workload("review", Map.of(), IMAGE, "agent"),
                Map.of("B", "2", "A", "1"),
                List.of("node", "run.ts"),
                LIMITS,
                SecurityProfile.DEFAULT);

        var spec = pod.getSpec();
        assertThat(spec.getRestartPolicy()).isEqualTo("Never");
        assertThat(spec.getAutomountServiceAccountToken()).isFalse();
        assertThat(spec.getEnableServiceLinks()).isFalse();
        // Unset is the API's false.
        assertThat(spec.getHostNetwork()).isNotEqualTo(true);
        assertThat(spec.getHostPID()).isNotEqualTo(true);
        assertThat(spec.getRuntimeClassName()).isEqualTo("gvisor");
        assertThat(spec.getActiveDeadlineSeconds())
                .as("the review's Job bounds it")
                .isNull();
        assertThat(spec.getDnsPolicy()).isEqualTo("None");
        assertThat(spec.getSecurityContext().getRunAsNonRoot()).isTrue();
        assertThat(spec.getSecurityContext().getSeccompProfile().getType()).isEqualTo("Localhost");
        assertThat(spec.getSecurityContext().getSeccompProfile().getLocalhostProfile())
                .isEqualTo("hephaestus/agent-seccomp-profile.json");

        assertThat(spec.getInitContainers())
                .extracting(Container::getName)
                .as("the isolation gate runs before the workspace is filled")
                .containsExactly(KubernetesPods.ISOLATION, KubernetesPods.INITIALIZER);
        Container initializer = spec.getInitContainers().get(1);
        Container runtime = spec.getContainers().getFirst();
        for (Container container : List.of(initializer, runtime)) {
            assertRestricted(container, KubernetesPods.SANDBOX_UID);
            Quantity memory = container.getResources().getLimits().get("memory");
            assertThat(memory).isNotNull();
            assertThat(memory.getNumericalAmount()).isEqualByComparingTo(Long.toString(LIMITS.memoryBytes()));
            assertThat(container.getEnv()).extracting("name").containsExactly("A", "B");
            assertThat(container.getCommand()).startsWith(KubernetesPods.RLIMITS.toArray(String[]::new));
        }
        assertThat(initializer.getCommand()).endsWith(KubernetesPods.INITIALIZER_COMMAND.toArray(String[]::new));
        assertThat(runtime.getCommand()).containsExactly(limitedArgv("node", "run.ts"));
        assertThat(readOnly(initializer, "/workspace")).isFalse();
        assertThat(readOnly(runtime, "/workspace")).isTrue();
        assertThat(readOnly(runtime, "/workspace/out")).isFalse();
        assertThat(readOnly(runtime, "/tmp")).isFalse();
        assertThat(spec.getVolumes())
                .filteredOn(volume -> volume.getEmptyDir() != null
                        && "Memory".equals(volume.getEmptyDir().getMedium()))
                .hasSize(3);
    }

    @Test
    void shouldGiveTurnSandboxAReadOnlyWorkspaceAndTheStagerAUserOfItsOwn() {
        Pod pod = pods.turnPod(new KubernetesPods.Workload("turn", Map.of(), IMAGE, "sandbox"), LIMITS);

        assertThat(pod.getSpec().getShareProcessNamespace()).isNotEqualTo(true);
        assertThat(pod.getSpec().getInitContainers())
                .extracting(Container::getName)
                .as("no staging or command reaches a sandbox before its isolation gate passed")
                .containsExactly(KubernetesPods.ISOLATION);
        assertRestricted(pod.getSpec().getInitContainers().getFirst(), KubernetesPods.SANDBOX_UID);
        Container sandbox = container(pod, KubernetesPods.SANDBOX);
        Container stager = container(pod, KubernetesPods.STAGER);
        assertRestricted(sandbox, KubernetesPods.SANDBOX_UID);
        assertRestricted(stager, KubernetesPods.STAGER_UID);
        assertThat(KubernetesPods.STAGER_UID).isNotEqualTo(KubernetesPods.SANDBOX_UID);
        assertThat(readOnly(sandbox, "/workspace")).isTrue();
        assertThat(readOnly(stager, "/workspace")).isFalse();
        assertThat(sandbox.getCommand())
                .containsExactly(limitedArgv(KubernetesPods.SANDBOX_INIT.toArray(String[]::new)));
        assertThat(stager.getCommand()).containsExactly(limitedArgv("sleep", "infinity"));
        assertThat(pod.getSpec().getSecurityContext().getFsGroup()).isEqualTo(KubernetesPods.STAGER_UID);
    }

    @Test
    void shouldStartEveryCommandThroughTheRlimitsWithItsArgumentsUnchanged() {
        List<String> hostile = List.of("sh", "-c", "ulimit -Hn 65536; echo $HOME", "--nofile=65536", "a b", "'\"", "");

        List<String> argv = KubernetesPods.limited(hostile);

        assertThat(argv)
                .containsExactly(
                        "/usr/bin/prlimit",
                        "--nofile=1024:1024",
                        "--core=0:0",
                        "--",
                        "sh",
                        "-c",
                        "ulimit -Hn 65536; echo $HOME",
                        "--nofile=65536",
                        "a b",
                        "'\"",
                        "");
    }

    @Test
    void shouldRefuseSandboxThatAsksForFewerProcessesThanItsNodesBound() {
        var strict = new ResourceLimits(LIMITS.memoryBytes(), 1.0, 128, Duration.ofMinutes(5));

        assertThatThrownBy(() -> pods.requireLimits(strict)).isInstanceOf(SandboxException.class);
        pods.requireLimits(LIMITS);
    }

    @Test
    void shouldReachGatewayAtTheWorkersOwnAddress() {
        assertThat(worker.gatewayUrl()).isEqualTo("http://[fd00::7]:8081");
    }

    @Test
    void shouldStartListAndRemoveTurnSandbox() throws Exception {
        var sandboxes = new KubernetesExecSandboxes(api(), pods, worker, IMAGE);
        String sandboxId = UUID.randomUUID().toString();
        CompletableFuture<String> created =
                CompletableFuture.supplyAsync(() -> sandboxes.create(sandboxId, LIMITS), background);
        String name = "hephaestus-turn-" + sandboxId;
        setStatus(
                SANDBOXES,
                name,
                "Running",
                List.of(),
                List.of(running(KubernetesPods.SANDBOX), running(KubernetesPods.STAGER)));

        assertThat(created.get(10, TimeUnit.SECONDS)).isEqualTo(name);
        Pod pod = Objects.requireNonNull(
                api().pods().inNamespace(SANDBOXES).withName(name).get());
        assertThat(pod.getMetadata().getLabels())
                .containsEntry(KubernetesWorker.SANDBOX_KIND, KubernetesWorker.KIND_TURN)
                .containsEntry(KubernetesWorker.WORKER_POD, workerName)
                .containsEntry(KubernetesWorker.SANDBOX_ID, sandboxId)
                .containsEntry(KubernetesWorker.PART_OF, "hephaestus");
        assertThat(sandboxes.workerInstance()).isEqualTo(workerName);
        assertThat(sandboxes.list()).containsExactly(new ExecSandboxes.Listed(name, sandboxId, workerName, false));

        sandboxes.remove(name);
        sandboxes.remove(name);

        assertThat(api().pods().inNamespace(SANDBOXES).withName(name).get()).isNull();
        sandboxes.close();
    }

    @Test
    void shouldEndReviewAsCancelledAndDeleteItsJobAndPodWhenCancelledWhileItsPodRuns() throws Exception {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = name + "-child";
        watchedPod.set(child);
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        assertThat(startChild(name)).isEqualTo(child);
        assertThat(watching.await(10, TimeUnit.SECONDS))
                .as("fixture condition: the review waits on a registered watch of its Pod")
                .isTrue();

        adapter.cancel(jobId);

        assertThatThrownBy(() -> execution.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SandboxCancelledException.class);
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
        assertThat(executions("cancelled")).isEqualTo(1);
    }

    @Test
    void shouldDeleteTheRunningPodAndEndTheReviewAsCancelledWhenTheCancellationsJobDeletionIsRefused()
            throws Exception {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = name + "-child";
        watchedPod.set(child);
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId, LIMITS)), background);
        assertThat(startChild(name)).isEqualTo(child);
        assertThat(watching.await(10, TimeUnit.SECONDS))
                .as("fixture condition: the review waits on a registered watch of its Pod")
                .isTrue();
        // The API refuses the next deletion of the Job once. The client retries no refusal.
        mockServer()
                .expect()
                .delete()
                .withPath("/apis/batch/v1/namespaces/" + SANDBOXES + "/jobs/" + name)
                .andReturn(
                        403,
                        new StatusBuilder()
                                .withCode(403)
                                .withReason("Forbidden")
                                .build())
                .once();

        assertThatThrownBy(() -> adapter.cancel(jobId))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Could not delete a review Job");

        // Without the deletion of the Pod, the review would wait on it until its work deadline, minutes away.
        assertThatThrownBy(() -> execution.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SandboxCancelledException.class);
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
        assertThat(executions("cancelled")).isEqualTo(1);
        assertThat(executions("failure")).isZero();
        assertThat(job(name))
                .as("the attempt's own cleanup deletes the Job that stayed")
                .isNull();
    }

    @Test
    void shouldEndTheReviewAsAnInfrastructureFailureByItsWorkDeadlineWhenItsJobCreatesNoPod() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        var shortWork =
                new ResourceLimits(LIMITS.memoryBytes(), LIMITS.cpus(), LIMITS.pidsLimit(), Duration.ofSeconds(3));
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId, shortWork)), background);
        awaitJob(name);

        // The fixed wait for a Pod is two minutes; the remaining work time, three seconds, bounds it here.
        assertThatThrownBy(() -> execution.get(30, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SandboxInfrastructureException.class)
                .hasMessageContaining("created no Pod");
        assertThat(job(name)).isNull();
        assertThatThrownBy(() -> sessions.require(jobId, "Bearer job-token"))
                .as("the attempt's gateway session is closed")
                .isInstanceOf(ResponseStatusException.class);
        assertThat(executions("failure")).isEqualTo(1);
        assertThat(executions("timeout")).isZero();
    }

    @Test
    void shouldEndReviewAsCancelledAndDeleteItsJobWhenCancelledBeforeItsPodExists() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        awaitJob(name);

        adapter.cancel(jobId);

        assertThatThrownBy(() -> execution.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SandboxCancelledException.class);
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).list().getItems()).isEmpty();
    }

    @Test
    void shouldRefuseReviewAndKeepTheJobWhenItsAttemptAlreadyHasOne() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        Job existing = api().batch()
                .v1()
                .jobs()
                .inNamespace(SANDBOXES)
                .resource(new JobBuilder()
                        .withNewMetadata()
                        .withName(name)
                        .withNamespace(SANDBOXES)
                        .endMetadata()
                        .withNewSpec()
                        .withNewTemplate()
                        .withNewSpec()
                        .withRestartPolicy("Never")
                        .addNewContainer()
                        .withName("c")
                        .withImage(IMAGE)
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build())
                .create();

        assertThatThrownBy(() -> adapter.execute(spec(jobId)))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("already exists");
        assertThat(job(name))
                .as("the Job of the attempt is neither adopted nor deleted")
                .extracting(stored -> stored.getMetadata().getUid())
                .isEqualTo(existing.getMetadata().getUid());
    }

    @Test
    void shouldFailReviewWhoseWorkspaceDoesNotInitialize() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = startChild(name);
        setStatus(SANDBOXES, child, "Failed", List.of(exited(KubernetesPods.INITIALIZER, 1)), List.of());

        assertThatThrownBy(() -> execution.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(SandboxException.class)
                .hasMessageContaining("initialization failed");
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
    }

    @Test
    void shouldEndAsAnInfrastructureFailureWhenTheNetworkPolicyNeverFiltersTheReviewPod() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = startChild(name);
        setStatus(SANDBOXES, child, "Failed", List.of(exited(KubernetesPods.ISOLATION, 1)), List.of());

        assertThatThrownBy(() -> execution.get(10, TimeUnit.SECONDS))
                .as("the cluster's failure, not one of the review: the attempt is tried again")
                .hasCauseInstanceOf(SandboxInfrastructureException.class)
                .hasMessageContaining("network policy");
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
    }

    @Test
    void shouldReportUploadFailureExitWithoutResultsAsDockerDoes() throws Exception {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = startChild(name);
        setStatus(
                SANDBOXES,
                child,
                "Failed",
                List.of(exited(KubernetesPods.INITIALIZER, 0)),
                List.of(exited(KubernetesPods.RUNTIME, SandboxLayout.EXIT_UPLOAD_FAILED)));

        SandboxResult result = execution.get(10, TimeUnit.SECONDS);

        assertThat(result.exitCode()).isEqualTo(SandboxLayout.EXIT_UPLOAD_FAILED);
        assertThat(result.outputFiles()).isEmpty();
        assertThat(result.timedOut()).isFalse();
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
    }

    @Test
    void shouldReportTheWorkTimeoutExitAsATimeoutAndDeleteTheJobWhenTheRunnerReachesItsDeadline() throws Exception {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        CompletableFuture<SandboxResult> execution =
                CompletableFuture.supplyAsync(() -> adapter.execute(spec(jobId)), background);
        String name = KubernetesSandboxAdapter.jobName(jobId, 2);
        String child = startChild(name);
        setStatus(
                SANDBOXES,
                child,
                "Failed",
                List.of(exited(KubernetesPods.INITIALIZER, 0)),
                List.of(exited(KubernetesPods.RUNTIME, SandboxLayout.EXIT_WORK_TIMEOUT)));

        SandboxResult result = execution.get(10, TimeUnit.SECONDS);

        assertThat(result.exitCode()).isEqualTo(SandboxLayout.EXIT_WORK_TIMEOUT);
        assertThat(result.timedOut()).isTrue();
        assertThat(executions("timeout")).isEqualTo(1);
        assertThat(executions("success")).isZero();
        assertThat(job(name)).isNull();
        assertThat(api().pods().inNamespace(SANDBOXES).withName(child).get()).isNull();
    }

    @Test
    void shouldRefuseReviewThatAsksForInternetAccess() {
        var adapter = adapter();
        UUID jobId = UUID.randomUUID();
        SandboxSpec online = new SandboxSpec(
                jobId,
                IMAGE,
                List.of("x"),
                Map.of(),
                new NetworkPolicy(true, null, "token"),
                LIMITS,
                null,
                Map.of(),
                SandboxLayout.OUTPUT_PATH);

        assertThatThrownBy(() -> adapter.execute(online)).isInstanceOf(SandboxException.class);
        assertThat(api().batch().v1().jobs().inNamespace(SANDBOXES).list().getItems())
                .isEmpty();
        assertThat(api().pods().inNamespace(SANDBOXES).list().getItems()).isEmpty();
    }

    @Test
    void shouldUseTheClustersDefaultRuntimeAndRuntimeDefaultSeccompWhenNoneIsNamed() {
        var defaults = new KubernetesWorker(
                new KubernetesSandboxProperties(SANDBOXES, workerName, "10.42.0.7", 256, "", ""), 8081);
        Pod pod = new KubernetesPods(api(), defaults, 10, ImagePullPolicy.IF_NOT_PRESENT)
                .turnPod(new KubernetesPods.Workload("turn", Map.of(), IMAGE, "sandbox"), LIMITS);

        assertThat(pod.getSpec().getRuntimeClassName()).isNull();
        assertThat(pod.getSpec().getSecurityContext().getSeccompProfile().getType())
                .isEqualTo("RuntimeDefault");
        assertThat(pod.getSpec().getSecurityContext().getSeccompProfile().getLocalhostProfile())
                .isNull();
    }

    @Test
    void shouldStartNoSandboxBeforeTheIsolationProbePassed() {
        var unproved = new KubernetesWorker(properties, 8081);
        var unprovedPods = new KubernetesPods(api(), unproved, 10, ImagePullPolicy.IF_NOT_PRESENT);
        var adapter = new KubernetesSandboxAdapter(
                unprovedPods,
                new KubernetesReviewJobs(api(), unproved),
                unproved,
                new SandboxWorkspaceManager(),
                sessions,
                meters);

        assertThatThrownBy(() -> adapter.execute(spec(UUID.randomUUID())))
                .isInstanceOf(SandboxInfrastructureException.class)
                .hasMessageContaining("isolation probe");
        try (var turns = new KubernetesExecSandboxes(api(), unprovedPods, unproved, IMAGE)) {
            assertThatThrownBy(() -> turns.create("turn", LIMITS)).isInstanceOf(SandboxInfrastructureException.class);
        }
        assertThat(adapter.isHealthy()).isFalse();
        assertThat(api().batch().v1().jobs().inNamespace(SANDBOXES).list().getItems())
                .isEmpty();
        assertThat(api().pods().inNamespace(SANDBOXES).list().getItems()).isEmpty();
    }

    @Test
    void shouldRemoveWhatAnEarlierProcessLeftAndEndedJobsAndKeepTheRestWhenSwept() {
        UUID activeJob = UUID.randomUUID();
        createJob("job-ended", labels -> withJob(labels, UUID.randomUUID()));
        createJob("job-active", labels -> withJob(labels, activeJob));
        createJob("job-of-earlier-process", labels -> earlier(withJob(labels, activeJob)));
        createSandbox("host-here", KubernetesWorker.KIND_HOST, labels -> labels);
        createSandbox("host-of-earlier-process", KubernetesWorker.KIND_HOST, KubernetesSandboxRuntimeTest::earlier);
        createSandbox("turn-of-earlier-process", KubernetesWorker.KIND_TURN, KubernetesSandboxRuntimeTest::earlier);
        var running = new ReviewJob();
        running.setId(activeJob);
        ReviewJobRepository jobs = mock(ReviewJobRepository.class);
        when(jobs.findByStatusIn(any())).thenReturn(List.of(running));
        var registry = new SimpleMeterRegistry();
        // An hour later, every object here is past the reconciler's grace.
        Clock later = Clock.offset(Clock.systemUTC(), Duration.ofHours(1));

        new KubernetesSandboxReconciler(jobs, pods, reviewJobs, worker, registry, later).sweep();

        assertThat(api().batch().v1().jobs().inNamespace(SANDBOXES).list().getItems())
                .extracting(job -> job.getMetadata().getName())
                .containsExactly("job-active");
        assertThat(api().pods().inNamespace(SANDBOXES).list().getItems())
                .extracting(pod -> pod.getMetadata().getName())
                .as("turn sandboxes are their worker's, not the reconciler's")
                .containsExactlyInAnyOrder("host-here", "turn-of-earlier-process");
        assertThat(orphaned(registry, "job")).isEqualTo(2);
        assertThat(orphaned(registry, "pod")).isEqualTo(1);
    }

    @Test
    void shouldKeepAYoungJobOfAnUnknownJobWhenSwept() {
        createJob("job-young", labels -> withJob(labels, UUID.randomUUID()));
        ReviewJobRepository jobs = mock(ReviewJobRepository.class);
        when(jobs.findByStatusIn(any())).thenReturn(List.of());

        new KubernetesSandboxReconciler(jobs, pods, reviewJobs, worker, new SimpleMeterRegistry(), Clock.systemUTC())
                .sweep();

        assertThat(job("job-young"))
                .as("an attempt that starts is not mistaken for an ended one")
                .isNotNull();
    }

    @Test
    void shouldRemoveNothingWhenTheActiveJobsAreUnreadable() {
        createJob("job-ended", labels -> withJob(labels, UUID.randomUUID()));
        createSandbox("host-of-earlier-process", KubernetesWorker.KIND_HOST, KubernetesSandboxRuntimeTest::earlier);
        ReviewJobRepository jobs = mock(ReviewJobRepository.class);
        when(jobs.findByStatusIn(any())).thenThrow(new IllegalStateException("database unavailable"));
        var registry = new SimpleMeterRegistry();

        new KubernetesSandboxReconciler(
                        jobs, pods, reviewJobs, worker, registry, Clock.offset(Clock.systemUTC(), Duration.ofHours(1)))
                .sweep();

        assertThat(storedJob("job-ended")).isNotNull();
        assertThat(api().pods()
                        .inNamespace(SANDBOXES)
                        .withName("host-of-earlier-process")
                        .get())
                .isNotNull();
        assertThat(registry.get(AgentMetrics.SANDBOX_RECONCILER_SWEEPS)
                        .tag("outcome", "skipped")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void shouldCallEveryOtherWorkerOfATurnSandboxGone() {
        createSandbox("turn-here", KubernetesWorker.KIND_TURN, labels -> labels);
        createSandbox("turn-of-another-pod", KubernetesWorker.KIND_TURN, labels -> {
            labels.put(KubernetesWorker.WORKER_POD, "worker-before-rename");
            return labels;
        });
        try (var sandboxes = new KubernetesExecSandboxes(api(), pods, worker, IMAGE)) {
            assertThat(sandboxes.list())
                    .extracting(ExecSandboxes.Listed::id, ExecSandboxes.Listed::workerGone)
                    .containsExactlyInAnyOrder(tuple("turn-here", false), tuple("turn-of-another-pod", true));
        }
    }

    /** The labels as an earlier process of this worker Pod wrote them. */
    private static Map<String, String> earlier(Map<String, String> labels) {
        labels.put(KubernetesWorker.WORKER_PROCESS, UUID.randomUUID().toString());
        return labels;
    }

    // --- fixtures ---

    private KubernetesSandboxAdapter adapter() {
        return new KubernetesSandboxAdapter(pods, reviewJobs, worker, new SandboxWorkspaceManager(), sessions, meters);
    }

    private double executions(String outcome) {
        return meters.get(AgentMetrics.SANDBOX_EXECUTIONS)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private static double orphaned(SimpleMeterRegistry registry, String resource) {
        return registry.get(AgentMetrics.SANDBOX_RECONCILER_ORPHANED)
                .tag("resource", resource)
                .counter()
                .count();
    }

    /** A review Job of this installation, as the adapter writes one, with the labels that {@code labels} returns. */
    private Job createJob(String name, Function<Map<String, String>, Map<String, String>> labels) {
        Pod template = pods.workspacePod(
                new KubernetesPods.Workload(
                        name, labels.apply(worker.labels(KubernetesWorker.KIND_REVIEW)), IMAGE, "agent"),
                Map.of(),
                List.of("node", "run.ts"),
                LIMITS,
                SecurityProfile.DEFAULT);
        return api().batch()
                .v1()
                .jobs()
                .inNamespace(SANDBOXES)
                .resource(reviewJobs.job(template, 600L))
                .create();
    }

    private static Map<String, String> withJob(Map<String, String> labels, UUID jobId) {
        labels.put(KubernetesWorker.JOB_ID, jobId.toString());
        return labels;
    }

    private @Nullable Job job(String name) {
        return api().batch().v1().jobs().inNamespace(SANDBOXES).withName(name).get();
    }

    private Job storedJob(String name) {
        return Objects.requireNonNull(job(name), () -> "Job " + name + " exists");
    }

    private Job awaitJob(String name) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Job job = job(name);
            if (job != null) {
                return job;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Job " + name + " was not created");
    }

    /** Creates the Pod that the Job controller would create for the attempt's Job. The mock runs no Job controller. */
    private String startChild(String name) {
        Job job = awaitJob(name);
        Pod child = new PodBuilder()
                .withNewMetadata()
                .withName(name + "-child")
                .withNamespace(SANDBOXES)
                .withLabels(job.getSpec().getTemplate().getMetadata().getLabels())
                .addToLabels(KubernetesReviewJobs.JOB_NAME, name)
                .endMetadata()
                .withSpec(job.getSpec().getTemplate().getSpec())
                .build();
        return api().pods()
                .inNamespace(SANDBOXES)
                .resource(child)
                .create()
                .getMetadata()
                .getName();
    }

    private static SandboxSpec spec(UUID jobId) {
        return spec(jobId, LIMITS);
    }

    private static SandboxSpec spec(UUID jobId, ResourceLimits limits) {
        return new SandboxSpec(
                jobId,
                IMAGE,
                List.of("node", "run.ts"),
                Map.of(),
                new NetworkPolicy(false, null, "job-token"),
                limits,
                null,
                Map.of("input.txt", "input".getBytes(StandardCharsets.UTF_8)),
                Map.of(),
                List.of(),
                SandboxLayout.OUTPUT_PATH,
                2);
    }

    private void createSandbox(String name, String kind, Function<Map<String, String>, Map<String, String>> labels) {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(SANDBOXES)
                .withLabels(labels.apply(worker.labels(kind)))
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("c")
                .withImage(IMAGE)
                .endContainer()
                .endSpec()
                .build();
        api().pods().inNamespace(SANDBOXES).resource(pod).create();
    }

    private Pod awaitPod(String namespace, String name) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Pod pod = api().pods().inNamespace(namespace).withName(name).get();
            if (pod != null) {
                return pod;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Pod " + name + " was not created");
    }

    /** What the kubelet would report; the mock server runs no container. */
    private void setStatus(
            String namespace,
            String name,
            String phase,
            List<ContainerStatus> initStatuses,
            List<ContainerStatus> statuses) {
        Pod pod = awaitPod(namespace, name);
        pod.setStatus(new PodStatusBuilder()
                .withPhase(phase)
                .withInitContainerStatuses(initStatuses)
                .withContainerStatuses(statuses)
                .build());
        api().pods().inNamespace(namespace).resource(pod).update();
    }

    private static ContainerStatus running(String name) {
        return new ContainerStatusBuilder()
                .withName(name)
                .withNewState()
                .withNewRunning()
                .endRunning()
                .endState()
                .build();
    }

    private static ContainerStatus exited(String name, int exitCode) {
        return new ContainerStatusBuilder()
                .withName(name)
                .withNewState()
                .withNewTerminated()
                .withExitCode(exitCode)
                .endTerminated()
                .endState()
                .build();
    }

    private static Container container(Pod pod, String name) {
        return pod.getSpec().getContainers().stream()
                .filter(container -> name.equals(container.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static boolean readOnly(Container container, String path) {
        VolumeMount mount = container.getVolumeMounts().stream()
                .filter(candidate -> path.equals(candidate.getMountPath()))
                .findFirst()
                .orElseThrow();
        return Boolean.TRUE.equals(mount.getReadOnly());
    }

    private static String[] limitedArgv(String... command) {
        List<String> argv = new ArrayList<>(KubernetesPods.RLIMITS);
        argv.addAll(List.of(command));
        return argv.toArray(String[]::new);
    }

    private static void assertRestricted(Container container, long uid) {
        var security = container.getSecurityContext();
        assertThat(security.getRunAsUser()).isEqualTo(uid);
        assertThat(security.getRunAsNonRoot()).isTrue();
        assertThat(security.getPrivileged()).isFalse();
        assertThat(security.getAllowPrivilegeEscalation()).isFalse();
        assertThat(security.getReadOnlyRootFilesystem()).isTrue();
        assertThat(security.getCapabilities().getDrop()).containsExactly("ALL");
        assertThat(security.getCapabilities().getAdd()).isNullOrEmpty();
    }
}
