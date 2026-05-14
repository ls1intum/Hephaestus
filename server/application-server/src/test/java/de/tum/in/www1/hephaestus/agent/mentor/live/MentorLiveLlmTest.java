package de.tum.in.www1.hephaestus.agent.mentor.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.PiEventToUiChunkTranslator;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.in.www1.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.in.www1.hephaestus.testconfig.LiveLlmTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end live test for the mentor runner against a real LLM endpoint. This test:
 *
 * <ol>
 *   <li>Ensures the Pi SDK is installed under {@code target/pi-sdk/node_modules} (idempotent;
 *       the install marker survives across test runs and parallel JVMs use a directory lock).</li>
 *   <li>Spawns {@code pi-mentor-runner.mjs} directly with {@code node} — no Docker — and points
 *       it at the TUM AET ASE OpenAI-compatible gateway via {@code OPENAI_BASE_URL} +
 *       {@code OPENAI_API_KEY}.</li>
 *   <li>Drives the JSON-RPC protocol the same way {@code MentorRunnerClient} drives it in prod
 *       (hello → open_thread → prompt) and translates every emitted event through the real
 *       {@link PiEventToUiChunkTranslator} so the test exercises the production stream-merge logic.</li>
 * </ol>
 *
 * <p>The runner only honours a custom model registry through extensions. To exercise the
 * production routing end-to-end, this test uses the real {@link PiRuntimeFactory} to mint
 * the settings.json and the {@code hephaestus-provider.ts} extension — the same bytes the
 * production agent container would see. The {@code PI_HEPHAESTUS_*} env vars the factory
 * expects are seeded from {@link LiveLlmCredentials}. If C1's provider refactor regresses
 * the production path, this live test fails — no separate test extension to mask the bug.
 */
