package de.tum.cit.aet.hephaestus.agent.practice;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.proxy.JobTokenAuthenticationFilter;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentImageProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNotRatedReason;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Cross-language sync test. The practice script runner refuses the whole models file for one slot or
 * protocol it does not know, so the names that {@link PracticePiAdapter} writes must be the ones that
 * {@code docker/agents/practice-scripts/lib/contract.ts} accepts.
 */
class PracticeScriptContractSyncTest extends BaseUnitTest {

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    @Test
    void shouldWriteOnlySlotsAndProtocolsThePracticeScriptRunnerAccepts() throws IOException {
        String contract = Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/contract.ts"));

        assertThat(quotedIn(contract, "MODEL_SLOTS = ["))
                .containsExactlyElementsOf(
                        Arrays.stream(ModelKind.values()).map(ModelKind::slot).toList());
        assertThat(quotedIn(contract, "PROTOCOLS = ["))
                .containsExactlyElementsOf(Arrays.stream(LlmApiProtocol.values())
                        .map(LlmApiProtocol::wire)
                        .toList());
    }

    /** The proxy refuses a practice script call without the practice header, or with a slug it does not accept. */
    @Test
    void shouldAttributeEveryPracticeScriptCallTheWayTheProxyReadsIt() throws IOException {
        String contract = Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/contract.ts"));

        assertThat(contract)
                .contains("PRACTICE_SCRIPT_PRACTICE_HEADER = \""
                        + JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PRACTICE_HEADER + "\"")
                .contains("PRACTICE_SLUG = /^" + SandboxLayout.PRACTICE_SLUG.pattern() + "$/u");
    }

    /** The server drops a practice script report entry with a reason it does not know, so the lists must match. */
    @Test
    void shouldKnowEveryReasonThePracticeScriptRunnerReportsAsNotRated() throws IOException {
        String contract = Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/contract.ts"));

        assertThat(quotedIn(contract, "REASONS = ["))
                .containsExactlyElementsOf(Arrays.stream(PracticeScriptNotRatedReason.values())
                        .map(PracticeScriptNotRatedReason::wire)
                        .toList());
    }

    /**
     * The server drops a report entry with a status it does not know. Every status that the runner records for a
     * script that ended must reach its own {@link PracticeScriptRunStatus}; only "did not finish" has no runner status.
     */
    @Test
    void shouldReadEveryStatusThePracticeScriptRunnerRecords() throws IOException {
        List<String> statuses = quotedIn(
                Files.readString(resolveRepoFile("docker/agents/practice-scripts/lib/contract.ts")),
                "RUN_STATUSES = [");
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        List<String> entries = statuses.stream()
                .map(status -> "{\"slug\":\"script-" + status + "\",\"status\":\"" + status + "\",\"leads\":0}")
                .toList();
        Set<String> staged = statuses.stream().map(status -> "script-" + status).collect(Collectors.toSet());

        List<PracticeScriptRunStatus> read = new PiResultParser(new ObjectMapper(), meters)
                        .parsePracticeScriptReport(
                                ("{\"practices\":[" + String.join(",", entries) + "],\"truncated\":false}")
                                        .getBytes(StandardCharsets.UTF_8),
                                staged)
                        .stream()
                        .map(PiResultParser.PracticeScriptRunReport::status)
                        .toList();

        assertThat(meters.find(AgentMetrics.AGENT_PI_RESULT_PARSE_FAILURE).counter())
                .as("no runner status is dropped")
                .isNull();
        assertThat(read)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(PracticeScriptRunStatus.values())
                        .filter(status -> status != PracticeScriptRunStatus.NOT_FINISHED)
                        .toList());
    }

    /** {@code lib/models-host.test.ts} gives every slot of the same file a model. */
    @Test
    void shouldWriteTheModelsFileInTheShapeThePracticeScriptRunnerTestsRead() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PracticePiAdapter adapter = new PracticePiAdapter(
                new PiRuntimeFactory(mapper),
                new PiResultParser(mapper, new SimpleMeterRegistry()),
                new AgentImageProperties("agent-pi:test", ImagePullPolicy.IF_NOT_PRESENT),
                new PracticeReviewProperties(false, 15, 5, null, 12, 16000, 200_000),
                mapper);
        PracticeAgentRequest request = new PracticeAgentRequest(
                "openai-responses",
                "gpt-chat",
                null,
                null,
                null,
                "job-token",
                600,
                "practice-script-token",
                Map.of(
                        ModelKind.CHAT, model("openai-responses", "gpt-chat", null),
                        ModelKind.DECISION, model("openai-completions", "qwen-decider", ReasoningEffort.HIGH),
                        ModelKind.EMBEDDING, model("openai-embeddings", "embedder", null),
                        ModelKind.RERANKING, model("cohere-rerank", "reranker", null)));

        byte[] written = adapter.buildSandboxSpec(request).inputFiles().get(SandboxLayout.PRACTICE_SCRIPT_MODELS_FILE);

        assertThat(mapper.readTree(written))
                .isEqualTo(mapper.readTree(Files.readString(
                        resolveRepoFile("docker/agents/practice-scripts/test/practice-script-models.json"))));
    }

    @Test
    void shouldPassThePracticeScriptTokenToThePracticeScriptRunner() throws IOException {
        String token = PracticePiAdapter.PRACTICE_SCRIPT_TOKEN_ENV;

        assertThat(Files.readString(
                        resolveRepoFile("server/application/src/main/resources/agent/pi-practice-scripts.sh")))
                .contains(token + "=\"${" + token);
        assertThat(Files.readString(resolveRepoFile("docker/agents/practice-scripts/runner.ts")))
                .contains("process.env." + token);
    }

    private static FrozenModel model(String protocol, String modelId, @Nullable ReasoningEffort reasoningEffort) {
        return new FrozenModel(
                protocol, "https://models.example", modelId, null, null, null, null, null, reasoningEffort, null);
    }

    /** The quoted strings of the array literal that starts at {@code start}. */
    private static List<String> quotedIn(String source, String start) {
        int from = source.indexOf(start);
        assertThat(from).as("contract.ts declares %s", start).isNotNegative();
        String array = source.substring(from + start.length(), source.indexOf(']', from));
        return QUOTED.matcher(array).results().map(result -> result.group(1)).toList();
    }

    private static Path resolveRepoFile(String relativePath) {
        Path candidate = Path.of("..", "..").resolve(relativePath);
        return Files.exists(candidate) ? candidate : Path.of(relativePath);
    }
}
