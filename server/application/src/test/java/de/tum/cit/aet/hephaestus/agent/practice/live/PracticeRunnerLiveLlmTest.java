package de.tum.cit.aet.hephaestus.agent.practice.live;

import static de.tum.cit.aet.hephaestus.agent.practice.live.PracticeCriteriaCaseFixtures.FIXTURE_DIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.tum.cit.aet.hephaestus.agent.practice.PracticeSandboxSpec;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmCredentials;
import de.tum.cit.aet.hephaestus.testconfig.LiveLlmTest;
import de.tum.cit.aet.hephaestus.testconfig.PiSdkInstallation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Live end-to-end test for the practice-review {@code pi-runner.ts} against a real LLM.
 *
 * <p>Exercises Pi SDK ↔ LLM, the runner's two-attempt loop, watchdog, custom
 * {@code submit_result} tool, the program host with the built-in review, and the admission body the runner emits —
 * all without Docker.
 * The {@code DockerSandboxLiveTest} covers the sandbox SPI separately.
 *
 * <p>Mirrors {@code MentorLiveLlmTest} for the Pi SDK install and the custom provider
 * extension that bends Pi's built-in {@code openai} provider toward the configured endpoint (Pi does not
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

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    /** The runner reads/writes here verbatim; we own the directory for the duration of one test. */
    // Somewhere writable, rather than a root-owned /workspace this harness may not be able to create.
    private static final Path WORKSPACE =
            Path.of(System.getenv().getOrDefault("PI_RUNNER_CWD", "/workspace")).toAbsolutePath();

    /** The one practice of the run without a criteria case. */
    private static final String PRACTICE = "avoids-insecure-defaults-and-over-broad-permissions";

    @BeforeAll
    static void installPiSdk() throws Exception {
        PiSdkInstallation.ensureInstalled();
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
        try (ProxyStandIn proxy = new ProxyStandIn(creds)) {
            runAndVerify(creds, proxy, null);
        }
    }

    @ParameterizedTest(name = CriteriaCaseSelection.NAME)
    @MethodSource(CriteriaCaseSelection.SOURCE)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void shouldApplyCatalogueBoundaryWhenEvidenceMatchesCase(JsonNode scenario) throws Exception {
        LiveLlmCredentials creds = LiveLlmCredentials.fromEnv();
        try (ProxyStandIn proxy = new ProxyStandIn(creds)) {
            runAndVerify(creds, proxy, scenario);
        }
    }

    private void runAndVerify(LiveLlmCredentials creds, ProxyStandIn proxy, @Nullable JsonNode scenario)
            throws Exception {
        Map<String, String> environment = stageWorkspace(creds, scenario);
        // This is a measurement harness, not a delivery test. Keeping the composition request absent
        // means the runner cannot even prepare feedback, and the runner starts with no ambient credential
        // before the agent process starts. In particular, a developer's GH_TOKEN/GITHUB_TOKEN is never
        // exposed to the model's bash tool.
        assertThat(Files.exists(WORKSPACE.resolve(SandboxLayout.FEEDBACK_COMPOSITION_PATH)))
                .as("live harness does not request feedback composition")
                .isFalse();
        Process runner =
                LiveRunnerWorkspace.spawn(WORKSPACE, environment, proxy.baseUrl(), "live-test-job-token", null);

        // Drain stdout/stderr into the JVM console with a tag so failures show what the agent said.
        Thread stdoutPump = pumpStream(runner.getInputStream(), "[practice-runner stdout]");
        Thread stderrPump = pumpStream(runner.getErrorStream(), "[practice-runner stderr]");

        boolean finished = runner.waitFor(240, TimeUnit.SECONDS);
        if (!finished) {
            runner.destroyForcibly();
            stdoutPump.join(5_000);
            stderrPump.join(5_000);
            fail("pi-runner did not exit within 240s — likely an LLM stall or watchdog miss. Check "
                    + "the [practice-runner *] output above for diagnostics.");
            return;
        }
        stdoutPump.join(5_000);
        stderrPump.join(5_000);

        int exitCode = runner.exitValue();

        // The runner returns 0 on success, 1 on retry-budget exhaustion. Anything else (2 fatal,
        // 3 watchdog kill, 42 envelope mismatch) is a real bug.
        if (exitCode != 0 && exitCode != 1) {
            fail("pi-runner exited with unexpected code " + exitCode
                    + " (0=success, 1=retry-exhausted, 2=fatal, 3=watchdog, 42=envelope mismatch). "
                    + "See [practice-runner *] output above.");
        }

        // Parse via the real production parser. The parser tolerates fallback through review-state.json
        // and swallows malformed JSON — assertions below are what catches a real regression.
        SandboxResult sandboxResult = buildSandboxResult(exitCode);
        AgentResult result = new PiResultParser(MAPPER, new SimpleMeterRegistry()).parse(sandboxResult);

        assertThat(exitCode).as("the review completed within its budget").isZero();
        assertThat(result.success()).isTrue();

        String rawOutput = (String) result.output().get("rawOutput");
        assertThat(rawOutput)
                .as("result.json (or review-state.json fallback) yielded a parsed payload")
                .isNotNull();

        JsonNode parsed = MAPPER.readTree(rawOutput);
        assertThat(parsed.path("schemaVersion").asInt())
                .as("result.json is the admission body")
                .isEqualTo(2);
        JsonNode practiceResults = parsed.path("results");
        assertThat(practiceResults.isArray()).as("results must be a JSON array").isTrue();
        assertThat(practiceResults.size()).as("at least one result emitted").isGreaterThanOrEqualTo(1);

        Map<String, String> expected = new LinkedHashMap<>();
        if (scenario == null) {
            expected.put(PRACTICE, "NOT_MET");
        } else {
            scenario.path("expected")
                    .properties()
                    .forEach(entry ->
                            expected.put(entry.getKey(), entry.getValue().asString()));
        }
        Map<String, String> expectedSeverity = new LinkedHashMap<>();
        if (scenario != null) {
            scenario.path("expectedSeverity")
                    .properties()
                    .forEach(entry -> expectedSeverity.put(
                            entry.getKey(), entry.getValue().asString()));
        }
        Map<String, String> actual = new LinkedHashMap<>();
        Map<String, String> actualSeverity = new LinkedHashMap<>();
        for (JsonNode entry : practiceResults) {
            String slug = entry.path("practiceSlug").asString();
            JsonNode candidate = entry.path("candidate");
            assertThat(actual.put(slug, candidate.path("outcome").asString()))
                    .as("one result per practice")
                    .isNull();
            String severity = ProxyStandIn.storedSeverity(candidate);
            if (expectedSeverity.containsKey(slug)) {
                actualSeverity.put(slug, String.valueOf(severity));
            }
            if ("NOT_MET".equals(candidate.path("outcome").asString())) {
                assertThat(severity).isIn("CRITICAL", "MAJOR", "MINOR", "INFO");
                assertThat(candidate.path("problems"))
                        .as("a negative locates its problems")
                        .isNotEmpty();
            } else {
                assertThat(candidate.has("severity")).isFalse();
                assertThat(candidate.has("problems")).isFalse();
            }
            assertThat(candidate.has("confidence")).isFalse();
            assertThat(candidate.path("citations").isArray()).isTrue();
        }
        assertThat(actual)
                .as(
                        "%s: %s",
                        scenario == null
                                ? "hardcoded secret"
                                : scenario.path("id").asString(),
                        rawOutput)
                .isEqualTo(expected);
        assertThat(actualSeverity)
                .as(
                        "severity in %s: %s",
                        scenario == null
                                ? "hardcoded secret"
                                : scenario.path("id").asString(),
                        rawOutput)
                .isEqualTo(expectedSeverity);

        // Usage diagnostics — surfaces token totals to the console so flakes show whether the call
        // even reached the LLM. Not asserted as the watchdog branch may leave usage=0.
        AgentResult.LlmUsage usage = result.usage();
        if (usage != null) {
            System.out.printf(
                    "[practice-live] usage: model=%s, input=%s, output=%s, totalCalls=%d, cost=$%s%n",
                    usage.model(), usage.inputTokens(), usage.outputTokens(), usage.totalCalls(), usage.costUsd());
        }
        System.out.printf("[practice-live] %d practiceResult(s)%n", practiceResults.size());
    }

    /**
     * The workspace of the worker's spec for this model. Without a case, one practice reviews the fixture's login
     * change; a case stages its own change and catalog.
     */
    private Map<String, String> stageWorkspace(LiveLlmCredentials creds, @Nullable JsonNode scenario) throws Exception {
        PracticeSandboxSpec spec = LiveRunnerWorkspace.adapter()
                .buildSandboxSpec(LiveRunnerWorkspace.request(creds.model(), "live-test-job-token", Map.of()));
        LiveRunnerWorkspace.stageSpec(WORKSPACE, spec.inputFiles());
        String kind = "scm.pull_request";
        if (scenario == null) {
            LiveRunnerWorkspace.stageLoginChange(WORKSPACE);
            LiveRunnerWorkspace.stagePractice(
                    WORKSPACE,
                    PRACTICE,
                    "Hardcoded Secrets",
                    Files.readString(FIXTURE_DIR.resolve("practices/" + PRACTICE + ".md")),
                    1L);
            LiveRunnerWorkspace.stageTask(
                    WORKSPACE,
                    "Review merge request #1 in test/fixture against " + PRACTICE + ". Follow "
                            + SandboxLayout.ORCHESTRATOR_PATH + ".");
        } else {
            PracticeCriteriaCaseFixtures.stage(WORKSPACE, scenario);
            if (scenario.path("workType").asString().equals("issue")) kind = "scm.issue";
        }
        LiveRunnerWorkspace.stagePracticeInputs(WORKSPACE, kind, null);
        return spec.environment();
    }

    private static Thread pumpStream(InputStream stream, String tag) {
        Thread t = new Thread(
                () -> {
                    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(tag + " " + line);
                        }
                    } catch (IOException ignored) {
                        // Stream closed when the child exits — nothing to do.
                    }
                },
                "practice-runner-log-" + tag.hashCode());
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

    /**
     * Stands in for the server's LLM proxy on one base URL, as production does: model calls are forwarded to
     * the live endpoint with its key, and the result admission the runner posts after measuring admits
     * every result it carries.
     */
    static final class ProxyStandIn implements AutoCloseable {
        /**
         * The whole wait for forwarding work and the upstream client. Their own close() waits for every
         * unfinished response stream, so a stalled upstream would hold a runner assertion in cleanup until
         * the JUnit timeout replaces it.
         */
        private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

        private final HttpServer server;
        private final ExecutorService executor;
        private final HttpClient client = HttpClient.newHttpClient();
        private final Object lifecycle = new Object();
        // Guarded by lifecycle, so close() cannot miss a forwarding that registers concurrently.
        private final Set<Forwarding> active = new HashSet<>();
        private boolean closed;

        ProxyStandIn(LiveLlmCredentials creds) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/admit-results", exchange -> {
                byte[] body = MAPPER.writeValueAsBytes(admit(MAPPER.readTree(exchange.getRequestBody())));
                exchange.getResponseHeaders().set("content-type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.createContext("/", exchange -> forward(exchange, creds));
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        /**
         * Every candidate admitted as sent, projected as the server answers it: the result and each of its problems
         * under the ids the server assigns, every citation indexed, nothing withheld and no line anchorable.
         */
        static ObjectNode admit(JsonNode request) {
            ObjectNode answer =
                    MAPPER.createObjectNode().put("schemaVersion", 2).put("admissionDigest", "live-test");
            ArrayNode admitted = answer.putArray("results");
            for (JsonNode entry : request.path("results")) {
                JsonNode candidate = entry.path("candidate");
                ObjectNode row = admitted.addObject();
                row.put("practiceResultId", UUID.randomUUID().toString());
                row.set("practiceSlug", entry.path("practiceSlug"));
                row.put("outcome", outcomeOf(candidate));
                row.put("severity", storedSeverity(candidate));
                row.set("summary", candidate.path("summary"));
                row.set("explanation", candidate.path("explanation"));
                row.put("summaryOnly", false);
                row.put("publicEligible", true);
                row.set("citations", indexed(candidate.path("citations")));
                ObjectNode evidence = row.putObject("evidence");
                candidate
                        .path("evidence")
                        .properties()
                        .forEach(warrant -> evidence.set(warrant.getKey(), warrant.getValue()));
                row.set("example", candidate.has("example") ? candidate.get("example") : NullNode.getInstance());
                row.set("coverage", candidate.path("coverage"));
                ArrayNode observations = row.putArray("observations");
                int position = 0;
                for (JsonNode problem : candidate.path("problems")) {
                    ObjectNode child = observations.addObject();
                    child.put("observationId", UUID.randomUUID().toString());
                    child.put("position", position++);
                    child.set("severity", problem.path("severity"));
                    child.set("summary", problem.path("summary"));
                    child.set("explanation", problem.path("explanation"));
                    child.set("nextStep", problem.has("nextStep") ? problem.get("nextStep") : NullNode.getInstance());
                    child.set("basis", problem.path("basis"));
                    child.set("citations", indexed(problem.path("citations")));
                    child.put("anchorable", false);
                }
            }
            answer.putArray("withheld");
            return answer;
        }

        /** As admission records it: the outcome as sent, which must name an {@link Outcome}. */
        private static String outcomeOf(JsonNode candidate) {
            JsonNode outcome = candidate.path("outcome");
            if (!outcome.isString()) {
                throw new IllegalArgumentException("An admitted result needs a string outcome");
            }
            return Outcome.valueOf(outcome.asString()).name();
        }

        /** The severity admission stores: the one the candidate states, else its worst problem's; none for others. */
        static @Nullable String storedSeverity(JsonNode candidate) {
            if (candidate.path("severity").isString())
                return candidate.path("severity").asString();
            Severity worst = null;
            for (JsonNode problem : candidate.path("problems")) {
                Severity severity = Severity.valueOf(problem.path("severity").asString());
                if (worst == null || severity.rank() < worst.rank()) worst = severity; // rank 0 is the most severe
            }
            return worst == null ? null : worst.name();
        }

        private static ArrayNode indexed(JsonNode citations) {
            ArrayNode indexed = MAPPER.createArrayNode();
            int index = 0;
            for (JsonNode citation : citations) {
                indexed.add(
                        ((ObjectNode) citation.deepCopy()).put("index", index++).put("anchorable", false));
            }
            return indexed;
        }

        private void forward(HttpExchange exchange, LiveLlmCredentials creds) throws IOException {
            Forwarding forwarding = new Forwarding();
            if (!register(forwarding)) {
                exchange.close();
                return;
            }
            try (exchange) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                creds.baseUrl() + exchange.getRequestURI().getPath()))
                        .header("authorization", "Bearer " + creds.apiKey())
                        .header("content-type", "application/json")
                        .method(
                                exchange.getRequestMethod(),
                                HttpRequest.BodyPublishers.ofByteArray(
                                        exchange.getRequestBody().readAllBytes()))
                        .build();
                try {
                    HttpResponse<InputStream> response =
                            client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream upstream = response.body()) {
                        if (!attach(forwarding, upstream)) {
                            // close() took its snapshot before this stream arrived.
                            return;
                        }
                        response.headers()
                                .firstValue("content-type")
                                .ifPresent(type -> exchange.getResponseHeaders().set("content-type", type));
                        // Forward the response with chunked transfer encoding.
                        exchange.sendResponseHeaders(response.statusCode(), 0);
                        try (var out = exchange.getResponseBody()) {
                            upstream.transferTo(out);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exchange.sendResponseHeaders(502, -1);
                }
            } finally {
                synchronized (lifecycle) {
                    active.remove(forwarding);
                }
            }
        }

        private boolean register(Forwarding forwarding) {
            synchronized (lifecycle) {
                return !closed && active.add(forwarding);
            }
        }

        private boolean attach(Forwarding forwarding, InputStream upstream) {
            synchronized (lifecycle) {
                if (closed) {
                    return false;
                }
                forwarding.upstream = upstream;
                return true;
            }
        }

        /**
         * Stops forwarding within {@link #CLOSE_TIMEOUT}. Every cancellation stage runs even when an earlier one
         * fails, and blocking work stays outside the lifecycle lock. A resource that has not terminated by the
         * deadline fails the close; under try-with-resources an earlier assertion stays primary.
         */
        @Override
        public void close() {
            List<InputStream> streams = new ArrayList<>();
            synchronized (lifecycle) {
                if (closed) {
                    return;
                }
                closed = true;
                for (Forwarding forwarding : active) {
                    if (forwarding.upstream != null) {
                        streams.add(forwarding.upstream);
                    }
                }
            }
            long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
            List<Exception> failures = new ArrayList<>();
            try {
                // Stop closes connections; the handler alone closes each response body and exchange.
                server.stop(0);
            } catch (RuntimeException e) {
                failures.add(e);
            }
            try {
                executor.shutdownNow();
            } catch (RuntimeException e) {
                failures.add(e);
            }
            for (InputStream stream : streams) {
                try {
                    stream.close();
                } catch (IOException | RuntimeException e) {
                    failures.add(e);
                }
            }
            try {
                client.shutdownNow();
            } catch (RuntimeException e) {
                failures.add(e);
            }
            try {
                if (!executor.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS)) {
                    failures.add(new IllegalStateException("forwarding work did not stop within " + CLOSE_TIMEOUT));
                }
                if (!client.awaitTermination(remaining(deadline))) {
                    failures.add(new IllegalStateException("the upstream client did not stop within " + CLOSE_TIMEOUT));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(e);
            }
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException("The proxy stand-in did not shut down");
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        }

        private static Duration remaining(long deadline) {
            return Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
        }

        /** Once the upstream answers, the response stream transferred by one forwarding handler. */
        private static final class Forwarding {
            // Guarded by the stand-in's lifecycle lock.
            private @Nullable InputStream upstream;
        }
    }
}
