package de.tum.in.www1.hephaestus.agent.mentor.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.in.www1.hephaestus.testconfig.LiveLlmTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Production-readiness stress test for the Pi mentor runner. Spawns {@code N} concurrent
 * runner subprocesses (one per simulated user-workspace), drives each through the same
 * JSON-RPC handshake {@link MentorRunnerClient} drives in prod, and captures three classes of
 * metric per session:
 *
 * <ul>
 *   <li><b>Cold-start</b> wall-clock (spawn → {@code runner_ready} → {@code hello.result})</li>
 *   <li><b>Per-runner RSS</b> sampled every 250 ms from {@code /proc/$pid/status} for the
 *       runner's lifetime — captures peak + steady-state.</li>
 *   <li><b>Turn latency</b> (prompt → {@code agent_end}) against the live LLM.</li>
 * </ul>
 *
 * <p>This deliberately bypasses Docker (Path C of the stress plan). The runner footprint
 * measured here is the FLOOR for sizing the per-(userId, workspaceId) container — Docker adds
 * cgroup overhead + image base layer but does NOT change Node/Pi-SDK heap. Multiplying these
 * numbers by the configured ceiling (`maxSessionsTotal=50`) tells us whether the policy
 * matches reality.
 *
 * <p>Run with: {@code N=10 ./mvnw -Plive-tests test -Dtest=MentorSandboxStressTest}.
 * Defaults to N=5 to stay polite on shared infra.
 */
