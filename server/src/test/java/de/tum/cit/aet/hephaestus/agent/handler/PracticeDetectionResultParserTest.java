package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiscardedEntry;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ParseResult;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PracticeDetectionResultParserTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private PracticeDetectionResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new PracticeDetectionResultParser(objectMapper);
    }

    /** Wraps a raw JSON string in the jobOutput envelope ({rawOutput: "..."}). */
    private ObjectNode wrapRawOutput(String rawJson) {
        ObjectNode jobOutput = objectMapper.createObjectNode();
        jobOutput.put("rawOutput", rawJson);
        return jobOutput;
    }

    /** Creates a minimal valid finding JSON object. */
    private ObjectNode validFindingNode() {
        ObjectNode finding = objectMapper.createObjectNode();
        finding.put("practiceSlug", "pr-description-quality");
        finding.put("title", "Good PR description");
        finding.put("verdict", "POSITIVE");
        finding.put("severity", "INFO");
        finding.put("confidence", 0.95);
        return finding;
    }

    /** Wraps findings into a complete raw output JSON string. */
    private String wrapFindings(ObjectNode... findings) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arr = root.putArray("findings");
        for (ObjectNode f : findings) {
            arr.add(f);
        }
        return root.toString();
    }

    @Nested
    class StructuralValidation {

        @Test
        void nullJobOutput() {
            ParseResult result = parser.parse(null);

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).reason()).contains("null");
        }

        @Test
        void missingRawOutput() {
            ObjectNode jobOutput = objectMapper.createObjectNode();
            jobOutput.put("somethingElse", "value");

            ParseResult result = parser.parse(jobOutput);

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("missing rawOutput");
        }

        @Test
        void blankRawOutput() {
            ParseResult result = parser.parse(wrapRawOutput("  "));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("blank");
        }

        @Test
        void invalidJson() {
            ParseResult result = parser.parse(wrapRawOutput("not json {{{"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("invalid JSON");
        }

        @Test
        void missingFindings() {
            ParseResult result = parser.parse(wrapRawOutput("{\"summary\":\"hello\"}"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("missing");
        }

        @Test
        void emptyFindings() {
            ParseResult result = parser.parse(wrapRawOutput("{\"findings\":[]}"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("empty");
        }

        @Test
        void keepsAllFindings() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("findings");
            for (int i = 0; i < 5; i++) {
                ObjectNode f = validFindingNode();
                f.put("practiceSlug", "practice-" + i);
                arr.add(f);
            }

            ParseResult result = parser.parse(wrapRawOutput(root.toString()));

            assertThat(result.validFindings()).hasSize(5);
            assertThat(result.validFindings().get(0).practiceSlug()).isEqualTo("practice-0");
            assertThat(result.validFindings().get(4).practiceSlug()).isEqualTo("practice-4");
        }

        @Test
        @DisplayName("skips non-object entries in findings array")
        void nonObjectEntry() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("findings");
            arr.add("not an object");
            arr.add(validFindingNode());

            ParseResult result = parser.parse(wrapRawOutput(root.toString()));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).reason()).contains("not a JSON object");
        }
    }

    @Nested
    class FieldValidation {

        @Test
        void validFinding() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.discarded()).isEmpty();

            ValidatedFinding f = result.validFindings().get(0);
            assertThat(f.practiceSlug()).isEqualTo("pr-description-quality");
            assertThat(f.title()).isEqualTo("Good PR description");
            assertThat(f.verdict()).isEqualTo(Verdict.POSITIVE);
            assertThat(f.severity()).isEqualTo(Severity.INFO);
            assertThat(f.confidence()).isEqualTo(0.95f);
        }

        @Test
        void missingPracticeSlug() {
            ObjectNode finding = validFindingNode();
            finding.remove("practiceSlug");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).reason()).contains("practiceSlug");
        }

        @Test
        void blankTitle() {
            ObjectNode finding = validFindingNode();
            finding.put("title", "  ");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("title is blank");
        }

        @Test
        void notApplicableVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "NOT_APPLICABLE");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).verdict()).isEqualTo(Verdict.NOT_APPLICABLE);
        }

        @Test
        void lowercaseVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "positive");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).verdict()).isEqualTo(Verdict.POSITIVE);
        }

        @Test
        void invalidVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "UNKNOWN_VERDICT");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("invalid verdict");
        }

        @Test
        void lowercaseSeverity() {
            ObjectNode finding = validFindingNode();
            finding.put("severity", "major");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).severity()).isEqualTo(Severity.MAJOR);
        }

        @Test
        void invalidSeverity() {
            ObjectNode finding = validFindingNode();
            finding.put("severity", "EXTREME");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
        }

        @Test
        void confidenceBelowZero() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", -0.5);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("out of range");
        }

        @Test
        void percentageConfidence() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 85);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(0.85f);
        }

        @Test
        void confidenceAbove100() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 150);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("out of range");
        }

        @Test
        void oversizedTitle() {
            ObjectNode finding = validFindingNode();
            finding.put("title", "x".repeat(300));

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            String title = result.validFindings().get(0).title();
            assertThat(title).hasSize(255);
            assertThat(title).endsWith("...");
        }

        @Test
        @DisplayName("normalizes practice slug with underscores")
        void slugNormalization() {
            ObjectNode finding = validFindingNode();
            finding.put("practiceSlug", "PR_Description_Quality");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).practiceSlug()).isEqualTo("pr-description-quality");
        }

        @Test
        void optionalFieldsPresent() {
            ObjectNode finding = validFindingNode();
            finding.put("reasoning", "Some reasoning");
            finding.put("guidance", "Some guidance");
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("key", "value");
            finding.set("evidence", evidence);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            ValidatedFinding f = result.validFindings().get(0);
            assertThat(f.reasoning()).isEqualTo("Some reasoning");
            assertThat(f.guidance()).isEqualTo("Some guidance");
            assertThat(f.evidence()).isNotNull();
            assertThat(f.evidence().get("key").asString()).isEqualTo("value");
        }

        @Test
        void nullOptionalFields() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            ValidatedFinding f = result.validFindings().get(0);
            assertThat(f.reasoning()).isNull();
            assertThat(f.guidance()).isNull();
            assertThat(f.evidence()).isNull();
        }

        @Test
        void confidenceExactlyZero() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 0.0);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(0.0f);
        }

        @Test
        void confidenceExactlyOne() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 1.0);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(1.0f);
        }

        @Test
        void oversizedEvidenceDropped() {
            ObjectNode finding = validFindingNode();
            ObjectNode evidence = objectMapper.createObjectNode();
            // Create evidence exceeding 64KB
            evidence.put("data", "x".repeat(70_000));
            finding.set("evidence", evidence);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).evidence()).isNull();
            assertThat(result.discarded()).isEmpty();
        }

        @Test
        @DisplayName("truncates reasoning exceeding 10000 chars")
        void reasoningTruncated() {
            ObjectNode finding = validFindingNode();
            finding.put("reasoning", "r".repeat(15_000));

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).reasoning()).hasSize(10_000);
        }

        @Test
        @DisplayName("truncates guidance exceeding 5000 chars")
        void guidanceTruncated() {
            ObjectNode finding = validFindingNode();
            finding.put("guidance", "g".repeat(8_000));

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).guidance()).hasSize(5_000);
        }
    }

    @Nested
    class MixedFindings {

        @Test
        void mixedValidAndInvalid() {
            ObjectNode valid = validFindingNode();
            ObjectNode invalid = validFindingNode();
            invalid.put("verdict", "BOGUS");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(valid, invalid)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).index()).isEqualTo(1);
        }

        @Test
        void allInvalid() {
            ObjectNode bad1 = validFindingNode();
            bad1.remove("practiceSlug");
            ObjectNode bad2 = validFindingNode();
            bad2.put("confidence", -1);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(bad1, bad2)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded()).hasSize(2);
        }
    }

    @Nested
    class PerFindingSuggestedDiffNotes {

        private ObjectNode findingWithSuggestedNotes(
            String slug,
            String verdict,
            String severity,
            ObjectNode... notes
        ) {
            ObjectNode finding = objectMapper.createObjectNode();
            finding.put("practiceSlug", slug);
            finding.put("title", "Issue found");
            finding.put("verdict", verdict);
            finding.put("severity", severity);
            finding.put("confidence", 0.90);
            ArrayNode arr = finding.putArray("suggestedDiffNotes");
            for (ObjectNode note : notes) {
                arr.add(note);
            }
            return finding;
        }

        private ObjectNode suggestedNote(String filePath, int startLine, String body) {
            ObjectNode note = objectMapper.createObjectNode();
            note.put("filePath", filePath);
            note.put("startLine", startLine);
            note.put("body", body);
            return note;
        }

        @Test
        void attachesNotesToFinding() {
            ObjectNode finding = findingWithSuggestedNotes(
                "error-handling",
                "NEGATIVE",
                "MAJOR",
                suggestedNote("src/Main.java", 42, "Add error handling here.")
            );
            String raw = "{\"findings\": [%s]}".formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes()).hasSize(1);
            DiffNote note = result.validFindings().get(0).suggestedDiffNotes().get(0);
            assertThat(note.filePath()).isEqualTo("src/Main.java");
            assertThat(note.startLine()).isEqualTo(42);
            assertThat(note.body()).isEqualTo("Add error handling here.");
        }

        @Test
        void absentNotesYieldsEmptyList() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes()).isEmpty();
        }

        @Test
        void skipsInvalidEntries() {
            ObjectNode finding = objectMapper.createObjectNode();
            finding.put("practiceSlug", "error-handling");
            finding.put("title", "Issue");
            finding.put("verdict", "NEGATIVE");
            finding.put("severity", "MAJOR");
            finding.put("confidence", 0.90);
            ArrayNode arr = finding.putArray("suggestedDiffNotes");
            ObjectNode bad1 = objectMapper.createObjectNode();
            bad1.put("startLine", 1);
            bad1.put("body", "missing file path");
            arr.add(bad1);
            ObjectNode bad2 = objectMapper.createObjectNode();
            bad2.put("filePath", "src/A.java");
            bad2.put("startLine", 1);
            arr.add(bad2);
            arr.add(suggestedNote("src/Valid.java", 5, "Valid note"));

            String raw = "{\"findings\": [%s]}".formatted(finding.toString());
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes().get(0).filePath()).isEqualTo(
                "src/Valid.java"
            );
        }

        @Test
        void rejectsInternalPaths() {
            ObjectNode finding = findingWithSuggestedNotes(
                "error-handling",
                "NEGATIVE",
                "MAJOR",
                suggestedNote("inputs/context/foo.json", 1, "should be rejected"),
                suggestedNote("src/Real.java", 5, "should be kept")
            );
            String raw = "{\"findings\": [%s]}".formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings().get(0).suggestedDiffNotes()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes().get(0).filePath()).isEqualTo("src/Real.java");
        }

        @Test
        void preservesEndLine() {
            ObjectNode note = suggestedNote("src/Range.java", 10, "Multi-line issue");
            note.put("endLine", 20);
            ObjectNode finding = findingWithSuggestedNotes("error-handling", "NEGATIVE", "MAJOR", note);
            String raw = "{\"findings\": [%s]}".formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings().get(0).suggestedDiffNotes()).hasSize(1);
            DiffNote diffNote = result.validFindings().get(0).suggestedDiffNotes().get(0);
            assertThat(diffNote.startLine()).isEqualTo(10);
            assertThat(diffNote.endLine()).isEqualTo(20);
        }
    }

    @Nested
    class Deduplication {

        @Test
        void keepsAllFindingsPerPractice() {
            ObjectNode f1 = validFindingNode();
            f1.put("practiceSlug", "error-handling");
            f1.put("confidence", 0.85);
            f1.put("title", "First violation");

            ObjectNode f2 = validFindingNode();
            f2.put("practiceSlug", "error-handling");
            f2.put("confidence", 0.95);
            f2.put("title", "Second violation");

            ObjectNode f3 = validFindingNode();
            f3.put("practiceSlug", "code-hygiene");
            f3.put("confidence", 0.90);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(f1, f2, f3)));

            // All three findings kept — no dedup
            assertThat(result.validFindings()).hasSize(3);
            assertThat(
                result
                    .validFindings()
                    .stream()
                    .filter(f -> f.practiceSlug().equals("error-handling"))
                    .count()
            ).isEqualTo(2);
            assertThat(
                result
                    .validFindings()
                    .stream()
                    .anyMatch(f -> f.practiceSlug().equals("code-hygiene"))
            ).isTrue();
        }
    }

    @Nested
    class JsonExtractionFromMixedText {

        @Test
        void extractsJsonFromPhaseMarkers() {
            String mixed = """
                [PHASE0] Context loaded: 1 files changed
                [PHASE1] RELEVANT: hardcoded-secrets
                [PHASE4] Output ready
                {"findings": [%s]}
                """.formatted(validFindingNode().toString());

            ParseResult result = parser.parse(wrapRawOutput(mixed));

            assertThat(result.validFindings()).hasSize(1);
        }

        @Test
        void returnsEmptyWhenNoJsonInText() {
            String text = "[PHASE0] no json here at all {notjson";

            ParseResult result = parser.parse(wrapRawOutput(text));

            assertThat(result.validFindings()).isEmpty();
        }
    }

    @Nested
    class JsonEscapeSanitization {

        @Test
        void fixesSwiftInterpolation() {
            // Simulate agent output with Swift \(error) in code snippets
            // Jackson would fail on \( because it's not a valid JSON escape
            String rawWithSwiftEscapes = """
                {"findings":[{"practiceSlug":"silent-failure","title":"Empty catch","verdict":"NEGATIVE","severity":"MAJOR","confidence":0.95,"guidance":"```swift\\nprint(\\"Error: \\(error)\\")\\n```"}]}
                """;

            ParseResult result = parser.parse(wrapRawOutput(rawWithSwiftEscapes));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).practiceSlug()).isEqualTo("silent-failure");
            assertThat(result.validFindings().get(0).guidance()).contains("Error:");
        }

        @Test
        void fixesInvalidParenEscape() {
            String input = "print(\\\"\\(error)\\\")";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isEqualTo("print(\\\"\\\\(error)\\\")");
        }

        @Test
        void handlesAlreadyEscaped() {
            // \\( in the input means the text literally has \( which is valid JSON (\\)
            String input = "print(\\\\(error))";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isEqualTo(input);
        }
    }

    @Nested
    class ContractTest {

        @Test
        void parseSampleFixture() throws Exception {
            InputStream is = getClass().getResourceAsStream("/practices/finding/sample-agent-output.json");
            assertThat(is).as("sample fixture must exist").isNotNull();

            JsonNode fixture = objectMapper.readTree(is);
            // Wrap in jobOutput envelope
            ObjectNode jobOutput = objectMapper.createObjectNode();
            jobOutput.put("rawOutput", objectMapper.writeValueAsString(fixture));

            ParseResult result = parser.parse(jobOutput);

            assertThat(result.validFindings()).hasSize(5);
            assertThat(result.discarded()).isEmpty();

            // Verify first finding
            ValidatedFinding first = result.validFindings().get(0);
            assertThat(first.practiceSlug()).isEqualTo("pr-description-quality");
            assertThat(first.verdict()).isEqualTo(Verdict.POSITIVE);

            // Verify negative finding
            ValidatedFinding negative = result.validFindings().get(1);
            assertThat(negative.verdict()).isEqualTo(Verdict.NEGATIVE);
            assertThat(negative.severity()).isEqualTo(Severity.MAJOR);

            // Verify remaining verdicts
            assertThat(result.validFindings().get(3).verdict()).isEqualTo(Verdict.POSITIVE);
            assertThat(result.validFindings().get(4).verdict()).isEqualTo(Verdict.NEGATIVE);
        }
    }
}
