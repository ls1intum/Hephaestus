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
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
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
import de.tum.in.www1.hephaestus.testconfig.LiveDockerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/** Live integration tests — boots real Docker. Run with {@code -Pgroups=live} or {@code live-tests}. */
@LiveDockerTest
@DisplayName("Docker Interactive Sandbox Live")
class DockerInteractiveSandboxLiveTest {

    private static final String NODE_IMAGE = "node:22-slim";
    private static final String AGENT_PI_IMAGE = "ghcr.io/ls1intum/hephaestus/agent-pi:latest";
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

        @Test
        @DisplayName("agent-pi image: workspace setup succeeds with cap-drop=ALL (uid-1000-owned /workspace)")
        void agentPiWorkspaceSetupWithCapDropAll() {
            // node:22-slim lets root create /workspace itself, so it owns it and mkdir never fails.
            // agent-pi pre-creates /workspace owned by 1000:1000 in the Dockerfile; mkdir as root
            // with --cap-drop=ALL (no CAP_DAC_OVERRIDE) would fail without the CONTAINER_USER fix.
            assumeTrue(
                dockerOps.imageIsPresent(AGENT_PI_IMAGE),
                "agent-pi image not in local daemon — build with: docker build -t " + AGENT_PI_IMAGE + " docker/agents/pi/"
            );
            UUID sessionId = UUID.randomUUID();
            SecurityProfile sec = new SecurityProfile(null, "private", List.of("ALL"), Map.of());
            InteractiveSandboxSpec piSpec = new InteractiveSandboxSpec(
                sessionId,
                "u_pi_perm",
                "w_pi_perm",
                AGENT_PI_IMAGE,
                List.of("node", "/workspace/.runner/pi-mentor-runner.mjs"),
                Map.of(),
                new NetworkPolicy(true, null, null, null),
                new ResourceLimits(512 * 1024 * 1024, 1.0, 256, Duration.ofMinutes(5)),
                sec,
                Map.of(".runner/pi-mentor-runner.mjs", runnerBytes),
                Map.of()
            );
            AttachedSandbox sb = adapter.attach(piSpec);
            assertThat(((DockerAttachedSandboxAdapter) sb).state()).isEqualTo(AttachedSandboxState.ATTACHED);
            sb.close(Duration.ofSeconds(2));
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

    /**
     * Builds an {@link InteractiveSandboxSpec} that uses the REAL {@link PiRuntimeFactory}
     * production command — identical to what {@code MentorPiAdapter} produces in the live server.
     * {@code MENTOR_RUNNER_PROTOCOL_ONLY=1} stubs the Pi SDK so no LLM or API key is needed.
     *
     * <p>This exercises the full {@code sh -c "mkdir … && ln … && cp … && LD_PRELOAD=… node …"}
     * chain, catching bootstrap failures (missing dirs, bad LD_PRELOAD symlink, failing {@code cp})
     * that the toy-command tests in other nested classes never reach.
     */
    private InteractiveSandboxSpec buildMentorSpec(String userId, String workspaceId) {
        PiRuntimeFactory factory = new PiRuntimeFactory(MAPPER);
        // API_KEY + baseUrl triggers the custom provider extension code path used in production.
        PiPlanSpec planSpec = new PiPlanSpec(
            LlmProvider.OPENAI,
            CredentialMode.API_KEY,
            "stub-api-key",
            "stub-model",
            "https://api.stub.example.com/v1",
            null,
            true,
            120,
            "pi-mentor-runner.mjs",
            Map.of(),
            ""
        );
        PiRuntimeFactory.PiPlan plan = factory.build(planSpec);

        Map<String, String> env = new HashMap<>(plan.environment());
        // Stub: skip Pi SDK load; runner emits runner_ready + handles requests without an LLM.
        env.put("MENTOR_RUNNER_PROTOCOL_ONLY", "1");

        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            userId,
            workspaceId,
            AGENT_PI_IMAGE,
            plan.command(),
            Map.copyOf(env),
            plan.networkPolicy(),
            ResourceLimits.DEFAULT,
            SecurityProfile.DEFAULT,
            plan.inputFiles(),
            Map.of()
        );
    }

    @Nested
    @DisplayName("Mentor RPC protocol")
    class MentorRpcProtocol {

        private static final Duration RPC_TIMEOUT = Duration.ofSeconds(25);

