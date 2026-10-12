package de.tum.cit.aet.hephaestus.agent.practice.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("unit")
class ProxyStandInAdmissionTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** A version 2 admission body of these {@code {practiceSlug, candidate}} entries. */
    private static JsonNode request(String... entries) {
        return MAPPER.readTree("{\"schemaVersion\":2,\"results\":[" + String.join(",", entries) + "]}");
    }

    private static String entry(String slug, String candidate) {
        return "{\"practiceSlug\":\"" + slug + "\",\"candidate\":" + candidate + "}";
    }

    private static String citation(int line) {
        return "{\"path\":\"Loader.swift\",\"startLine\":" + line + "}";
    }

    private static String problem(String severity, int line) {
        return """
                {"severity":"%s","summary":"The failure is dropped","explanation":"The catch block is empty.",
                 "citations":[%s],"basis":{"type":"introduced","head":0,"explanation":"The change adds it."}}
                """.formatted(severity, citation(line));
    }

    /** A candidate of {@code outcome}, a JSON value as sent; a negative carries one MINOR problem. */
    private static String candidate(String outcome) {
        String problems = "\"NOT_MET\"".equals(outcome) ? ",\"problems\":[" + problem("MINOR", 7) + "]" : "";
        return """
                {"outcome":%s,"summary":"Handles errors","explanation":"The handler records the failure.",
                 "citations":[%s,%s],"evidence":{}%s}
                """.formatted(outcome, citation(3), citation(5), problems);
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void shouldAdmitEveryOutcomeAsSentWithItsGroundsAndIndexedCitations(Outcome outcome) {
        JsonNode request = request(entry("handles-errors", candidate("\"" + outcome.name() + "\"")));
        JsonNode sent = request.deepCopy();

        ObjectNode answer = PracticeRunnerLiveLlmTest.ProxyStandIn.admit(request);

        assertThat(answer.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(answer.path("admissionDigest").asString()).isEqualTo("live-test");
        assertThat(answer.path("withheld")).isEmpty();
        assertThat(answer.path("results")).hasSize(1);
        JsonNode admitted = answer.path("results").get(0);
        assertThat(admitted.has("id")).as("ids are always named").isFalse();
        assertThat(UUID.fromString(admitted.path("practiceResultId").asString()))
                .isNotNull();
        assertThat(admitted.path("practiceSlug").asString()).isEqualTo("handles-errors");
        assertThat(admitted.path("outcome").asString()).isEqualTo(outcome.name());
        JsonNode original = sent.path("results").get(0).path("candidate");
        for (String ground : new String[] {"summary", "explanation"}) {
            assertThat(admitted.path(ground)).as(ground).isEqualTo(original.path(ground));
        }
        assertThat(admitted.path("citations").get(0).path("index").asInt()).isZero();
        assertThat(admitted.path("citations").get(1).path("index").asInt()).isEqualTo(1);
        assertThat(admitted.path("citations").get(1).path("startLine").asInt()).isEqualTo(5);
        if (outcome == Outcome.NOT_MET) {
            assertThat(admitted.path("severity").asString()).isEqualTo("MINOR");
            assertThat(admitted.path("observations")).singleElement().satisfies(child -> {
                assertThat(UUID.fromString(child.path("observationId").asString()))
                        .isNotNull();
                assertThat(child.path("position").asInt()).isZero();
                assertThat(child.path("citations").get(0).path("index").asInt()).isZero();
                assertThat(child.path("citations").get(0).path("startLine").asInt())
                        .isEqualTo(7);
            });
        } else {
            assertThat(admitted.path("severity").isNull()).isTrue();
            assertThat(admitted.path("observations")).isEmpty();
        }
        assertThat(request).as("the request is not changed").isEqualTo(sent);
    }

    @Test
    void shouldStoreTheWorstProblemSeverityWhenTheNegativeStatesNone() {
        String negative = """
                {"outcome":"NOT_MET","summary":"Handles errors","explanation":"Two failures are dropped.",
                 "citations":[],"evidence":{},"problems":[%s,%s]}
                """.formatted(problem("MINOR", 3), problem("MAJOR", 5));

        JsonNode admitted = PracticeRunnerLiveLlmTest.ProxyStandIn.admit(request(entry("handles-errors", negative)))
                .path("results")
                .get(0);

        assertThat(admitted.path("severity").asString()).isEqualTo("MAJOR");
        assertThat(admitted.path("observations")).hasSize(2);
    }

    @Test
    void shouldGiveEachAdmittedResultItsOwnId() {
        JsonNode request = request(
                entry("handles-errors", candidate("\"MET\"")), entry("names-things", candidate("\"UNDETERMINED\"")));

        JsonNode admitted =
                PracticeRunnerLiveLlmTest.ProxyStandIn.admit(request).path("results");

        assertThat(admitted.get(0).path("practiceResultId").asString())
                .isNotEqualTo(admitted.get(1).path("practiceResultId").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "1", "true", "{}", "\"\"", "\"met\"", "\"PASS\""})
    void shouldRefuseAnOutcomeThatNamesNoOutcome(String outcome) {
        JsonNode request = request(entry("handles-errors", candidate(outcome)));

        assertThatThrownBy(() -> PracticeRunnerLiveLlmTest.ProxyStandIn.admit(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseAResultWithoutAnOutcome() {
        JsonNode request = request(entry("handles-errors", "{\"summary\":\"Handles errors\",\"citations\":[]}"));

        assertThatThrownBy(() -> PracticeRunnerLiveLlmTest.ProxyStandIn.admit(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
