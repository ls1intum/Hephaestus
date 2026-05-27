package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.SandboxResult;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PiResultParserTest extends BaseUnitTest {

    private PiResultParser parser;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        parser = new PiResultParser(new ObjectMapper(), meterRegistry);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("emits agent.pi.result.parse.failure{stage=usage} when usage.json is invalid")
    void emitsParseFailureMetric() {
        var bad = "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Trigger via usage parse path.
        parser.parseUsage(bad);
        assertThat(meterRegistry.counter("agent.pi.result.parse.failure", "stage", "usage").count()).isEqualTo(1d);
    }

    @Test
    void missingResultFile() {
        var result = parser.parse(new SandboxResult(0, Map.of(), "done", false, Duration.ofSeconds(10)));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).doesNotContainKey("rawOutput");
    }

    @Test
    void failureBecomesFailure() {
        var result = parser.parse(new SandboxResult(1, Map.of(), "x", false, Duration.ofSeconds(5)));
        assertThat(result.success()).isFalse();
    }

    @Test
    void rebuildsFromReviewState() {
        String reviewState = """
            {"findings":[{"practiceSlug":"x","title":"t","verdict":"NEGATIVE","severity":"MAJOR",
            "confidence":0.9,"evidence":{"locations":[],"snippets":[]},"reasoning":"r","guidance":"g",
            "suggestedDiffNotes":[]}]}""";
        var result = parser.parse(
            new SandboxResult(
                1,
                Map.of("review-state.json", reviewState.getBytes(StandardCharsets.UTF_8)),
                "runner failed",
                false,
                Duration.ofSeconds(10)
            )
        );
        String raw = result.output().get("rawOutput").toString();
        assertThat(raw).contains("\"x\"").contains("\"NEGATIVE\"");
    }

    @Test
    void extractsJsonFromMixedText() {
        String mixed =
            "Here:\n```json\n{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"a\"," +
            "\"verdict\":\"NEGATIVE\",\"severity\":\"MAJOR\",\"confidence\":0.8}]}\n```";
        var result = parser.parse(
            new SandboxResult(0, Map.of("result.json", mixed.getBytes()), "done", false, Duration.ofSeconds(10))
        );
        assertThat(result.output().get("rawOutput").toString()).contains("findings").contains("NEGATIVE");
    }

    @Test
    void surfacesUsageAndRunnerDebug() {
        String findings =
            "{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"x\",\"verdict\":\"POSITIVE\"," +
            "\"severity\":\"INFO\",\"confidence\":0.9}]}";
        String usage =
            "{\"model\":\"m\",\"inputTokens\":10,\"outputTokens\":5,\"cacheReadTokens\":20," +
            "\"costUsd\":0.12,\"totalCalls\":2}";
        String debug = "{\"attempts\":[],\"usageTotals\":{\"totalCalls\":2}}";
        var result = parser.parse(
            new SandboxResult(
                0,
                Map.of(
                    "result.json",
                    findings.getBytes(),
                    "usage.json",
                    usage.getBytes(),
                    "runner-debug.json",
                    debug.getBytes()
                ),
                "done",
                false,
                Duration.ofSeconds(10)
            )
        );
        assertThat(result.usage()).isNotNull();
        assertThat(result.usage().model()).isEqualTo("m");
        assertThat(result.usage().totalCalls()).isEqualTo(2);
        assertThat(result.usage().inputTokens()).isEqualTo(10);
        assertThat(result.usage().costUsd()).isEqualTo(0.12);
        assertThat(result.output()).containsKey("runnerDebug");
    }

    @Test
    void sanitizesSwiftEscapes() {
        String json =
            "{\"findings\":[{\"practiceSlug\":\"t\",\"title\":\"line1\\nline2\"," +
            "\"verdict\":\"POSITIVE\",\"severity\":\"INFO\",\"confidence\":0.9," +
            "\"reasoning\":\"Text(\\\"\\(weather.temp)°\\\")\"}]}";
        var result = parser.parse(
            new SandboxResult(0, Map.of("result.json", json.getBytes()), "done", false, Duration.ofSeconds(10))
        );
        assertThat(result.success()).isTrue();
        assertThat(result.output().get("rawOutput").toString()).contains("line1\\nline2");
    }

    @Test
    @DisplayName("watchdog-killed marker is surfaced into output")
    void surfacesWatchdogState() {
        String marker = "{\"budgetMs\":540000,\"elapsedMs\":570000,\"reason\":\"x\"}";
        var result = parser.parse(
            new SandboxResult(
                3,
                Map.of("watchdog-killed.json", marker.getBytes(StandardCharsets.UTF_8)),
                "killed",
                false,
                Duration.ofSeconds(570)
            )
        );
        assertThat(result.output()).containsKey("watchdogKilled");
    }

    @Test
    void emptyReviewStateNoOutput() {
        String empty = "{\"findings\":[]}";
        var result = parser.parse(
            new SandboxResult(
                1,
                Map.of("review-state.json", empty.getBytes(StandardCharsets.UTF_8)),
                "failed",
                false,
                Duration.ofSeconds(10)
            )
        );
        assertThat(result.output()).doesNotContainKey("rawOutput");
    }

    @Test
    void zeroCallsUsageIgnored() {
        String findings = "{\"findings\":[]}";
        String usage = "{\"model\":\"m\",\"totalCalls\":0}";
        var result = parser.parse(
            new SandboxResult(
                0,
                Map.of("result.json", findings.getBytes(), "usage.json", usage.getBytes()),
                "done",
                false,
                Duration.ofSeconds(10)
            )
        );
        assertThat(result.usage()).isNull();
    }
}