        @Test
        @DisplayName("production bootstrap: sh -c chain succeeds and runner emits runner_ready")
        void productionBootstrapSucceeds() {
            assumeTrue(
                dockerOps.imageIsPresent(AGENT_PI_IMAGE),
                "agent-pi image not in local daemon — build with: docker build -t " + AGENT_PI_IMAGE + " docker/agents/pi/"
            );
            // Uses the real PiRuntimeFactory command (mkdir + ln + cp + LD_PRELOAD + node).
            // If any step in the sh -c chain fails (missing dir, bad symlink, missing cp source),
            // the runner never starts, the pump sees EOF with non-zero exit, and attach() throws.
            AttachedSandbox sb = adapter.attach(buildMentorSpec("u_boot", "w_boot"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);
            await()
                .atMost(RPC_TIMEOUT)
                .untilAsserted(() ->
                    assertThat(
                        frames.stream().anyMatch(f ->
                            "runner_ready".equals(
                                f.path("params").path("event").path("type").asText()
                            )
                        )
                    ).isTrue()
                );
            sb.close(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("hello → {protocolVersion:1}")
        void helloHandshake() {
            assumeTrue(dockerOps.imageIsPresent(AGENT_PI_IMAGE), "agent-pi image not in local daemon");
            AttachedSandbox sb = adapter.attach(buildMentorSpec("u_hello", "w_hello"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);

            // Wait for the runner to be ready before sending any RPC.
            await().atMost(RPC_TIMEOUT).untilAsserted(() ->
                assertThat(frames.stream().anyMatch(f ->
                    "runner_ready".equals(f.path("params").path("event").path("type").asText())
                )).isTrue()
            );

            String helloId = UUID.randomUUID().toString();
            sb.send(MAPPER.createObjectNode()
                .<ObjectNode>put("jsonrpc", "2.0")
                .<ObjectNode>put("id", helloId)
                .<ObjectNode>put("method", "hello")
                .set("params", MAPPER.createObjectNode()));

            await().atMost(RPC_TIMEOUT).untilAsserted(() -> {
                JsonNode resp = frames.stream()
                    .filter(f -> helloId.equals(f.path("id").asText()))
                    .findFirst().orElse(null);
                assertThat(resp).as("hello response").isNotNull();
                assertThat(resp.path("result").path("protocolVersion").asInt())
                    .as("protocolVersion").isEqualTo(1);
            });
            sb.close(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("open_thread + prompt → agent_start + text_delta + agent_end (stub turn)")
        void stubTurn() {
            assumeTrue(dockerOps.imageIsPresent(AGENT_PI_IMAGE), "agent-pi image not in local daemon");
            AttachedSandbox sb = adapter.attach(buildMentorSpec("u_turn", "w_turn"));
            CopyOnWriteArrayList<JsonNode> frames = new CopyOnWriteArrayList<>();
            sb.subscribe(frames::add);

            await().atMost(RPC_TIMEOUT).untilAsserted(() ->
                assertThat(frames.stream().anyMatch(f ->
                    "runner_ready".equals(f.path("params").path("event").path("type").asText())
                )).isTrue()
            );

            // hello
            String helloId = UUID.randomUUID().toString();
            sb.send(MAPPER.createObjectNode()
                .<ObjectNode>put("jsonrpc", "2.0")
                .<ObjectNode>put("id", helloId)
                .<ObjectNode>put("method", "hello")
                .set("params", MAPPER.createObjectNode()));
            await().atMost(RPC_TIMEOUT).untilAsserted(() ->
                assertThat(frames.stream().anyMatch(f -> helloId.equals(f.path("id").asText()))).isTrue()
            );

            // open_thread
            String threadId = UUID.randomUUID().toString();
            String openId = UUID.randomUUID().toString();
            sb.send(MAPPER.createObjectNode()
                .<ObjectNode>put("jsonrpc", "2.0")
                .<ObjectNode>put("id", openId)
                .<ObjectNode>put("method", "open_thread")
                .set("params", MAPPER.createObjectNode().put("threadId", threadId)));
            await().atMost(RPC_TIMEOUT).untilAsserted(() -> {
                JsonNode openResp = frames.stream()
                    .filter(f -> openId.equals(f.path("id").asText()))
                    .findFirst().orElse(null);
                assertThat(openResp).as("open_thread response").isNotNull();
                assertThat(openResp.path("result").path("threadId").asText()).isEqualTo(threadId);
            });

            // prompt (stub emits: agent_start → message_update/text_delta → agent_end)
            String promptId = UUID.randomUUID().toString();
            sb.send(MAPPER.createObjectNode()
                .<ObjectNode>put("jsonrpc", "2.0")
                .<ObjectNode>put("id", promptId)
                .<ObjectNode>put("method", "prompt")
                .set("params", MAPPER.createObjectNode()
                    .put("threadId", threadId)
                    .put("text", "Hello, stub!")));
            await().atMost(RPC_TIMEOUT).untilAsserted(() -> {
                JsonNode promptResp = frames.stream()
                    .filter(f -> promptId.equals(f.path("id").asText()))
                    .findFirst().orElse(null);
                assertThat(promptResp).as("prompt response").isNotNull();
                assertThat(promptResp.path("result").path("accepted").asBoolean()).isTrue();
            });

            // The stub emits agent_start → message_update → agent_end scoped to threadId.
            await().atMost(RPC_TIMEOUT).untilAsserted(() -> {
                List<JsonNode> events = frames.stream()
                    .filter(f ->
                        "event".equals(f.path("method").asText()) &&
                        threadId.equals(f.path("params").path("threadId").asText())
                    )
                    .map(f -> f.path("params").path("event"))
                    .collect(Collectors.toList());
                assertThat(events.stream().anyMatch(e -> "agent_start".equals(e.path("type").asText())))
                    .as("agent_start").isTrue();
                assertThat(events.stream().anyMatch(e -> "message_update".equals(e.path("type").asText())))
                    .as("message_update / text_delta").isTrue();
                assertThat(events.stream().anyMatch(e -> "agent_end".equals(e.path("type").asText())))
                    .as("agent_end").isTrue();
            });
            sb.close(Duration.ofSeconds(2));
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
