package de.tum.cit.aet.hephaestus.agent.practice.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Live end-to-end test for the practice-review {@code pi-runner.mjs} against a real LLM.
 *
 * <p>Exercises Pi SDK ↔ LLM, the runner's two-attempt loop, watchdog, custom
 * {@code report_observation} tool, and the result schema the runner emits — all without Docker.
 * The {@code DockerSandboxLiveTest} covers the sandbox SPI separately.
 *
 * <p>Mirrors {@code MentorLiveLlmTest} for the Pi SDK install and the {@code tum-openai}
 * extension that bends Pi's built-in {@code openai} provider toward the TUM gateway (Pi does not
 * read {@code OPENAI_BASE_URL} natively).
 *
 * <p>Workspace staging differs from the mentor test: the practice runner hardcodes
 * {@code /workspace} as CWD and reads/writes via async fs (read tool) and {@code spawn} (bash
 * tool). Patching every fs entry point in a Node shim is fragile, so we stage at {@code
 * /workspace} directly. The harness runs as root and the directory is cleaned between tests.
 */
@LiveLlmTest
@Tag("live")
class PracticeRunnerLiveLlmTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Pinned to {@code pi-mentor-runner.mjs}'s version so a single npm install covers both tests. */
    private static final String PI_SDK_VERSION = "0.74.0";

    private static final Path SDK_DIR = Path.of("target", "pi-sdk").toAbsolutePath();
    private static final Path RUNNER = Path.of("src", "main", "resources", "agent", "pi-runner.mjs").toAbsolutePath();
    private static final Path FIXTURE_DIR = Path.of(
        "src",
        "test",
        "resources",
        "agent",
        "live-practice"
    ).toAbsolutePath();

    /** The runner reads/writes here verbatim; we own the directory for the duration of one test. */
    private static final Path WORKSPACE = Path.of("/workspace").toAbsolutePath();

    /** Wall-clock cap for the whole runner — initial + retry budgets are derived from this. */
    private static final long AGENT_BUDGET_MS = 240_000L;

    @BeforeAll
    static void installPiSdk() throws Exception {
        // Same marker + lock dance the mentor test uses so two JVMs (or repeated `mvn test` runs)
        // share one install. We deliberately reuse the mentor test's directory and marker file.
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
            if (Files.exists(marker)) {
                return;
            }
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
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("npm install for Pi SDK timed out after 180s");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("npm install for Pi SDK failed; see stderr above");
            }
            Files.writeString(marker, "ok\n");
            //noinspection ResultOfMethodCallIgnored
            lock.isValid();
        }
        // Honest failure mode: if a parallel test or earlier mentor run left a half-installed SDK,
        // surface that clearly instead of letting Pi blow up with a cryptic ESM error.
        if (!Files.isDirectory(SDK_DIR.resolve("node_modules/@earendil-works/pi-coding-agent"))) {
            throw new IllegalStateException(
                "Pi SDK install marker present but @earendil-works/pi-coding-agent is missing under " +
                    SDK_DIR +
                    " — delete target/pi-sdk and re-run."
            );
        }
    }

    @BeforeEach
    void cleanWorkspace() throws IOException {
        // The runner hard-codes /workspace; we own it exclusively (@LiveLlmTest pins to SAME_THREAD).
        // Per-test isolation: clear everything before each run so leftovers from a failed previous
        // attempt cannot satisfy result.json checks.
        if (Files.exists(WORKSPACE)) {
            deleteTree(WORKSPACE);
        }
        Files.createDirectories(WORKSPACE);
    }

    @AfterEach
    void leaveWorkspaceArtifacts() {
        // Intentionally do NOT delete /workspace here. If the test fails, the next run's @BeforeEach
        // will clean — but a developer hand-running locally keeps the .output files for diagnostics.
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void flagsHardcodedSecret_inOneFileDiff() throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();

        stageWorkspace(creds);
        Process runner = spawnRunner(creds);

        // Drain stdout/stderr into the JVM console with a tag so failures show what the agent said.
        Thread stdoutPump = pumpStream(runner.getInputStream(), "[practice-runner stdout]");
        Thread stderrPump = pumpStream(runner.getErrorStream(), "[practice-runner stderr]");

        boolean finished = runner.waitFor(240, TimeUnit.SECONDS);
        if (!finished) {
            runner.destroyForcibly();
            stdoutPump.join(5_000);
            stderrPump.join(5_000);
            fail(
                "pi-runner did not exit within 240s — likely an LLM stall or watchdog miss. Check " +
                    "the [practice-runner *] output above for diagnostics."
            );
            return;
        }
        stdoutPump.join(5_000);
        stderrPump.join(5_000);

        int exitCode = runner.exitValue();

        // The runner returns 0 on success, 1 on retry-budget exhaustion. Anything else (2 fatal,
        // 3 watchdog kill, 42 envelope mismatch) is a real bug.
        if (exitCode != 0 && exitCode != 1) {
            fail(
                "pi-runner exited with unexpected code " +
                    exitCode +
                    " (0=success, 1=retry-exhausted, 2=fatal, 3=watchdog, 42=envelope mismatch). " +
                    "See [practice-runner *] output above."
            );
        }

        // Parse via the real production parser. The parser tolerates fallback through review-state.json
        // and swallows malformed JSON — assertions below are what catches a real regression.
        SandboxResult sandboxResult = buildSandboxResult(exitCode);
        AgentResult result = new PiResultParser(MAPPER, new SimpleMeterRegistry()).parse(sandboxResult);

        // Strict assertion 1: the parser must classify the run as successful when exit code is 0.
        // If exit code is 1 (retry-exhausted) we don't enforce success() — but we still demand
        // findings below, which is the real product invariant.
        if (exitCode == 0) {
            assertThat(result.success()).as("PiResultParser.success() reflects exit code 0").isTrue();
        }

        String rawOutput = (String) result.output().get("rawOutput");
        assertThat(rawOutput).as("result.json (or review-state.json fallback) yielded a parsed payload").isNotNull();

        JsonNode parsed = MAPPER.readTree(rawOutput);
        JsonNode findings = parsed.path("observations");
        assertThat(findings.isArray()).as("findings must be a JSON array").isTrue();
        assertThat(findings.size()).as("at least one finding emitted").isGreaterThanOrEqualTo(1);

        // Strict shape: every finding must match the wire schema PiResultParser exposes to downstream
        // delivery code. Catches drift between runner output and parser tolerance.
        for (int i = 0; i < findings.size(); i++) {
            JsonNode finding = findings.get(i);
            String tag = "finding[" + i + "]";
            assertThat(finding.path("practiceSlug").asString())
                .as(tag + ".practiceSlug")
                .isEqualTo("hardcoded-secrets");
            assertThat(finding.path("presence").asString())
                .as(tag + ".presence")
                .isIn("PRESENT", "ABSENT", "NOT_APPLICABLE");
            // assessment is null/absent only when presence is NOT_APPLICABLE
            if (!"NOT_APPLICABLE".equals(finding.path("presence").asString())) {
                assertThat(finding.path("assessment").asString()).as(tag + ".assessment").isIn("GOOD", "BAD");
            }
            assertThat(finding.path("severity").asString())
                .as(tag + ".severity")
                .isIn("CRITICAL", "MAJOR", "MINOR", "INFO");
            double confidence = finding.path("confidence").asDouble(-1.0);
            assertThat(confidence).as(tag + ".confidence in [0,1]").isBetween(0.0, 1.0);
            assertThat(finding.path("evidence").path("locations").isArray())
                .as(tag + ".evidence.locations is an array")
                .isTrue();
        }

        // Planted-violation detection: this practice must be detected when the violation is present.
        // if it misses, the prompt or fixture is broken — not the LLM. A planted secret is a (PRESENT, BAD)
        // observation: the bad signal IS present and that is a violation.
        boolean foundViolation = false;
        for (JsonNode finding : findings) {
            if (
                "hardcoded-secrets".equals(finding.path("practiceSlug").asString()) &&
                "PRESENT".equals(finding.path("presence").asString()) &&
                "BAD".equals(finding.path("assessment").asString())
            ) {
                foundViolation = true;
                break;
            }
        }
        assertThat(foundViolation)
            .as(
                "at least one (PRESENT, BAD) hardcoded-secrets finding for the planted apiKey/dbPassword. " +
                    "Findings payload: " +
                    rawOutput
            )
            .isTrue();

        // Usage diagnostics — surfaces token totals to the console so flakes show whether the call
        // even reached the LLM. Not asserted as the watchdog branch may leave usage=0.
        AgentResult.LlmUsage usage = result.usage();
        if (usage != null) {
            System.out.printf(
                "[practice-live] usage: model=%s, input=%s, output=%s, totalCalls=%d, cost=$%s%n",
                usage.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalCalls(),
                usage.costUsd()
            );
        }
        System.out.printf("[practice-live] %d finding(s); violation=%s%n", findings.size(), foundViolation);
    }

    // Workspace staging

    private void stageWorkspace(LiveLlmCredentials creds) throws IOException {
        // ESM resolution walks node_modules upward from the importing file. Production binds the
        // global node_modules into /workspace/node_modules; the test does the same with a symlink.
        Path nodeModulesLink = WORKSPACE.resolve("node_modules");
        if (Files.exists(nodeModulesLink) || Files.isSymbolicLink(nodeModulesLink)) {
            Files.delete(nodeModulesLink);
        }
        Files.createSymbolicLink(nodeModulesLink, SDK_DIR.resolve("node_modules"));

        // Copy the production runner verbatim — same bytes that ship to the agent container.
        Files.copy(RUNNER, WORKSPACE.resolve("pi-runner.mjs"), StandardCopyOption.REPLACE_EXISTING);

        // Orchestrator instructions live at WORKSPACE/.pi/AGENTS.md — same layout production uses,
        // with PI_CODING_AGENT_DIR pointed inside the workspace.
        Path piDir = WORKSPACE.resolve(SandboxLayout.PI_AGENT_PREFIX);
        Files.createDirectories(piDir);
        Files.copy(
            Path.of("src", "main", "resources", "agent", "pi-orchestrator.md").toAbsolutePath(),
            piDir.resolve(SandboxLayout.ORCHESTRATOR_FILENAME),
            StandardCopyOption.REPLACE_EXISTING
        );

        // Pi-side settings + custom provider via models.json. The mentor test registers a provider
        // through an extension because its createAgentSession path drains pendingProviderRegistrations
        // via createAgentSessionServices. The practice runner uses createAgentSession() directly —
        // that path resolves findInitialModel() BEFORE extensions ever run, so an extension here
        // would be ignored. models.json is loaded inside ModelRegistry.create(), so the provider is
        // visible at model-resolution time without modifying the production runner.
        Path piHome = WORKSPACE.resolve(".pi-home");
        Files.createDirectories(piHome);
        Files.write(piHome.resolve("settings.json"), buildSettingsJson(creds.model()));
        Files.write(piHome.resolve("models.json"), buildModelsJson(creds));

        // Practice catalog under /workspace/inputs/practices/ — the agent reads index.json (slug list)
        // and all-criteria.md (per-practice rules) per the orchestrator instructions.
        Path practicesDir = WORKSPACE.resolve(SandboxLayout.PRACTICES_PREFIX);
        Files.createDirectories(practicesDir);
        copyFixture("practices/index.json", practicesDir.resolve("index.json"));
        copyFixture("practices/all-criteria.md", practicesDir.resolve("all-criteria.md"));
        copyFixture("practices/hardcoded-secrets.md", practicesDir.resolve("hardcoded-secrets.md"));

        // Context fixture — diff, metadata, comments, diff_summary. Mirrors what
        // PullRequestContentSource materialises in production.
        Path contextDir = WORKSPACE.resolve(SandboxLayout.CONTEXT_PREFIX);
        Files.createDirectories(contextDir);
        copyFixture("diff.patch", contextDir.resolve("diff.patch"));
        copyFixture("metadata.json", contextDir.resolve("metadata.json"));
        copyFixture("comments.json", contextDir.resolve("comments.json"));
        copyFixture("diff_summary.md", contextDir.resolve("diff_summary.md"));

        // Real Swift source so the agent's read tool can pull the actual bytes when grepping the
        // repo mount (the orchestrator hints at /workspace/repo/ — we shim it as a symlink to the
        // fixture directory so grep+read work without an actual git clone).
        Path repoLink = WORKSPACE.resolve("repo");
        if (Files.exists(repoLink) || Files.isSymbolicLink(repoLink)) {
            Files.delete(repoLink);
        }
        Files.createSymbolicLink(repoLink, FIXTURE_DIR);

        // Task envelope at /workspace/task.json — built via the production TaskEnvelope record so
        // any future field addition fails this test at compile time, not at runtime.
        TaskEnvelope envelope = TaskEnvelope.of(
            UUID.randomUUID(),
            1L,
            new Task.PracticeReview(
                "Review merge request #1 in test/fixture. Read inputs/context/diff_summary.md, " +
                    "inputs/practices/all-criteria.md, inputs/practices/index.json, and inputs/context/metadata.json. " +
                    "Apply the hardcoded-secrets practice to inputs/context/diff.patch. Persist each " +
                    "justified observation via report_observation (one tool call per observation). Follow " +
                    SandboxLayout.ORCHESTRATOR_PATH +
                    " for the schema and review rules.",
                1,
                "test/fixture"
            )
        );
        Files.write(
            WORKSPACE.resolve(SandboxLayout.TASK_ENVELOPE_FILENAME),
            MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope)
        );
    }

    private static byte[] buildSettingsJson(String modelId) throws IOException {
        // Same shape as PiRuntimeFactory.buildPiSettingsJson but with the custom provider name. We
        // intentionally do NOT call PiRuntimeFactory.buildPiSettingsJson directly because the
        // production helper emits {defaultProvider: "openai"} which would hit api.openai.com — the
        // TUM gateway is reached via the tum-openai extension registered below.
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("defaultProvider", "tum-openai");
        settings.put("defaultModel", modelId);
        settings.put("transport", "sse");
        Map<String, Object> compaction = new HashMap<>();
        compaction.put("enabled", true);
        compaction.put("reserveTokens", 16384);
        settings.put("compaction", compaction);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(settings);
    }

    private static byte[] buildModelsJson(LiveLlmCredentials creds) throws IOException {
        // Pi loads providers from models.json during ModelRegistry.create() — before any session is
        // built. We register tum-openai as a custom provider with api: "openai-completions" (the
        // chat-completions impl Pi ships, matching Cerebras / Cloudflare AI Gateway) so the request
        // routes to the TUM gateway with Authorization: Bearer $OPENAI_API_KEY.
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", creds.model());
        model.put("name", "GPT OSS 120B (TUM)");
        model.put("reasoning", false);
        model.put("input", List.of("text"));
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("input", 0);
        cost.put("output", 0);
        cost.put("cacheRead", 0);
        cost.put("cacheWrite", 0);
        model.put("cost", cost);
        model.put("contextWindow", 131072);
        model.put("maxTokens", 4096);

        Map<String, Object> tumProvider = new LinkedHashMap<>();
        tumProvider.put("name", "TUM AET ASE Gateway");
        tumProvider.put("baseUrl", creds.baseUrl());
        tumProvider.put("apiKey", "OPENAI_API_KEY");
        tumProvider.put("authHeader", true);
        tumProvider.put("api", "openai-completions");
        tumProvider.put("models", List.of(model));

        Map<String, Object> providers = new LinkedHashMap<>();
        providers.put("tum-openai", tumProvider);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("providers", providers);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private static void copyFixture(String relativePath, Path dest) throws IOException {
        Files.copy(FIXTURE_DIR.resolve(relativePath), dest, StandardCopyOption.REPLACE_EXISTING);
    }

    // Process plumbing

    private static Process spawnRunner(LiveLlmCredentials creds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("node", "pi-runner.mjs");
        pb.directory(WORKSPACE.toFile());
        Map<String, String> env = pb.environment();
        env.putAll(creds.asProcessEnv()); // OPENAI_API_KEY + OPENAI_BASE_URL
        // PI_CODING_AGENT_DIR points Pi at our staged extension + settings, away from ~/.pi.
        env.put("PI_CODING_AGENT_DIR", WORKSPACE.resolve(".pi-home").toString());
        env.put("AGENT_BUDGET_MS", Long.toString(AGENT_BUDGET_MS));
        // The runner imports the SDK via bare ESM specifiers; NODE_PATH lets Node resolve them when
        // /workspace/node_modules is a symlink (some Node versions skip symlinked node_modules).
        env.put("NODE_PATH", SDK_DIR.resolve("node_modules").toString());
        return pb.start();
    }

    private static Thread pumpStream(java.io.InputStream stream, String tag) {
        Thread t = new Thread(
            () -> {
                try (
                    var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)
                    )
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(tag + " " + line);
                    }
                } catch (IOException ignored) {
                    // Stream closed when the child exits — nothing to do.
                }
            },
            "practice-runner-log-" + tag.hashCode()
        );
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Build a {@link SandboxResult} from the on-disk {@code out/} directory. Mirrors what
     * {@code DockerSandboxAdapter} does after a container exit so we feed the production parser
     * the exact map shape it expects.
     */
    private static SandboxResult buildSandboxResult(int exitCode) throws IOException {
        Map<String, byte[]> outputFiles = new LinkedHashMap<>();
        Path outputDir = WORKSPACE.resolve("out");
        if (Files.isDirectory(outputDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        outputFiles.put(entry.getFileName().toString(), Files.readAllBytes(entry));
                    }
                }
            }
        }
        return new SandboxResult(exitCode, outputFiles, "", false, Duration.ZERO);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        // Manual walk to avoid Files.walk + lambda surprises with symlinks (we have node_modules + repo).
        List<Path> entries = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.forEach(entries::add);
        }
        // Reverse: children before parents.
        for (int i = entries.size() - 1; i >= 0; i--) {
            Path p = entries.get(i);
            if (p.equals(root)) {
                continue;
            }
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // Symlink targets may be unreadable; remove the link itself.
                if (Files.isSymbolicLink(p)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
