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

/**
 * Live integration tests for the interactive sandbox.
 *
 * <p>Boots real Docker containers and runs {@code pi-mentor-runner.mjs} inside them. Tagged
 * {@code live} so the default test suite skips it; run with {@code -Dgroups=live} or via the
 * {@code live-tests} Maven profile.
 *
 * <p>Each test uses a fresh sandbox stack — adapter, registry, watchdog, container manager — to
 * keep state isolated. Cleanup forcibly removes any lingering interactive containers + networks.
 */
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
        // Tight TTL so the idle-eviction test doesn't have to wait minutes
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

        // Load the runner script from the classpath (same one shipped with the server)
        runnerBytes = Files.readAllBytes(Path.of("src/main/resources/agent/pi-mentor-runner.mjs"));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (registry != null) {
            registry.shutdown();
        }
        // Belt-and-braces: remove any containers/networks with our managed label that survived
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
            new NetworkPolicy(true, null, null, null), // internet on for simplicity (LLM proxy unused)
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

        /** Constructed at runtime because Java's unicode-escape pre-lexer would translate the
         * six-char backslash-u escape into the codepoint itself, which IS a line terminator
         * and would prematurely end the surrounding string literal. */
        private static final String LINE_SEP = Character.toString(0x2028);
        private static final String PARA_SEP = Character.toString(0x2029);

        @Test
        @DisplayName("U+2028 / U+2029 / \\n / \\r inside JSON string values survive intact")
        void unicodeSurvives() {
            AttachedSandbox sb = adapter.attach(buildSpec("u2", "w2"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);
            // Embed U+2028 and U+2029 (line/paragraph separators) plus literal \n and \r
            // inside the JSON string value the runner will echo back to us verbatim.
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
                    // The decoded payload must match the input exactly — proves U+2028/U+2029 are
                    // not treated as line terminators and \n / \r escapes round-trip cleanly.
                    assertThat(echoBack.path("payload").asText()).isEqualTo(payload);
                });
            // UTF-8 encoding of U+2028 / U+2029 is 3 bytes each (catches encoding regressions).
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
            // The runner emits exactly `burst` tick frames plus one "ready" frame on startup.
            // Expected drops = (1 + burst) - ringCapacity = 49.
            int expectedDrops = (1 + burst) - ringCapacity;

            await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                    assertThat(metrics.ringBufferDropped.count() - before).isEqualTo((double) expectedDrops)
                );

            // After the dust settles, a fresh subscriber observes the snapshot — which must contain
            // the LAST `ringCapacity` ticks (drop-OLDEST policy), not any prefix.
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
            // The newest tick is the largest n; the oldest retained tick is (burst - ringCapacity).
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

            // idleTtlSeconds=2: poll registry.reap() instead of sleeping so the test passes
            // as soon as the TTL elapses and the reaper observes idle.
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
    }

    @Nested
    @DisplayName("Container labels")
    class ContainerLabels {

        @Test
        @DisplayName("interactive containers carry KIND=interactive + SESSION_ID")
        void labelsPresent() {
            AttachedSandbox sb = adapter.attach(buildSpec("u6", "w6"));
            // Find our container in listManagedContainers
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
            // We do NOT use bind mounts (PE-recommended departure from the issue text — see
            // DockerInteractiveSandboxAdapter package-info). CAP_CHOWN is dropped by the security
            // policy, so we cannot chown to 1000:1000 inside the container. Functional requirement
            // is what matters: uid 1000 can write to scratch + context/user, NOT to context/target.
            AttachedSandbox sb = adapter.attach(buildSpec("u7", "w7"));
            String containerId = ((DockerAttachedSandboxAdapter) sb).containerId();
            // Run as uid 1000:1000 (the runner's identity)
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
            // Identical to the other live specs, except the runner is `sh -c 'exit 1'` instead of
            // the JSONL-emitting Pi mentor skeleton. The runner exits before any frame, so the
            // pump observes EOF and terminate(ERROR) completes `firstFrame` exceptionally.
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
            // Distinct from first_frame_timeout: dashboards must be able to tell runner-side
            // regressions from flow-control issues.
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
            // Container should be gone
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
