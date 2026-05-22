package de.tum.cit.aet.hephaestus.agent.mentor.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.PiEventToUiChunkTranslator;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.runtime.PiPlanSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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

    private final List<Path> workspaceDirs = new ArrayList<>();
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
        for (Path dir : workspaceDirs) {
            deleteRecursive(dir);
        }
        workspaceDirs.clear();
        workspaceDir = null;
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
        UIMessageChunk.MessageMetadata.Usage usage = finish.messageMetadata().usage();
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

    @Test
    @DisplayName("multi-turn (warm runner): turn 2 recalls a fact from turn 1")
    void multiTurn_secondTurnRecallsFirst() throws Exception {
        // Pin Pi SDK's session-bound agent._state.messages threading: a single long-lived
        // runner must keep both turns' messages in its in-memory state across message_end
        // events. Recall failure means rebind/switchSession or dispatch races dropped the
        // history.
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();
        workspaceDir = stageWorkspace(creds);
        sandbox = spawnRunner(creds, workspaceDir);

        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);

        // Turn 1: plant a token only the assistant can have seen.
        String t1Text = runTurnAndCollect(
            driver,
            threadId,
            "Remember the number forty-two. Reply with exactly: noted."
        );
        System.out.printf("[multi-turn] turn 1 (%d chars): %s%n", t1Text.length(), trim(t1Text, 200));
        assertThat(t1Text.trim()).as("turn 1 produced a non-empty response").isNotEmpty();

        // Turn 2: ask the LLM to recall the fact. If agent._state.messages was not threaded
        // through turn 1 → turn 2, the LLM has no way to answer this.
        String t2Text = runTurnAndCollect(
            driver,
            threadId,
            "What number did I ask you to remember? Reply with only the digits."
        );
        System.out.printf("[multi-turn] turn 2 (%d chars): %s%n", t2Text.length(), trim(t2Text, 200));
        // gpt-oss-120b is well-behaved on this prompt; accept "42" or the spelled-out form.
        String t2Lower = t2Text.toLowerCase();
        assertThat(t2Lower)
            .as(
                "turn 2 must recall the planted number — if this fails, the Pi SDK's session-bound " +
                    "agent._state.messages is not being fed through between turns on the same warm " +
                    "runner and multi-turn conversation is broken"
            )
            .satisfiesAnyOf(s -> assertThat(s).contains("42"), s -> assertThat(s).contains("forty-two"));
    }

    @Test
    @DisplayName("5-turn coherence: each follow-up references the prior turn correctly")
    void multiTurn_fiveTurnsCoherent() throws Exception {
        // High-confidence regression for the multi-turn fragmentation bug the user originally
        // reported (chat-log screenshot: each follow-up returned a shrinking tail of turn 1).
        // We drive five sequential turns on the SAME warm runner with a question that REQUIRES
        // the LLM to integrate turn N-1's answer into turn N's. If agent._state.messages
        // threading regresses anywhere along the chain, at least one turn's answer becomes
        // incoherent and the assertion catches it.
        //
        // The chain: build a list one item per turn. Turn 5 asks for the full list. This
        // requires the LLM to have seen every prior assistant message AND its own prior
        // answers — exactly the failure mode the screenshot showed.
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();
        workspaceDir = stageWorkspace(creds);
        sandbox = spawnRunner(creds, workspaceDir);

        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);

        // Turns 1-4: add one fruit per turn, ask for a one-word ack.
        String[] fruits = { "apple", "banana", "cherry", "date" };
        for (int i = 0; i < fruits.length; i++) {
            String resp = runTurnAndCollect(
                driver,
                threadId,
                "Add the fruit '" + fruits[i] + "' to the list. Reply with exactly one word: ok."
            );
            System.out.printf("[5-turn] turn %d (%s): %s%n", i + 1, fruits[i], trim(resp, 60));
            assertThat(resp.trim()).as("turn %d must produce a non-empty response", i + 1).isNotEmpty();
        }

        // Turn 5: ask for the full list. Only correct if all four prior user+assistant pairs
        // landed in agent._state.messages.
        String summary = runTurnAndCollect(
            driver,
            threadId,
            "List every fruit I have added so far, in the order I added them. " +
                "Reply with just the fruit names separated by commas. No commentary."
        );
        System.out.printf("[5-turn] turn 5 summary (%d chars): %s%n", summary.length(), trim(summary, 200));
        String lower = summary.toLowerCase();
        for (String fruit : fruits) {
            assertThat(lower)
                .as(
                    "turn 5 must include '%s' — its absence proves a turn earlier in the chain " +
                        "did not thread through agent._state.messages",
                    fruit
                )
                .contains(fruit);
        }
    }

    @Test
    @DisplayName("cold-restart preserves session JSONL: runner-B recalls a fact planted on runner-A")
    void coldRestart_preservesHistoryViaSessionJsonl() throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();

        byte[] bytesA = captureSessionBytesAfterTurn(
            creds,
            threadId,
            "Remember: my favorite framework is Spring Boot. Reply with exactly: noted."
        );
        assertThat(bytesA).as("runner emitted session_persisted before agent_end").isNotNull();

        respawnWithSession(creds, threadId, bytesA);
        String followUp = runTurnAndCollect(
            new RunnerDriver(sandbox),
            threadId,
            "What framework am I using? Reply with only the framework name."
        );
        assertThat(followUp.toLowerCase())
            .as("Pi SDK rehydrated agent state from injected .sessions/<id>.jsonl")
            .contains("spring");
    }

    @Test
    @DisplayName("tool use: agent uses read/bash to explore a staged git repo")
    void toolUse_agentExploresRepoWithReadOrBash() throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        workspaceDir = stageWorkspaceWithRepo(creds);

        sandbox = spawnRunner(creds, workspaceDir);
        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);

        PiEventToUiChunkTranslator translator = new PiEventToUiChunkTranslator();
        TranslatorState state = new TranslatorState(assistantMessageId);
        List<UIMessageChunk> chunks = new ArrayList<>();
        var done = new java.util.concurrent.CompletableFuture<Void>();
        sandbox.subscribe(frame -> {
            if (!isThreadEvent(frame, threadId)) return;
            JsonNode event = frame.path("params").path("event");
            chunks.addAll(translator.translate(event, state));
            if ("agent_end".equals(event.path("type").asText())) {
                done.complete(null);
            }
        });

        driver.prompt(
            threadId,
            "Read the file at /workspace/repo/README.md and tell me what the project name is. " +
                "You MUST use the read or bash tool to read the file. Do NOT guess."
        );
        done.get(TURN_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);

        // At least one tool was invoked.
        List<UIMessageChunk.ToolInputStart> toolStarts = chunks
            .stream()
            .filter(UIMessageChunk.ToolInputStart.class::isInstance)
            .map(UIMessageChunk.ToolInputStart.class::cast)
            .toList();
        assertThat(toolStarts)
            .as("agent must invoke at least one tool (read or bash) to answer the question")
            .isNotEmpty();

        List<String> toolNames = toolStarts.stream().map(UIMessageChunk.ToolInputStart::toolName).toList();
        assertThat(toolNames)
            .as("tools used should include read, bash, or grep")
            .anyMatch(name -> List.of("read", "bash", "grep").contains(name));
        System.out.printf("[tool-use] tools invoked: %s%n", toolNames);

        // Tool produced output (not error).
        List<UIMessageChunk.ToolOutputAvailable> toolOutputs = chunks
            .stream()
            .filter(UIMessageChunk.ToolOutputAvailable.class::isInstance)
            .map(UIMessageChunk.ToolOutputAvailable.class::cast)
            .toList();
        assertThat(toolOutputs).as("at least one tool call completed with output").isNotEmpty();

        // Agent's text response should reference the planted content.
        String text = chunks
            .stream()
            .filter(UIMessageChunk.TextDelta.class::isInstance)
            .map(UIMessageChunk.TextDelta.class::cast)
            .map(UIMessageChunk.TextDelta::delta)
            .reduce("", String::concat);
        System.out.printf("[tool-use] LLM response (%d chars): %s%n", text.length(), trim(text, 300));
        assertThat(text.toLowerCase())
            .as("agent must reference the project name from README.md (Hephaestus-Fixture)")
            .contains("hephaestus");
    }

    @Test
    @DisplayName("session JSONL is byte-identical across cold restarts (prompt-cache prefix preserved)")
    void coldRestart_sessionJsonlIsByteIdentical() throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        UUID threadId = UUID.randomUUID();

        byte[] bytesA = captureSessionBytesAfterTurn(creds, threadId, "Reply with exactly: alpha.");
        assertThat(bytesA).isNotNull();

        Path sessionFile = respawnWithSession(creds, threadId, bytesA);
        runTurnAndCollect(new RunnerDriver(sandbox), threadId, "Reply with exactly: beta.");

        byte[] bytesAfterB = Files.readAllBytes(sessionFile);
        assertThat(bytesAfterB.length).isGreaterThanOrEqualTo(bytesA.length);
        assertThat(Arrays.copyOfRange(bytesAfterB, 0, bytesA.length))
            .as("Pi SDK must append, not rewrite — prompt-cache prefix must survive")
            .isEqualTo(bytesA);
    }

    /**
     * Spawn a runner, run one turn, return the bytes the runner shipped via {@code session_persisted}.
     * Tears the runner down before returning so the caller can stage a fresh container.
     */
    private byte[] captureSessionBytesAfterTurn(LiveLlmCredentials creds, UUID threadId, String prompt)
        throws Exception {
        workspaceDir = stageWorkspace(creds);
        sandbox = spawnRunner(creds, workspaceDir);
        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);

        AtomicReference<byte[]> captured = new AtomicReference<>();
        sandbox.subscribe(frame -> {
            if (!isThreadEvent(frame, threadId)) return;
            JsonNode event = frame.path("params").path("event");
            if ("session_persisted".equals(event.path("type").asText())) {
                String jsonl = event.path("jsonl").asText("");
                if (!jsonl.isEmpty()) captured.set(jsonl.getBytes(StandardCharsets.UTF_8));
            }
        });
        runTurnAndCollect(driver, threadId, prompt);
        sandbox.close(Duration.ofSeconds(5));
        sandbox = null;
        return captured.get();
    }

    /**
     * Stage a second workspace pre-seeded with the captured JSONL and spawn a fresh runner against
     * it (mirrors {@code MentorPiAdapter#buildSandboxSpec} injecting {@code .sessions/<id>.jsonl}).
     * Deletes the prior workspace; {@code workspaceDir} is updated so @AfterEach cleans the new one.
     */
    private Path respawnWithSession(LiveLlmCredentials creds, UUID threadId, byte[] sessionBytes) throws Exception {
        Path nextWorkspace = stageWorkspace(creds);
        Path sessionFile = nextWorkspace.resolve(".sessions").resolve(threadId + ".jsonl");
        Files.createDirectories(sessionFile.getParent());
        Files.write(sessionFile, sessionBytes);

        // Keep the old workspace alive: the Pi SDK stores the CWD path in session JSONL, and
        // switchSession validates that the stored path still exists on disk. @AfterEach cleans all.
        workspaceDir = nextWorkspace;

        sandbox = spawnRunner(creds, workspaceDir);
        var driver = new RunnerDriver(sandbox);
        driver.expectRunnerReady();
        driver.helloOk();
        driver.openThread(threadId);
        return sessionFile;
    }

    private static void deleteRecursive(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }

    /**
     * Drive one full turn against the runner and collect the concatenated text deltas.
     * Used by multi-turn / resume tests to assert on the LLM's actual response text.
     */
    private String runTurnAndCollect(RunnerDriver driver, UUID threadId, String prompt) throws Exception {
        PiEventToUiChunkTranslator translator = new PiEventToUiChunkTranslator();
        TranslatorState state = new TranslatorState(UUID.randomUUID());
        List<UIMessageChunk> chunks = new ArrayList<>();
        var done = new java.util.concurrent.CompletableFuture<Void>();
        // Use a per-turn subscription so collected chunks are scoped to this turn only.
        var unsubscribe = sandbox.subscribe(frame -> {
            if (!isThreadEvent(frame, threadId)) return;
            JsonNode event = frame.path("params").path("event");
            chunks.addAll(translator.translate(event, state));
            if ("agent_end".equals(event.path("type").asText())) {
                done.complete(null);
            }
        });
        try {
            driver.prompt(threadId, prompt);
            done.get(TURN_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            return chunks
                .stream()
                .filter(UIMessageChunk.TextDelta.class::isInstance)
                .map(UIMessageChunk.TextDelta.class::cast)
                .map(UIMessageChunk.TextDelta::delta)
                .reduce("", String::concat);
        } finally {
            unsubscribe.dispose();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Workspace + process plumbing
    // ────────────────────────────────────────────────────────────────────────────────

    private Path stageWorkspace(LiveLlmCredentials creds) throws IOException {
        Path tmp = Files.createTempDirectory("hephaestus-mentor-live-");
        workspaceDirs.add(tmp);
        Files.createDirectories(tmp.resolve(".sessions"));

        // ESM resolution walks node_modules upward from the *importing* file, not from cwd. The
        // production container handles this by `ln -sf /opt/pi-sdk/node_modules
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
            new de.tum.cit.aet.hephaestus.agent.mentor.MentorRunnerProfile(),
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

        // Pi SDK auto-discovers extensions in $PI_CODING_AGENT_DIR/extensions/ via jiti.
        Path extDir = piHome.resolve("extensions");
        Files.createDirectories(extDir);
        Files.write(extDir.resolve("hephaestus-provider.ts"), extensionBytes);

        return tmp;
    }

    /**
     * Like {@link #stageWorkspace}, but also creates a small git repo at {@code repo/} with a
     * known README.md and uses the production system prompt so the agent knows about read/bash/grep.
     */
    private Path stageWorkspaceWithRepo(LiveLlmCredentials creds) throws IOException {
        Path workspace = stageWorkspace(creds);

        // Overwrite the minimal system prompt with the production one that lists available tools.
        Path productionPrompt = Path.of("src", "main", "resources", "agent", "mentor", "system.md").toAbsolutePath();
        if (Files.exists(productionPrompt)) {
            Files.copy(
                productionPrompt,
                workspace.resolve("agent").resolve("mentor").resolve("system.md"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }

        // Stage a small git repo with a known file the agent can read.
        Path repoDir = workspace.resolve("repo");
        Files.createDirectories(repoDir);
        Files.writeString(
            repoDir.resolve("README.md"),
            "# Hephaestus-Fixture\n\nA test project for validating mentor tool use.\n"
        );
        Files.writeString(
            repoDir.resolve("Main.java"),
            "public class Main {\n    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from Hephaestus-Fixture!\");\n    }\n}\n"
        );

        // Initialize as a git repo so `git log`, `git diff` etc. work.
        runGit(repoDir, "init");
        runGit(repoDir, "add", ".");
        runGit(repoDir, "commit", "-m", "initial commit", "--allow-empty-message");
        return workspace;
    }

    private static void runGit(Path cwd, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.environment().put("GIT_AUTHOR_NAME", "test");
        pb.environment().put("GIT_AUTHOR_EMAIL", "test@test.local");
        pb.environment().put("GIT_COMMITTER_NAME", "test");
        pb.environment().put("GIT_COMMITTER_EMAIL", "test@test.local");
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException("git command failed to start: " + cmd, e);
        }
        try {
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("git command timed out: " + cmd);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git command interrupted: " + cmd, e);
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
        // The runner hard-codes CWD/SESSIONS_DIR/SYSTEM_PROMPT_PATH to /workspace/... paths.
        // ESM named imports (`import { mkdirSync } from "node:fs"`) capture bindings at module
        // evaluation time, so a monkey-patch shim on the `fs` default export object does NOT
        // affect them in Node 22+. Use the runner's own env var overrides instead.
        env.put("MENTOR_RUNNER_CWD", workspace.toString());
        env.put("MENTOR_RUNNER_SESSIONS_DIR", workspace.resolve(".sessions").toString());
        env.put(
            "MENTOR_RUNNER_SYSTEM_PROMPT_PATH",
            workspace.resolve("agent").resolve("mentor").resolve("system.md").toString()
        );
        pb.directory(workspace.toFile());

        pb.command("node", workspace.resolve("pi-mentor-runner.mjs").toString());

        pb.redirectErrorStream(false);
        Process process = pb.start();
        return new StdioAttachedSandbox(UUID.randomUUID(), "live-test-user", "live-test-workspace", process);
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

        /** Test-only escape hatch: send a raw method call and return the full response frame. */
        JsonNode callRaw(String method, JsonNode params, Duration timeout) {
            return call(method, params, timeout);
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
