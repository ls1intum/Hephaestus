package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The shared fixtures that {@code lib/contract.test.ts} reads too (§17 of the cutover interfaces): both sides admit
 * the same candidates and refuse the same values with the same code.
 */
class CandidateFixturesTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURES = "practice-results/";

    private static final Map<String, Class<? extends Candidate>> CANDIDATES = Map.of(
            "candidate-met-with-example.json", MetCandidate.class,
            "candidate-not-applicable.json", NotApplicableCandidate.class,
            "candidate-not-met.json", NotMetCandidate.class,
            "candidate-undetermined.json", UndeterminedCandidate.class);

    @Test
    void shouldParseEveryCandidateFixtureToItsOutcomeWhenTheFixtureIsValid() throws IOException {
        Map<String, JsonNode> fixtures = resources("candidate-*.json");
        assertThat(fixtures.keySet()).containsExactlyInAnyOrderElementsOf(CANDIDATES.keySet());

        fixtures.forEach((name, fixture) -> assertThat(CandidateParser.parse(fixture))
                .as(name)
                .isInstanceOf(Objects.requireNonNull(CANDIDATES.get(name))));
    }

    @Test
    void shouldAcceptOnlyFullLowercaseHexRevisionsWhenTheSharedCorpusIsParsed() throws IOException {
        ObjectNode corpus = fixture("citation-revisions.json");
        for (String verdict : List.of("valid", "invalid")) {
            for (JsonNode revision : corpus.path(verdict)) {
                ObjectNode candidate = fixture("candidate-met-with-example.json");
                ((ObjectNode) candidate.path("citations").get(0)).put("revision", revision.asString());
                if (verdict.equals("valid")) {
                    assertThatCode(() -> CandidateParser.parse(candidate))
                            .as(revision.asString())
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> CandidateParser.parse(candidate))
                            .as(revision.asString())
                            .isInstanceOfSatisfying(
                                    CandidateRefusedException.class,
                                    refused -> assertThat(refused.refusal())
                                            .isEqualTo(CandidateRefusal.MALFORMED_CANDIDATE));
                }
            }
        }
    }

    @Test
    void shouldStoreTheWorstProblemSeverityWhenTheNotMetFixtureDeclaresNone() throws IOException {
        NotMetCandidate candidate = (NotMetCandidate) CandidateParser.parse(fixture("candidate-not-met.json"));

        assertThat(candidate.declaredSeverity()).isNull();
        assertThat(candidate.severity()).isEqualTo(Severity.MAJOR);
        assertThat(candidate.problems())
                .extracting(CandidateProblem::severity)
                .containsExactly(Severity.MAJOR, Severity.MINOR);
        assertThat(candidate.example()).isNotNull();
        assertThat(candidate.openQuestions()).hasSize(1);
        assertThat(candidate.coverage().path("complete").asBoolean()).isFalse();
    }

    @Test
    void shouldParseEveryAdmissionCandidateWhenTheAdmissionRequestIsShared() throws IOException {
        JsonNode request = fixture("admission-request.json");
        List<JsonNode> candidates = new ArrayList<>();
        request.path("results").forEach(result -> candidates.add(result.path("candidate")));
        Map<String, JsonNode> fixtures = resources("candidate-*.json");

        assertThat(candidates)
                .allSatisfy(candidate -> assertThat(
                                CandidateParser.parse(candidate).outcome().name())
                        .isEqualTo(candidate.path("outcome").asString()))
                .as("admission-request.json carries the candidate fixtures unchanged")
                .containsExactlyInAnyOrderElementsOf(fixtures.values());
    }

    @Test
    void shouldRefuseEveryInvalidFixtureWithItsSidecarCodeWhenTheFixtureIsParsed() throws IOException {
        Map<String, JsonNode> invalid = resources("invalid-*.json");
        JsonNode sidecar = Objects.requireNonNull(invalid.remove("invalid-refusals.json"), "invalid-refusals.json");
        Map<String, String> codes = new LinkedHashMap<>();
        sidecar.properties()
                .forEach(entry -> codes.put(entry.getKey(), entry.getValue().asString()));

        assertThat(codes.keySet()).as("the sidecar names every invalid fixture").isEqualTo(invalid.keySet());
        assertThat(Set.copyOf(codes.values()))
                .as("the fixtures refuse with every code")
                .isEqualTo(Arrays.stream(CandidateRefusal.values())
                        .map(CandidateRefusal::code)
                        .collect(Collectors.toSet()));
        invalid.forEach((name, fixture) -> assertThatThrownBy(() -> CandidateParser.parse(fixture))
                .as(name)
                .isInstanceOfSatisfying(
                        CandidateRefusedException.class,
                        refused -> assertThat(refused.reasonCode()).as(name).isEqualTo(codes.get(name))));
    }

    @Test
    void shouldReportAnEmptyListAsItsRuleWhenAListMustNameSomething() throws IOException {
        ObjectNode noQuestion = fixture("candidate-undetermined.json");
        evidence(noQuestion).putArray("undecidability");
        assertRefused(noQuestion, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode nothingConsulted = fixture("candidate-met-with-example.json");
        ((ObjectNode) evidence(nothingConsulted).get("search")).putArray("consulted");
        assertRefused(nothingConsulted, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode noProblem = fixture("candidate-not-met.json");
        noProblem.putArray("problems");
        assertRefused(noProblem, CandidateRefusal.OUTCOME_PROBLEM_MISMATCH);

        ObjectNode emptyProblemsOnMet = fixture("candidate-met-with-example.json");
        emptyProblemsOnMet.putArray("problems");
        assertRefused(emptyProblemsOnMet, CandidateRefusal.OUTCOME_PROBLEM_MISMATCH);

        ObjectNode omitsNothing = fixture("candidate-not-met.json");
        ((ObjectNode) omitsNothing.get("coverage")).putArray("omitted");
        assertRefused(omitsNothing, CandidateRefusal.COVERAGE_INCONSISTENT);
    }

    @Test
    void shouldDropAnEmptyOmissionListWhenCoverageIsComplete() throws IOException {
        ObjectNode candidate = fixture("candidate-met-with-example.json");
        ((ObjectNode) candidate.get("coverage")).putArray("omitted");

        assertThat(CandidateParser.parse(candidate).coverage().has("omitted")).isFalse();
    }

    @Test
    void shouldRefuseAsMalformedWhenACitationBreaksItsShape() throws IOException {
        ObjectNode sideless = fixture("candidate-met-with-example.json");
        firstCitation(sideless).remove("side");
        assertRefused(sideless, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode sideOffDiff = fixture("candidate-not-applicable.json");
        firstCitation(sideOffDiff).put("side", "NEW");
        assertRefused(sideOffDiff, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode revisionOffTree = fixture("candidate-not-applicable.json");
        firstCitation(revisionOffTree).put("revision", "a".repeat(40));
        assertRefused(revisionOffTree, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode shortRevision = fixture("candidate-met-with-example.json");
        firstCitation(shortRevision).put("revision", "a".repeat(39));
        assertRefused(shortRevision, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode backwards = fixture("candidate-met-with-example.json");
        firstCitation(backwards).put("startLine", 9).put("endLine", 8);
        assertRefused(backwards, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode emptySource = fixture("candidate-met-with-example.json");
        firstCitation(emptySource).put("sourceKind", "");
        assertRefused(emptySource, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode emptyArtifact = fixture("candidate-met-with-example.json");
        firstCitation(emptyArtifact).put("artifactPath", "");
        assertRefused(emptyArtifact, CandidateRefusal.MALFORMED_CANDIDATE);
    }

    @Test
    void shouldBoundLineNumbersAsTheRunnerDoesWhenACitationIsRead() throws IOException {
        ObjectNode zero = fixture("candidate-met-with-example.json");
        firstCitation(zero).put("startLine", 0);
        assertRefused(zero, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode pastInt = fixture("candidate-met-with-example.json");
        firstCitation(pastInt).put("endLine", Integer.MAX_VALUE + 1L);
        assertRefused(pastInt, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode fraction = fixture("candidate-met-with-example.json");
        firstCitation(fraction).put("endLine", 72.5);
        assertRefused(fraction, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode last = fixture("candidate-met-with-example.json");
        firstCitation(last).put("startLine", Integer.MAX_VALUE).put("endLine", Integer.MAX_VALUE);
        assertThat(CandidateParser.parse(last)).isInstanceOf(MetCandidate.class);

        ObjectNode integralDouble = fixture("candidate-met-with-example.json");
        firstCitation(integralDouble).put("startLine", 72.0).put("endLine", 72.0);
        assertThat(CandidateParser.parse(integralDouble)).isInstanceOf(MetCandidate.class);
    }

    @Test
    void shouldRefuseAsMalformedWhenAProblemPartExceedsTheBoundTheRunnerUses() throws IOException {
        ObjectNode longBasis = fixture("candidate-not-met.json");
        basis(longBasis).put("explanation", "x".repeat(CandidateLimits.PROBLEM_EXPLANATION_CHARS + 1));
        assertRefused(longBasis, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode longCriterion = fixture("candidate-not-met.json");
        basis(longCriterion)
                .put("type", "duty")
                .put("criterionQuote", "x".repeat(CandidateLimits.QUOTE_CHARS + 1))
                .put("event", 0)
                .remove("head");
        assertRefused(longCriterion, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode notSlug = fixture("candidate-not-met.json");
        problem(notSlug, 0).put("key", "Swallowed-Timeout");
        assertRefused(notSlug, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode sameKey = fixture("candidate-not-met.json");
        problem(sameKey, 0).put("key", "swallowed-error");
        problem(sameKey, 1).put("key", "swallowed-error");
        assertRefused(sameKey, CandidateRefusal.DUPLICATE_PROBLEM_KEY);
    }

    @Test
    void shouldRefuseAsMalformedWhenAnOmissionOrWarrantExceedsItsBound() throws IOException {
        ObjectNode twoLines = fixture("candidate-not-met.json");
        omission(twoLines).put("what", "lines of the export client\nbeyond the read limit");
        assertRefused(twoLines, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode longWhat = fixture("candidate-not-met.json");
        omission(longWhat).put("what", "x".repeat(CandidateLimits.SUMMARY_CHARS + 1));
        assertRefused(longWhat, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode longUnit = fixture("candidate-not-met.json");
        omission(longUnit).putArray("units").add("x".repeat(CandidateLimits.WARRANT_FIELD_CHARS + 1));
        assertRefused(longUnit, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode longConsulted = fixture("candidate-met-with-example.json");
        ((ObjectNode) evidence(longConsulted).get("search"))
                .putArray("consulted")
                .add("x".repeat(CandidateLimits.WARRANT_FIELD_CHARS + 1));
        assertRefused(longConsulted, CandidateRefusal.MALFORMED_CANDIDATE);
    }

    @Test
    void shouldRefuseAnUnknownKeyButNameTheOutcomeRuleWhenAKnownKeySitsOnTheWrongOutcome() throws IOException {
        ObjectNode unknownKey = fixture("candidate-met-with-example.json");
        unknownKey.put("confidence", "high");
        assertRefused(unknownKey, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode nullOptional = fixture("candidate-not-met.json");
        nullOptional.putNull("severity");
        assertRefused(nullOptional, CandidateRefusal.MALFORMED_CANDIDATE);

        ObjectNode severityOnMet = fixture("candidate-met-with-example.json");
        severityOnMet.put("severity", "MINOR");
        assertRefused(severityOnMet, CandidateRefusal.SEVERITY_OUTCOME_MISMATCH);
    }

    @Test
    void shouldCountCandidateBytesAsJavaScriptWritesThemWhenTheTreeHoldsDoublesOrSurrogates() {
        ObjectNode value = MAPPER.createObjectNode()
                .put("a", 3.0)
                .put("b", "\uD83D\uDE00\u00e9\n")
                .put("c", "\uD800");
        String javaScript = "{\"a\":3,\"b\":\"\uD83D\uDE00\u00e9\\n\",\"c\":\"\\ud800\"}";

        assertThat(CandidateParser.javaScriptJsonBytes(value))
                .isEqualTo(javaScript.getBytes(StandardCharsets.UTF_8).length);
    }

    private static void assertRefused(JsonNode value, CandidateRefusal expected) {
        assertThatThrownBy(() -> CandidateParser.parse(value))
                .isInstanceOfSatisfying(
                        CandidateRefusedException.class,
                        refused -> assertThat(refused.refusal()).isEqualTo(expected));
    }

    private static ObjectNode evidence(ObjectNode candidate) {
        return (ObjectNode) candidate.get("evidence");
    }

    private static ObjectNode firstCitation(ObjectNode candidate) {
        return (ObjectNode) candidate.get("citations").get(0);
    }

    private static ObjectNode problem(ObjectNode candidate, int index) {
        return (ObjectNode) candidate.get("problems").get(index);
    }

    private static ObjectNode basis(ObjectNode candidate) {
        return (ObjectNode) problem(candidate, 0).get("basis");
    }

    private static ObjectNode omission(ObjectNode candidate) {
        return (ObjectNode) ((ArrayNode) candidate.get("coverage").get("omitted")).get(0);
    }

    private static ObjectNode fixture(String name) throws IOException {
        try (InputStream in = CandidateFixturesTest.class.getResourceAsStream("/" + FIXTURES + name)) {
            return (ObjectNode) MAPPER.readTree(Objects.requireNonNull(in, name));
        }
    }

    private static Map<String, JsonNode> resources(String pattern) throws IOException {
        Map<String, JsonNode> found = new LinkedHashMap<>();
        Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath:" + FIXTURES + pattern);
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                found.put(Objects.requireNonNull(resource.getFilename()), MAPPER.readTree(in));
            }
        }
        return found;
    }
}