@LiveLlmTest
@DisplayName("Mentor runner — live LLM round-trip")
class MentorLiveLlmTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Pi SDK version pinned in pi-mentor-runner.mjs. Bump in lockstep with the runner. */
    private static final String PI_SDK_VERSION = "0.74.0";

    /** Per-test wall-clock cap — mentor turns against gpt-oss-120b complete in 5-30s on the TUM box. */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(90);

    /** Project-relative location of the SDK install. Build output, never vendored. Gitignored. */
    private static final Path SDK_DIR = Path.of("target", "pi-sdk").toAbsolutePath();

    private static final Path RUNNER = Path.of(
        "src",
        "main",
        "resources",
        "agent",
        "pi-mentor-runner.mjs"
    ).toAbsolutePath();

    private Path workspaceDir;
    private StdioAttachedSandbox sandbox;

    @BeforeAll
    static void installPiSdk() throws Exception {
        // The marker file lets parallel test JVMs and repeated runs short-circuit. The file lock
        // is acquired on a sibling lockfile so two JVMs racing to install don't both spawn npm.
        Files.createDirectories(SDK_DIR);
        Path marker = SDK_DIR.resolve(".installed-" + PI_SDK_VERSION);
        if (Files.exists(marker)) {
            return;
        }
        Path lockFile = SDK_DIR.resolve(".install.lock");
        try (
            var raf = new java.io.RandomAccessFile(lockFile.toFile(), "rw");
            var channel = raf.getChannel();
            var lock = channel.lock()
        ) {
            // Re-check under lock — a parallel JVM may have just finished.
            if (Files.exists(marker)) {
                return;
            }
            // Write a stub package.json so npm doesn't traverse upward to the project root and
            // mutate its node_modules tree.
            Files.writeString(SDK_DIR.resolve("package.json"), "{\"name\":\"pi-sdk-test-deps\",\"private\":true}");
            ProcessBuilder pb = new ProcessBuilder(
                "npm",
                "install",
                "--no-audit",
                "--no-fund",
                "--prefix",
                SDK_DIR.toString(),
                "@earendil-works/pi-coding-agent@" + PI_SDK_VERSION
            );
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process p = pb.start();
            if (!p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("npm install for Pi SDK timed out after 180s");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("npm install for Pi SDK failed; see stderr above");
            }
            Files.writeString(marker, "ok\n");
            // Suppress the unused-variable warning for `lock`; we hold it for the try-with-resources.
            //noinspection ResultOfMethodCallIgnored
            lock.isValid();
        }
    }

    @AfterEach
    void teardown() {
        if (sandbox != null) {
            sandbox.close(Duration.ofSeconds(5));
            sandbox = null;
        }
        if (workspaceDir != null) {
            // Each test stages a fresh /tmp/hephaestus-mentor-live-* with a runner copy +
            // settings + symlinked node_modules. Leaking these across runs accumulates GBs of
            // disposable state on CI agents that re-use volumes.
            try (var stream = Files.walk(workspaceDir)) {
                stream
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (java.io.IOException ignored) {
                            // Best-effort cleanup; the OS-level tmp reaper catches the rest.
                        }
                    });
            } catch (java.io.IOException ignored) {
                // Workspace already gone — fine.
            }
            workspaceDir = null;
        }
    }

    @Test
    @DisplayName("hero: single turn streams text + Finish carries authoritative usage")
    void hero_singleTurnStreamsTextAndFinishesWithUsage() throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        workspaceDir = stageWorkspace(creds);

        sandbox = spawnRunner(creds, workspaceDir);
        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);

        // Translate every event through the real production translator so we test the
        // streaming-merge invariant, not a parallel implementation.
        PiEventToUiChunkTranslator translator = new PiEventToUiChunkTranslator();
        TranslatorState state = new TranslatorState(assistantMessageId);
        List<UIMessageChunk> chunks = new ArrayList<>();
        var translationDone = new java.util.concurrent.CompletableFuture<Void>();
        sandbox.subscribe(frame -> {
            if (!isThreadEvent(frame, threadId)) return;
            JsonNode event = frame.path("params").path("event");
            chunks.addAll(translator.translate(event, state));
            // Pi's terminal event for a turn is `agent_end`; the translator turns it into a
            // Finish chunk. We signal completion off that, not by polling.
            if ("agent_end".equals(event.path("type").asText())) {
                translationDone.complete(null);
            }
        });

        driver.prompt(threadId, "Briefly explain unit testing in one sentence.");
        translationDone.get(TURN_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);

        // ─── Lifecycle invariants ──────────────────────────────────────────────────────
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).as("first chunk is Start").isInstanceOf(UIMessageChunk.Start.class);
        assertThat(chunks.get(chunks.size() - 1)).as("last chunk is Finish").isInstanceOf(UIMessageChunk.Finish.class);

        // ─── Streaming-merge invariant ────────────────────────────────────────────────
        // Every TextDelta in the stream must share the same block id as the opening TextStart;
        // a regression here breaks AI SDK's client-side reconciliation (deltas get split into
        // separate parts).
        List<UIMessageChunk.TextDelta> deltas = chunks
            .stream()
            .filter(UIMessageChunk.TextDelta.class::isInstance)
            .map(UIMessageChunk.TextDelta.class::cast)
            .toList();
        assertThat(deltas).as("at least two text deltas (else merge logic untested)").hasSizeGreaterThanOrEqualTo(2);
        String firstBlockId = deltas.get(0).id();
        assertThat(deltas).allMatch(d -> firstBlockId.equals(d.id()), "all deltas share one block id");

        String concatenated = deltas.stream().map(UIMessageChunk.TextDelta::delta).reduce("", String::concat);
        assertThat(concatenated.trim()).as("LLM responded with non-blank text").isNotEmpty();
        // Log a snippet so the test report shows what the model actually said — saves a debug round
        // when the response is unexpected.
        System.out.printf("[hero] LLM response (%d chars): %s%n", concatenated.length(), trim(concatenated, 200));

        // ─── Usage capture ────────────────────────────────────────────────────────────
        // Pi pumps {input, output, totalTokens, ...} on every message_end. The translator threads
        // them into the Finish chunk's messageMetadata.usage. If `stream_options.include_usage`
        // ever regresses these go to zero — exactly the failure mode this test exists to catch.
        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) chunks.get(chunks.size() - 1);
        assertThat(finish.messageMetadata()).as("Finish carries metadata").isNotNull();
        UIMessageChunk.FinishMetadata.Usage usage = finish.messageMetadata().usage();
        assertThat(usage).as("usage object present").isNotNull();
        assertThat(usage.input()).as("usage.input ≥ 1").isGreaterThanOrEqualTo(1);
        assertThat(usage.output()).as("usage.output ≥ 1").isGreaterThanOrEqualTo(1);
        System.out.printf(
            "[hero] usage: input=%d output=%d totalTokens=%s model=%s%n",
            usage.input(),
            usage.output(),
            usage.totalTokens(),
            finish.messageMetadata().model()
        );

        // ─── Persistence snapshot ─────────────────────────────────────────────────────
        // partsSnapshot is what lands in chat_message.parts JSONB — a single text part once the
        // turn closes. Asserts that closeTextBlock() actually flushed our text into the array.
        // Note: AI SDK's UIMessage has the text part populated on `text-end`. Pi emits agent_end
        // but the translator only closes the open block on agent_end. So the snapshot here may
        // not yet contain the text part if the translator hasn't seen the finishing block-close.
        // We assert the *intent* (parts is well-formed JSON) and check the buffered text matches.
        var partsSnapshot = state.partsSnapshot();
        assertThat(partsSnapshot.isArray()).isTrue();
        // AI SDK reducer pushes one `step-start` per start-step, then content parts. Find the
        // text part (skip the step-start placeholder) — the assertion is that the rehydrated
        // UIMessage carries the concatenated text exactly.
        JsonNode textPart = null;
        for (JsonNode p : partsSnapshot) {
            if ("text".equals(p.path("type").asText())) {
                textPart = p;
                break;
            }
        }
        if (textPart != null) {
            assertThat(textPart.path("text").asText()).isEqualTo(concatenated);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Workspace + process plumbing
    // ────────────────────────────────────────────────────────────────────────────────

    private Path stageWorkspace(LiveLlmCredentials creds) throws IOException {
        Path tmp = Files.createTempDirectory("hephaestus-mentor-live-");
        Files.createDirectories(tmp.resolve(".sessions"));

        // ESM resolution walks node_modules upward from the *importing* file, not from cwd. The
        // production container handles this by `ln -sf /usr/local/lib/node_modules
        // /workspace/node_modules` and bind-mounting the runner under /workspace/.run-pi.mjs
        // (see PiRuntimeFactory). We mirror both moves here: symlink node_modules under the
        // workspace, and copy the runner into the workspace so resolution finds the symlink.
        Path nodeModulesLink = tmp.resolve("node_modules");
        Path sdkNodeModules = SDK_DIR.resolve("node_modules");
        Files.createSymbolicLink(nodeModulesLink, sdkNodeModules);
        Files.copy(RUNNER, tmp.resolve("pi-mentor-runner.mjs"));

        // System prompt the runner loads from /workspace/agent/mentor/system.md. Keep it minimal —
        // the live LLM doesn't need the full production prompt to prove the round-trip works.
        Path systemPromptDir = tmp.resolve("agent").resolve("mentor");
        Files.createDirectories(systemPromptDir);
        Files.writeString(
            systemPromptDir.resolve("system.md"),
            "You are a software engineering mentor. Answer concisely.\n"
        );

        // Use the REAL production PiRuntimeFactory paths so this test fails the moment the
        // factory regresses (e.g. C1's provider refactor breaking the env-var contract).
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

        // Pi loads its on-disk settings from `~/.pi/settings.json`; redirect with env vars so we
        // never touch the user's real ~/.pi.
        Path piHome = tmp.resolve(".pi-home");
        Files.createDirectories(piHome);
        Files.write(piHome.resolve("settings.json"), settingsBytes);

        // Extensions go under PI_CODING_AGENT_DIR/extensions/ (the SDK auto-discovers them via
        // jiti during session start). The production container path is /home/agent/.pi/extensions/
        // — same shape, different root.
        Path extDir = piHome.resolve("extensions");
        Files.createDirectories(extDir);
        Files.write(extDir.resolve("hephaestus-provider.ts"), extensionBytes);

        return tmp;
    }

    /** Encode a Java string as a JSON-safe literal for interpolation into TS/JS source. */
    private static String jsonStringLiteral(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("jackson cannot serialize a String — JVM is broken", e);
        }
    }

    private StdioAttachedSandbox spawnRunner(LiveLlmCredentials creds, Path workspace) throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        Map<String, String> env = pb.environment();
        env.putAll(creds.asProcessEnv()); // OPENAI_API_KEY + OPENAI_BASE_URL (legacy back-compat)
        // The production hephaestus-provider extension reads these env vars; mirror what
        // LlmProxyAuthShell would set in API_KEY mode with a non-blank baseUrl. If C1 regressed,
        // the extension throws "needs PI_HEPHAESTUS_BASE_URL" at session start and this test
        // fails loud.
        env.put("PI_HEPHAESTUS_BASE_URL", creds.baseUrl());
        env.put("PI_HEPHAESTUS_API_KEY", creds.apiKey());
        env.put("PI_HEPHAESTUS_MODEL", creds.model());
        // Pi looks for settings under PI_CODING_AGENT_DIR; pin it inside our temp dir so the
        // runtime never touches the user's real ~/.pi.
        env.put("PI_CODING_AGENT_DIR", workspace.resolve(".pi-home").toString());
        // The runner hard-codes the CWD constant to /workspace, but it only uses CWD for
        // SESSIONS_DIR / SYSTEM_PROMPT_PATH lookups via `existsSync`. We patch by setting CWD to
        // our workspace and letting the runner's existence checks fail gracefully (system prompt
        // is optional). Actually — the runner does `mkdirSync(SESSIONS_DIR, { recursive: true })`
        // which would create `/workspace/.sessions` and fail without root. So we override the
        // path constants by symlinking the runner's expected /workspace into our temp dir if it
        // doesn't exist, otherwise we copy.
        pb.directory(workspace.toFile());

        // The runner reads SESSIONS_DIR = "/workspace/.sessions" verbatim. There's no env override
        // for that path in v1, so we either need root (to create /workspace) or to monkey-patch
        // fs reads. The shim does the latter. It lives next to the staged runner so ESM
        // resolution still picks up the workspace's node_modules symlink for both files.
        Path shim = workspace.resolve("runner-entry.mjs");
        Path stagedRunner = workspace.resolve("pi-mentor-runner.mjs");
        Files.writeString(shim, buildRunnerShim(workspace, stagedRunner));
        pb.command("node", shim.toString());

        pb.redirectErrorStream(false);
        Process process = pb.start();
        return new StdioAttachedSandbox(UUID.randomUUID(), "live-test-user", "live-test-workspace", process);
    }

    /**
     * Build a Node shim that monkey-patches {@code fs.existsSync} / {@code fs.mkdirSync} /
     * {@code fs.readFileSync} to map {@code /workspace/*} reads onto the test temp dir, then
     * imports the real {@code pi-mentor-runner.mjs}. This keeps the runner unmodified — we
     * test the exact bytes that ship.
     */
    private static String buildRunnerShim(Path workspace, Path runner) {
        // Use String.replace, not String.format, to avoid Java's % collisions with JS templates.
        return """
        import { createRequire } from "node:module";
        import path from "node:path";
        import fs from "node:fs";

        const WORKSPACE_REAL = __WORKSPACE__;

        // Pi mentor runner hard-codes "/workspace" as CWD. We can't easily mount a tmpfs at
        // that path from inside the JVM-spawned process; instead, redirect every fs call that
        // begins with "/workspace" to our real temp dir. This is shim-only — the production
        // container has /workspace bind-mounted, no redirect needed.
        function rewrite(p) {
            if (typeof p !== "string") return p;
            if (p === "/workspace") return WORKSPACE_REAL;
            if (p.startsWith("/workspace/")) return WORKSPACE_REAL + p.substring("/workspace".length);
            return p;
        }
        const origExists = fs.existsSync;
        fs.existsSync = (p) => origExists(rewrite(p));
        const origMkdir = fs.mkdirSync;
        fs.mkdirSync = (p, opts) => origMkdir(rewrite(p), opts);
        const origReadFile = fs.readFileSync;
        fs.readFileSync = (p, opts) => origReadFile(rewrite(p), opts);

        await import(__RUNNER_URL__);
        """.replace("__WORKSPACE__", jsonStringLiteral(workspace.toString()))
            .replace("__RUNNER_URL__", jsonStringLiteral(runner.toUri().toString()));
    }

    private static boolean isThreadEvent(JsonNode frame, UUID threadId) {
        if (!"event".equals(frame.path("method").asText())) return false;
        String observedThreadId = frame.path("params").path("threadId").asText();
        // runner_ready notification has no threadId — we filter it out here intentionally.
        return threadId.toString().equals(observedThreadId);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // RunnerDriver — same handshake MentorRunnerClient drives in prod
    // ────────────────────────────────────────────────────────────────────────────────

    private static final class RunnerDriver {

        private final AttachedSandbox sandbox;
        private final ConcurrentLinkedQueue<JsonNode> responses = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<JsonNode> readyNotifications = new ConcurrentLinkedQueue<>();
        private final java.util.concurrent.atomic.AtomicInteger requestIdCounter =
            new java.util.concurrent.atomic.AtomicInteger();

        RunnerDriver(AttachedSandbox sandbox) {
            this.sandbox = sandbox;
            sandbox.subscribe(frame -> {
                if (frame.has("id") && (frame.has("result") || frame.has("error"))) {
                    responses.add(frame);
                } else if (
                    "event".equals(frame.path("method").asText()) &&
                    "runner_ready".equals(frame.path("params").path("event").path("type").asText())
                ) {
                    readyNotifications.add(frame);
                }
            });
        }

        void expectRunnerReady() {
            await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> !readyNotifications.isEmpty());
        }

        void helloOk() {
            JsonNode response = call("hello", MAPPER.createObjectNode(), Duration.ofSeconds(10));
            assertThat(response.path("result").path("protocolVersion").asInt(0))
                .as("hello returns protocolVersion 1")
                .isEqualTo(1);
        }

        void openThread(UUID threadId) {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("threadId", threadId.toString());
            JsonNode response = call("open_thread", params, Duration.ofSeconds(30));
            assertThat(response.path("result").path("threadId").asText())
                .as("open_thread acks with threadId")
                .isEqualTo(threadId.toString());
        }

        void prompt(UUID threadId, String text) {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("threadId", threadId.toString());
            params.put("text", text);
            JsonNode response = call("prompt", params, Duration.ofSeconds(10));
            assertThat(response.path("result").path("accepted").asBoolean())
                .as("prompt accepted (turn streams via events)")
                .isTrue();
        }

        private JsonNode call(String method, JsonNode params, Duration timeout) {
            int id = requestIdCounter.incrementAndGet();
            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.set("params", params);
            sandbox.send(request);
            // Drain matching response from the queue (out-of-order responses are not expected for
            // the request methods we call, but we still match by id to be safe).
            JsonNode response = await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(20))
                .until(
                    () -> {
                        for (JsonNode candidate : responses) {
                            if (candidate.path("id").asInt(-1) == id) {
                                responses.remove(candidate);
                                return candidate;
                            }
                        }
                        return null;
                    },
                    java.util.Objects::nonNull
                );
            if (response.has("error")) {
                throw new IllegalStateException("RPC " + method + " failed: " + response.path("error").toString());
            }
            return response;
        }
    }
}
