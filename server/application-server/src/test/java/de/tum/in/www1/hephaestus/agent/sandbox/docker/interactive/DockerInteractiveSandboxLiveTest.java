package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.ContainerSecurityPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.DockerClientOperations;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxContainerManager;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxLabels;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxNetworkManager;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxWorkspaceManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandboxState;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/** Live integration tests — boots real Docker. Run with {@code -Pgroups=live} or {@code live-tests}. */
@Tag("live")
@DisplayName("Docker Interactive Sandbox Live")
class DockerInteractiveSandboxLiveTest {

    private static final String NODE_IMAGE = "node:22-slim";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DockerClient dockerClient;
    private DockerClientOperations dockerOps;
    private SandboxNetworkManager networkManager;
    private SandboxWorkspaceManager workspaceManager;
    private SandboxContainerManager containerManager;
    private ContainerSecurityPolicy securityPolicy;
    private InteractiveSandboxRegistry registry;
    private InteractiveSandboxMetrics metrics;
    private StdinWriteWatchdog watchdog;
    private DockerInteractiveSandboxAdapter adapter;
    private ExecutorService dockerWaitExecutor;
    private SimpleMeterRegistry meterRegistry;
    private byte[] runnerBytes;

    @BeforeAll
    static void checkDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available — skipping live tests");
    }

    @BeforeEach
    void setUp() throws Exception {
        SandboxProperties sandboxProperties = new SandboxProperties(
            true,
            "unix:///var/run/docker.sock",
            false,
            null,
            5,
            10,
            60,
            null,
            8080,
            null,
            209_715_200L,
            500_000,
            null
        );
        // Tight TTL so idle eviction tests don't have to wait minutes.
        InteractiveSandboxProperties interactiveProperties = new InteractiveSandboxProperties(
            true,
            /* idleTtlSeconds */ 2,
            /* graceTimeoutSeconds */ 2,
            /* reapIntervalSeconds */ 1,
            /* ringBufferFrames */ 16,
            /* stdinWriteTimeoutMs */ 5_000,
            /* sendQueueCapacity */ 64,
            /* subscriberQueueCapacity */ 64,
            /* attachFirstFrameTimeoutSeconds */ 15,
            /* maxSessionsPerUser */ 3,
            /* maxSessionsTotal */ 50,
            /* replicaCount */ 1,
            /* maxFrameChars */ 64 * 1024
        );

        dockerClient = DockerClientImpl.getInstance(
            DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
            new ApacheDockerHttpClient.Builder().dockerHost(URI.create("unix:///var/run/docker.sock")).build()
        );

        dockerOps = new DockerClientOperations(dockerClient);
        dockerWaitExecutor = Executors.newCachedThreadPool();
        containerManager = new SandboxContainerManager(dockerOps, sandboxProperties, dockerWaitExecutor);
        networkManager = new SandboxNetworkManager(dockerOps, sandboxProperties);
        workspaceManager = new SandboxWorkspaceManager(dockerOps);
        securityPolicy = new ContainerSecurityPolicy(sandboxProperties, null);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new InteractiveSandboxMetrics(meterRegistry);
        watchdog = new StdinWriteWatchdog();
        registry = new InteractiveSandboxRegistry(
            interactiveProperties,
            containerManager,
            metrics,
            watchdog,
            meterRegistry
        );
        adapter = new DockerInteractiveSandboxAdapter(
            interactiveProperties,
            sandboxProperties,
            networkManager,
            workspaceManager,
            containerManager,
            securityPolicy,
            registry,
            metrics,
            MAPPER,
            dockerWaitExecutor,
            "docker",
            8080
        );

        runnerBytes = Files.readAllBytes(Path.of("src/main/resources/agent/pi-mentor-runner.mjs"));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (registry != null) {
            registry.shutdown();
        }
        // Belt-and-braces: clean up anything a failing test left behind.
        try {
            containerManager
                .listManagedContainers()
                .forEach(c -> {
                    try {
                        containerManager.forceRemove(c.id());
                    } catch (Exception ignored) {}
                });
        } catch (Exception ignored) {}
        try {
            networkManager
                .listOrphanedNetworks()
                .forEach(n -> {
                    try {
                        networkManager.removeNetwork(n.id());
                    } catch (Exception ignored) {}
                });
        } catch (Exception ignored) {}
        if (dockerWaitExecutor != null) {
            dockerWaitExecutor.shutdownNow();
        }
        if (dockerClient != null) {
            dockerClient.close();
        }
    }

    private InteractiveSandboxSpec buildSpec(String userId, String workspaceId) {
        UUID sessionId = UUID.randomUUID();
        SecurityProfile sec = new SecurityProfile(null, "private", List.of("ALL"), Map.of());
        return new InteractiveSandboxSpec(
            sessionId,
            userId,
            workspaceId,
            NODE_IMAGE,
            List.of("node", "/workspace/.runner/pi-mentor-runner.mjs"),
            Map.of(),
            new NetworkPolicy(true, null, null, null),
            new ResourceLimits(512 * 1024 * 1024, 1.0, 256, Duration.ofMinutes(5)),
            sec,
            Map.of(".runner/pi-mentor-runner.mjs", runnerBytes),
            Map.of()
        );
    }

    private static JsonNode ping() {
        return MAPPER.createObjectNode().put("type", "ping");
    }

    private static JsonNode echo(String payload) {
        return ((ObjectNode) MAPPER.createObjectNode().put("type", "echo")).put("payload", payload);
    }

    private static JsonNode emit(int count, String tag) {
        return ((ObjectNode) MAPPER.createObjectNode().put("type", "emit").put("count", count)).put("tag", tag);
    }

    @Nested
    @DisplayName("Handshake")
    class Handshake {

        @Test
        @DisplayName("ping → pong via subscribe")
        void pingPong() {
            AttachedSandbox sb = adapter.attach(buildSpec("u1", "w1"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);
            sb.send(ping());

            await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                    assertThat(frames.stream().anyMatch(n -> "pong".equals(n.path("type").asText()))).isTrue()
                );
            sb.close(Duration.ofSeconds(2));
        }
    }

    @Nested
    @DisplayName("Unicode safety")
    class UnicodeSafety {

        // Built at runtime: the U+2028/U+2029 escapes are pre-lexer line terminators in Java.
        private static final String LINE_SEP = Character.toString(0x2028);
        private static final String PARA_SEP = Character.toString(0x2029);

        @Test
        @DisplayName("U+2028 / U+2029 / \\n / \\r inside JSON string values survive intact")
        void unicodeSurvives() {
            AttachedSandbox sb = adapter.attach(buildSpec("u2", "w2"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);
            String payload = "a" + LINE_SEP + "b" + PARA_SEP + "c\nd\re";
            sb.send(echo(payload));

            await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    JsonNode echoBack = frames
                        .stream()
                        .filter(n -> "echo_back".equals(n.path("type").asText()))
                        .findFirst()
                        .orElse(null);
                    assertThat(echoBack).isNotNull();
                    assertThat(echoBack.path("payload").asText()).isEqualTo(payload);
                });
            // 3-byte UTF-8 sequences; catches encoding regressions.
            assertThat(LINE_SEP.getBytes(StandardCharsets.UTF_8)).hasSize(3);
            assertThat(PARA_SEP.getBytes(StandardCharsets.UTF_8)).hasSize(3);
            sb.close(Duration.ofSeconds(2));
        }
    }

    @Nested
    @DisplayName("Ring buffer overflow")
    class RingBufferOverflow {

        @Test
        @DisplayName("flood drops EXACTLY (frames+ready - capacity) and preserves NEWEST")
        void ringBufferDropsOldest() {
            double before = metrics.ringBufferDropped.count();
            AttachedSandbox sb = adapter.attach(buildSpec("u3", "w3"));
            int burst = 64;
            int ringCapacity = 16;
            sb.send(emit(burst, "burst"));
            // 1 startup "ready" + burst ticks - ring capacity = drops.
            int expectedDrops = (1 + burst) - ringCapacity;

            await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                    assertThat(metrics.ringBufferDropped.count() - before).isEqualTo((double) expectedDrops)
                );

            // Late subscriber's snapshot must hold the NEWEST ringCapacity ticks, not any prefix.
            CopyOnWriteArrayList<JsonNode> snapshot = new CopyOnWriteArrayList<>();
            sb.subscribe(snapshot::add);
            await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                    assertThat(
                        snapshot
                            .stream()
                            .filter(n -> "tick".equals(n.path("type").asText()))
                            .count()
                    ).isEqualTo(ringCapacity)
                );
            int oldestRetainedN = burst - ringCapacity;
            int newestN = burst - 1;
            assertThat(
                snapshot
                    .stream()
                    .filter(n -> "tick".equals(n.path("type").asText()))
                    .map(n -> n.path("n").asInt())
            )
                .as("subscriber must observe ticks (%d..%d)", oldestRetainedN, newestN)
                .contains(oldestRetainedN)
                .contains(newestN)
                .doesNotContain(oldestRetainedN - 1);

            sb.close(Duration.ofSeconds(2));
        }
    }

    @Nested
    @DisplayName("Idle eviction")
    class IdleEviction {

        @Test
        @DisplayName("session idle past TTL is reaped with reason=idle")
        void idleReaped() {
            double before = metrics.evictionsBy(EvictionReason.IDLE).count();
            AttachedSandbox sb = adapter.attach(buildSpec("u4", "w4"));
            assertThat(((DockerAttachedSandboxAdapter) sb).state()).isEqualTo(AttachedSandboxState.ATTACHED);

            // Poll reap() rather than sleep so the test exits as soon as TTL elapses.
            await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    registry.reap();
                    assertThat(((DockerAttachedSandboxAdapter) sb).state()).isEqualTo(AttachedSandboxState.CLOSED);
                });
            assertThat(metrics.evictionsBy(EvictionReason.IDLE).count() - before).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Concurrent attach")
    class ConcurrentAttach {

        @Test
        @DisplayName("same (userId, workspaceId) returns the same AttachedSandbox instance")
        void shareSemantics() {
            AttachedSandbox a = adapter.attach(buildSpec("u5", "w5"));
            AttachedSandbox b = adapter.attach(buildSpec("u5", "w5"));
            assertThat(b).isSameAs(a);
            a.close(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("parallel attach race: loser's container/network are reclaimed (no leak)")
        void duplicateBranchTearsDownLoser() throws Exception {
            // Both callers race past the fast-path findLive and both spawn containers; the registry
            // resolves the race via tryRegister. Pre-fix the loser leaked its container + network.
            int containersBefore = managedInteractiveCount();
            java.util.concurrent.CyclicBarrier gate = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.atomic.AtomicReference<AttachedSandbox> r1 =
                new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<AttachedSandbox> r2 =
                new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> err =
                new java.util.concurrent.atomic.AtomicReference<>();
            Thread t1 = Thread.ofVirtual().start(() -> {
                try {
                    gate.await();
                    r1.set(adapter.attach(buildSpec("u_race", "w_race")));
                } catch (Throwable e) {
                    err.compareAndSet(null, e);
                }
            });
            Thread t2 = Thread.ofVirtual().start(() -> {
                try {
                    gate.await();
                    r2.set(adapter.attach(buildSpec("u_race", "w_race")));
                } catch (Throwable e) {
                    err.compareAndSet(null, e);
                }
            });
            t1.join();
            t2.join();
            assertThat(err.get()).isNull();
            assertThat(r1.get()).isSameAs(r2.get());

            // Reclaim of loser is fire-and-forget; wait for the close future then assert one container.
            await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(managedInteractiveCount() - containersBefore).isEqualTo(1));

            r1.get().close(Duration.ofSeconds(2));
        }

        private int managedInteractiveCount() {
            return (int) containerManager
                .listManagedContainers()
                .stream()
                .filter(c -> SandboxLabels.KIND_INTERACTIVE.equals(c.labels().get(SandboxLabels.KIND)))
                .count();
        }
    }

    @Nested
    @DisplayName("Container labels")
    class ContainerLabels {

        @Test
        @DisplayName("interactive containers carry KIND=interactive + SESSION_ID")
        void labelsPresent() {
            AttachedSandbox sb = adapter.attach(buildSpec("u6", "w6"));
            String sessionId = sb.sessionId().toString();
            var match = containerManager
                .listManagedContainers()
                .stream()
                .filter(c -> sessionId.equals(c.labels().get(SandboxLabels.SESSION_ID)))
                .findFirst();
            assertThat(match).as("Container with our SESSION_ID label exists").isPresent();
            assertThat(match.get().labels().get(SandboxLabels.KIND)).isEqualTo(SandboxLabels.KIND_INTERACTIVE);
            assertThat(match.get().labels().get(SandboxLabels.MANAGED)).isEqualTo("true");
            sb.close(Duration.ofSeconds(2));
        }
    }

    @Nested
    @DisplayName("Workspace mounts")
    class WorkspaceMounts {

        @Test
        @DisplayName("uid 1000 can write to scratch + context/user but not context/target")
        void mountPermissions() {
            AttachedSandbox sb = adapter.attach(buildSpec("u7", "w7"));
            String containerId = ((DockerAttachedSandboxAdapter) sb).containerId();
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "exec",
                "-u",
                "1000:1000",
                containerId,
                "sh",
                "-c",
                "set -e; " +
                    "touch /workspace/scratch/probe && rm /workspace/scratch/probe && echo scratch:rw; " +
                    "touch /workspace/context/user/probe && rm /workspace/context/user/probe && echo user:rw; " +
                    "if touch /workspace/context/target/probe 2>/dev/null; then echo target:RW_UNEXPECTED; else echo target:ro; fi; " +
                    "if touch /workspace/context/probe 2>/dev/null; then echo context:RW_UNEXPECTED; else echo context:ro; fi"
            );
            pb.redirectErrorStream(true);
            try {
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
                assertThat(out).contains("scratch:rw");
                assertThat(out).contains("user:rw");
                assertThat(out).contains("target:ro");
                assertThat(out).contains("context:ro");
                assertThat(out).doesNotContain("RW_UNEXPECTED");
            } catch (Exception e) {
                throw new AssertionError("Failed to probe mount permissions: " + e.getMessage(), e);
            } finally {
                sb.close(Duration.ofSeconds(2));
            }
        }
    }

    @Nested
    @DisplayName("Attach failure modes")
    class AttachFailureModes {

        @Test
        @DisplayName("runner that crashes before emitting a frame → attach.failure{reason=first_frame_failed}")
        void runnerCrashesBeforeFirstFrame() {
            double timeoutBefore = metrics.attachFailureFirstFrameTimeout.count();
            double failedBefore = metrics.attachFailureFirstFrameFailed.count();
            // sh -c 'exit 1' instead of the JSONL runner: pump sees EOF before any frame.
            InteractiveSandboxSpec base = buildSpec("u_crash", "w_crash");
            InteractiveSandboxSpec brokenSpec = new InteractiveSandboxSpec(
                base.sessionId(),
                base.userId(),
                base.workspaceId(),
                base.image(),
                List.of("sh", "-c", "exit 1"),
                base.environment(),
                base.networkPolicy(),
                base.resourceLimits(),
                base.securityProfile(),
                base.inputFiles(),
                base.volumeMounts()
            );
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.attach(brokenSpec)).isInstanceOf(
                InteractiveSandboxException.class
            );
            // Must distinguish runner-crash from flow-control timeout for dashboards.
            assertThat(metrics.attachFailureFirstFrameFailed.count() - failedBefore).isEqualTo(1.0);
            assertThat(metrics.attachFailureFirstFrameTimeout.count() - timeoutBefore).isZero();
        }
    }

    @Nested
    @DisplayName("Close lifecycle")
    class CloseLifecycle {

        @Test
        @DisplayName("close transitions to CLOSED and removes container")
        void closeRemoves() {
            AttachedSandbox sb = adapter.attach(buildSpec("u8", "w8"));
            String containerId = ((DockerAttachedSandboxAdapter) sb).containerId();
            sb.close(Duration.ofSeconds(2));
            assertThat(((DockerAttachedSandboxAdapter) sb).state()).isEqualTo(AttachedSandboxState.CLOSED);
            assertThat(
                containerManager
                    .listManagedContainers()
                    .stream()
                    .filter(c -> containerId.equals(c.id()))
                    .toList()
            ).isEmpty();
        }

        @Test
        @DisplayName("send after close throws InteractiveSandboxException")
        void sendAfterClose() {
            AttachedSandbox sb = adapter.attach(buildSpec("u9", "w9"));
            sb.close(Duration.ofSeconds(2));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sb.send(ping())).isInstanceOf(
                InteractiveSandboxException.class
            );
        }
    }
}