@LiveLlmTest
@DisplayName("Mentor sandbox stress — multi-user runner footprint")
class MentorSandboxStressTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PI_SDK_VERSION = "0.74.0";
    private static final Path SDK_DIR = Path.of("target", "pi-sdk").toAbsolutePath();
    private static final Path RUNNER = Path.of("src", "main", "resources", "agent", "pi-mentor-runner.mjs")
        .toAbsolutePath();
    /** Per-session deadline: cold-start + handshake + prompt + agent_end against live LLM. */
    private static final Duration SESSION_BUDGET = Duration.ofSeconds(120);

    private final List<Path> stagedWorkspaces = new CopyOnWriteArrayList<>();
    private final List<StdioAttachedSandbox> sandboxes = new CopyOnWriteArrayList<>();

    @AfterEach
    void teardown() {
        // Close all sandboxes in parallel — sequential close on 25 runners would serialise 25×
        // graceTimeout (50 s total) for no good reason.
        var closes = sandboxes
            .stream()
            .map(s -> CompletableFuture.runAsync(() -> s.close(Duration.ofSeconds(2))))
            .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(closes).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Best-effort.
        }
        sandboxes.clear();
        for (Path ws : stagedWorkspaces) {
            try (var stream = Files.walk(ws)) {
                stream
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
            } catch (IOException ignored) {}
        }
        stagedWorkspaces.clear();
    }

    @Test
    @DisplayName("N concurrent runners: capture cold-start, RSS, turn latency")
    void stressNConcurrentRunners() throws Exception {
        int n = Integer.parseInt(System.getenv().getOrDefault("N", "5"));
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        ensurePiSdkInstalled();

        ScheduledExecutorService sampler = Executors.newScheduledThreadPool(2);
        try {
            var sessions = new ArrayList<SessionMetrics>(n);
            for (int i = 0; i < n; i++) sessions.add(new SessionMetrics(i));

            // Spawn all N sandboxes in parallel — measures fan-out at burst, not sequential.
            long t0 = System.nanoTime();
            var spawnFutures = new ArrayList<CompletableFuture<Void>>(n);
            for (var session : sessions) {
                spawnFutures.add(
                    CompletableFuture.runAsync(() -> {
                        try {
                            runOneSession(creds, session, sampler);
                        } catch (Exception e) {
                            session.failure = e;
                        }
                    })
                );
            }
            CompletableFuture.allOf(spawnFutures.toArray(CompletableFuture[]::new))
                .get(SESSION_BUDGET.toSeconds() + 30, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            report(n, elapsedMs, sessions);

            int failed = (int) sessions.stream().filter(s -> s.failure != null).count();
            if (failed > 0) {
                throw new AssertionError("stress test failed: " + failed + "/" + n + " sessions errored");
            }
        } finally {
            sampler.shutdownNow();
        }
    }

    private void runOneSession(LiveLlmCredentials creds, SessionMetrics session, ScheduledExecutorService sampler)
        throws Exception {
        Path ws = stageWorkspace(creds, session.id);
        stagedWorkspaces.add(ws);

        session.spawnStartNanos = System.nanoTime();
        StdioAttachedSandbox sandbox = spawnRunner(creds, ws);
        sandboxes.add(sandbox);
        session.runnerPid = sandbox.process().pid();

        // Sample RSS / Threads every 250 ms while the runner is alive. Stored as a flat list of
        // (timestampMs, rssKb, threads) tuples — small enough to keep in memory for ~120 s × 4 Hz.
        var sampleFuture = sampler.scheduleAtFixedRate(
            () -> {
                if (!sandbox.process().isAlive()) return;
                try {
                    long[] sample = readProcStatus(session.runnerPid);
                    session.samples.add(sample);
                } catch (IOException ignored) {}
            },
            0,
            250,
            TimeUnit.MILLISECONDS
        );

        try {
            var driver = new RunnerDriver(sandbox);
            driver.expectRunnerReady(Duration.ofSeconds(30));
            session.readyNanos = System.nanoTime();

            driver.helloOk(Duration.ofSeconds(10));
            session.helloNanos = System.nanoTime();

            UUID threadId = UUID.randomUUID();
            driver.openThread(threadId, Duration.ofSeconds(30));
            session.openThreadNanos = System.nanoTime();

            // Listen for agent_end (turn complete signal) for this thread.
            CompletableFuture<Void> turnComplete = new CompletableFuture<>();
            sandbox.subscribe(frame -> {
                if (!"event".equals(frame.path("method").asText())) return;
                if (!threadId.toString().equals(frame.path("params").path("threadId").asText())) return;
                if ("agent_end".equals(frame.path("params").path("event").path("type").asText())) {
                    turnComplete.complete(null);
                }
            });

            driver.prompt(threadId, "Answer in exactly one sentence: what is dependency injection?", Duration.ofSeconds(10));
            session.promptAcceptedNanos = System.nanoTime();

            turnComplete.get(SESSION_BUDGET.toSeconds(), TimeUnit.SECONDS);
            session.agentEndNanos = System.nanoTime();
        } finally {
            sampleFuture.cancel(false);
        }
    }

    private void report(int n, long elapsedMs, List<SessionMetrics> sessions) {
        System.out.println("");
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.printf("STRESS REPORT — N=%d concurrent runners (wall-clock %d ms)%n", n, elapsedMs);
        System.out.println("══════════════════════════════════════════════════════════════════");

        // Cold-start: spawn → ready
        long[] coldStarts = sessions
            .stream()
            .filter(s -> s.readyNanos > 0)
            .mapToLong(s -> (s.readyNanos - s.spawnStartNanos) / 1_000_000)
            .sorted()
            .toArray();
        // Hello round-trip
        long[] helloMs = sessions
            .stream()
            .filter(s -> s.helloNanos > 0)
            .mapToLong(s -> (s.helloNanos - s.readyNanos) / 1_000_000)
            .sorted()
            .toArray();
        // open_thread
        long[] openMs = sessions
            .stream()
            .filter(s -> s.openThreadNanos > 0)
            .mapToLong(s -> (s.openThreadNanos - s.helloNanos) / 1_000_000)
            .sorted()
            .toArray();
        // Turn latency: prompt accepted → agent_end
        long[] turnMs = sessions
            .stream()
            .filter(s -> s.agentEndNanos > 0)
            .mapToLong(s -> (s.agentEndNanos - s.promptAcceptedNanos) / 1_000_000)
            .sorted()
            .toArray();

        // Per-session peak RSS.
        long[] peakRssKb = sessions
            .stream()
            .filter(s -> !s.samples.isEmpty())
            .mapToLong(s -> s.samples.stream().mapToLong(arr -> arr[1]).max().orElse(0))
            .sorted()
            .toArray();
        long[] peakThreads = sessions
            .stream()
            .filter(s -> !s.samples.isEmpty())
            .mapToLong(s -> s.samples.stream().mapToLong(arr -> arr[2]).max().orElse(0))
            .sorted()
            .toArray();

        printRow("cold-start (spawn→ready)", "ms", coldStarts);
        printRow("hello round-trip", "ms", helloMs);
        printRow("open_thread round-trip", "ms", openMs);
        printRow("turn (prompt→agent_end)", "ms", turnMs);
        printRow("peak RSS per runner", "KB", peakRssKb);
        printRow("peak threads per runner", "", peakThreads);

        long totalPeakRssMb = (peakRssKb.length == 0
                ? 0L
                : java.util.stream.LongStream.of(peakRssKb).sum() / 1024L);
        System.out.printf("%n  ▸ aggregate peak RSS across all %d runners: %d MB%n", peakRssKb.length, totalPeakRssMb);

        long failed = sessions.stream().filter(s -> s.failure != null).count();
        System.out.printf("  ▸ failures: %d / %d%n", failed, n);
        for (var s : sessions) {
            if (s.failure != null) {
                System.out.printf("    [session %d, pid %d] %s%n", s.id, s.runnerPid, s.failure);
            }
        }
        System.out.println("══════════════════════════════════════════════════════════════════");
    }

    private static void printRow(String label, String unit, long[] sortedSamples) {
        if (sortedSamples.length == 0) {
            System.out.printf("  %-30s n=0 (no samples)%n", label);
            return;
        }
        long p50 = sortedSamples[Math.min(sortedSamples.length - 1, sortedSamples.length / 2)];
        long p95 = sortedSamples[(int) Math.min(sortedSamples.length - 1L, Math.round(sortedSamples.length * 0.95))];
        long p99 = sortedSamples[(int) Math.min(sortedSamples.length - 1L, Math.round(sortedSamples.length * 0.99))];
        long min = sortedSamples[0];
        long max = sortedSamples[sortedSamples.length - 1];
        long mean = java.util.stream.LongStream.of(sortedSamples).sum() / sortedSamples.length;
        System.out.printf(
            "  %-30s n=%3d  min=%6d  p50=%6d  p95=%6d  p99=%6d  max=%6d  mean=%6d  %s%n",
            label,
            sortedSamples.length,
            min,
            p50,
            p95,
            p99,
            max,
            mean,
            unit
        );
    }

    /** Read VmRSS (KB) and Threads from /proc/$pid/status. Returns [-1, rssKb, threads]. */
    private static long[] readProcStatus(long pid) throws IOException {
        Path status = Path.of("/proc", String.valueOf(pid), "status");
        if (!Files.exists(status)) {
            return new long[] { System.currentTimeMillis(), -1L, -1L };
        }
        long rssKb = -1L;
        long threads = -1L;
        for (String line : Files.readAllLines(status)) {
            if (line.startsWith("VmRSS:")) {
                rssKb = Long.parseLong(line.split("\\s+")[1]);
            } else if (line.startsWith("Threads:")) {
                threads = Long.parseLong(line.split("\\s+")[1]);
            }
            if (rssKb > 0 && threads > 0) break;
        }
        return new long[] { System.currentTimeMillis(), rssKb, threads };
    }

    private static void ensurePiSdkInstalled() throws Exception {
        Path marker = SDK_DIR.resolve(".installed-" + PI_SDK_VERSION);
        if (Files.exists(marker)) return;
        throw new IllegalStateException(
            "Pi SDK not installed at " + SDK_DIR + " — run MentorLiveLlmTest first to install it"
        );
    }

    private Path stageWorkspace(LiveLlmCredentials creds, int idx) throws IOException {
        Path tmp = Files.createTempDirectory("hephaestus-mentor-stress-" + idx + "-");
        Files.createDirectories(tmp.resolve(".sessions"));
        Files.createSymbolicLink(tmp.resolve("node_modules"), SDK_DIR.resolve("node_modules"));
        Files.copy(RUNNER, tmp.resolve("pi-mentor-runner.mjs"));

        Path systemPromptDir = tmp.resolve("agent").resolve("mentor");
        Files.createDirectories(systemPromptDir);
        Files.writeString(
            systemPromptDir.resolve("system.md"),
            "You are a software engineering mentor. Answer concisely.\n"
        );

        PiRuntimeFactory factory = new PiRuntimeFactory(MAPPER);
        PiPlanSpec spec = new PiPlanSpec(
            LlmProvider.OPENAI,
            CredentialMode.API_KEY,
            creds.apiKey(),
            creds.model(),
            creds.baseUrl(),
            null,
            true,
            300,
            "pi-mentor-runner.mjs",
            Map.of(),
            ""
        );
        byte[] settingsBytes = factory.buildPiSettingsJson(spec.provider(), spec.modelName(), true);
        byte[] extensionBytes = factory.buildExtensionFile(spec);

        Path piHome = tmp.resolve(".pi-home");
        Files.createDirectories(piHome);
        Files.write(piHome.resolve("settings.json"), settingsBytes);
        Path extDir = piHome.resolve("extensions");
        Files.createDirectories(extDir);
        Files.write(extDir.resolve("hephaestus-provider.ts"), extensionBytes);
        return tmp;
    }

    private StdioAttachedSandbox spawnRunner(LiveLlmCredentials creds, Path workspace) throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        Map<String, String> env = pb.environment();
        env.putAll(creds.asProcessEnv());
        env.put("PI_HEPHAESTUS_BASE_URL", creds.baseUrl());
        env.put("PI_HEPHAESTUS_API_KEY", creds.apiKey());
        env.put("PI_HEPHAESTUS_MODEL", creds.model());
        env.put("PI_CODING_AGENT_DIR", workspace.resolve(".pi-home").toString());
        pb.directory(workspace.toFile());

        Path shim = workspace.resolve("runner-entry.mjs");
        Path stagedRunner = workspace.resolve("pi-mentor-runner.mjs");
        Files.writeString(shim, buildRunnerShim(workspace, stagedRunner));
        pb.command("node", shim.toString());
        pb.redirectErrorStream(false);
        Process process = pb.start();
        return new StdioAttachedSandbox(
            UUID.randomUUID(),
            "stress-user-" + workspace.getFileName(),
            "stress-ws",
            process
        );
    }

    private static String buildRunnerShim(Path workspace, Path runner) {
        return ("""
        import path from "node:path";
        import fs from "node:fs";
        const WORKSPACE_REAL = __WORKSPACE__;
        function rewrite(p) {
            if (typeof p !== "string") return p;
            if (p === "/workspace") return WORKSPACE_REAL;
            if (p.startsWith("/workspace/")) return WORKSPACE_REAL + p.substring("/workspace".length);
            return p;
        }
        const origExists = fs.existsSync; fs.existsSync = (p) => origExists(rewrite(p));
        const origMkdir = fs.mkdirSync; fs.mkdirSync = (p, opts) => origMkdir(rewrite(p), opts);
        const origReadFile = fs.readFileSync; fs.readFileSync = (p, opts) => origReadFile(rewrite(p), opts);
        await import(__RUNNER_URL__);
        """).replace("__WORKSPACE__", "\"" + workspace.toString().replace("\\", "\\\\") + "\"")
            .replace("__RUNNER_URL__", "\"" + runner.toUri() + "\"");
    }

    /** Per-session metric capture. All times are System.nanoTime() ticks; samples are /proc snapshots. */
    private static final class SessionMetrics {

        final int id;
        long runnerPid;
        long spawnStartNanos;
        long readyNanos;
        long helloNanos;
        long openThreadNanos;
        long promptAcceptedNanos;
        long agentEndNanos;
        final List<long[]> samples = new CopyOnWriteArrayList<>();
        volatile Throwable failure;

        SessionMetrics(int id) {
            this.id = id;
        }
    }

    /** Mirror of MentorLiveLlmTest.RunnerDriver but with explicit per-call deadlines. */
    private static final class RunnerDriver {

        private final StdioAttachedSandbox sandbox;
        private final java.util.concurrent.ConcurrentLinkedQueue<JsonNode> responses = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final java.util.concurrent.ConcurrentLinkedQueue<JsonNode> ready = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final AtomicLong idGen = new AtomicLong();
        private final ConcurrentHashMap<Long, Object> requestSent = new ConcurrentHashMap<>();

        RunnerDriver(StdioAttachedSandbox sandbox) {
            this.sandbox = sandbox;
            sandbox.subscribe(frame -> {
                if (frame.has("id") && (frame.has("result") || frame.has("error"))) {
                    responses.add(frame);
                } else if (
                    "event".equals(frame.path("method").asText()) &&
                    "runner_ready".equals(frame.path("params").path("event").path("type").asText())
                ) {
                    ready.add(frame);
                }
            });
        }

        void expectRunnerReady(Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (!ready.isEmpty()) return;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted waiting for runner_ready");
                }
            }
            throw new IllegalStateException("runner_ready not received within " + timeout);
        }

        void helloOk(Duration timeout) {
            JsonNode resp = call("hello", MAPPER.createObjectNode(), timeout);
            int version = resp.path("result").path("protocolVersion").asInt(0);
            if (version != 1) {
                throw new IllegalStateException("expected protocolVersion=1, got " + version);
            }
        }

        void openThread(UUID threadId, Duration timeout) {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("threadId", threadId.toString());
            JsonNode resp = call("open_thread", params, timeout);
            if (!threadId.toString().equals(resp.path("result").path("threadId").asText())) {
                throw new IllegalStateException("open_thread did not echo threadId");
            }
        }

        void prompt(UUID threadId, String text, Duration timeout) {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("threadId", threadId.toString());
            params.put("text", text);
            JsonNode resp = call("prompt", params, timeout);
            if (!resp.path("result").path("accepted").asBoolean()) {
                throw new IllegalStateException("prompt was not accepted");
            }
        }

        private JsonNode call(String method, JsonNode params, Duration timeout) {
            long id = idGen.incrementAndGet();
            ObjectNode req = MAPPER.createObjectNode();
            req.put("jsonrpc", "2.0");
            req.put("id", id);
            req.put("method", method);
            req.set("params", params);
            requestSent.put(id, Boolean.TRUE);
            sandbox.send(req);
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (JsonNode c : responses) {
                    if (c.path("id").asLong(-1) == id) {
                        responses.remove(c);
                        if (c.has("error")) {
                            throw new IllegalStateException("runner error: " + c.path("error"));
                        }
                        return c;
                    }
                }
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted waiting for response to " + method);
                }
            }
            throw new IllegalStateException(method + " response not received within " + timeout);
        }
    }
}
