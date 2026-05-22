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

@DisplayName("PracticeDetectionResultParser")
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
    @DisplayName("Structural validation")
    class StructuralValidation {

        @Test
        @DisplayName("returns empty result when jobOutput is null")
        void nullJobOutput() {
            ParseResult result = parser.parse(null);

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).reason()).contains("null");
        }

        @Test
        @DisplayName("returns empty result when rawOutput field is missing")
        void missingRawOutput() {
            ObjectNode jobOutput = objectMapper.createObjectNode();
            jobOutput.put("somethingElse", "value");

            ParseResult result = parser.parse(jobOutput);

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("missing rawOutput");
        }

        @Test
        @DisplayName("returns empty result when rawOutput is blank")
        void blankRawOutput() {
            ParseResult result = parser.parse(wrapRawOutput("  "));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("blank");
        }

        @Test
        @DisplayName("returns empty result when rawOutput is invalid JSON")
        void invalidJson() {
            ParseResult result = parser.parse(wrapRawOutput("not json {{{"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("invalid JSON");
        }

        @Test
        @DisplayName("returns empty result when findings field is missing")
        void missingFindings() {
            ParseResult result = parser.parse(wrapRawOutput("{\"summary\":\"hello\"}"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("missing");
        }

        @Test
        @DisplayName("returns empty result when findings array is empty")
        void emptyFindings() {
            ParseResult result = parser.parse(wrapRawOutput("{\"findings\":[]}"));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("empty");
        }

        @Test
        @DisplayName("keeps all findings when count is high")
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
    @DisplayName("Field validation")
    class FieldValidation {

        @Test
        @DisplayName("valid finding is parsed correctly")
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
        @DisplayName("discards entry with missing practiceSlug")
        void missingPracticeSlug() {
            ObjectNode finding = validFindingNode();
            finding.remove("practiceSlug");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded()).hasSize(1);
            assertThat(result.discarded().get(0).reason()).contains("practiceSlug");
        }

        @Test
        @DisplayName("discards entry with blank title")
        void blankTitle() {
            ObjectNode finding = validFindingNode();
            finding.put("title", "  ");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("title is blank");
        }

        @Test
        @DisplayName("accepts NOT_APPLICABLE verdict")
        void notApplicableVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "NOT_APPLICABLE");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).verdict()).isEqualTo(Verdict.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("normalizes lowercase verdict to uppercase")
        void lowercaseVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "positive");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).verdict()).isEqualTo(Verdict.POSITIVE);
        }

        @Test
        @DisplayName("discards entry with invalid verdict")
        void invalidVerdict() {
            ObjectNode finding = validFindingNode();
            finding.put("verdict", "UNKNOWN_VERDICT");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("invalid verdict");
        }

        @Test
        @DisplayName("normalizes lowercase severity to uppercase")
        void lowercaseSeverity() {
            ObjectNode finding = validFindingNode();
            finding.put("severity", "major");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).severity()).isEqualTo(Severity.MAJOR);
        }

        @Test
        @DisplayName("discards entry with invalid severity")
        void invalidSeverity() {
            ObjectNode finding = validFindingNode();
            finding.put("severity", "EXTREME");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
        }

        @Test
        @DisplayName("discards entry with confidence below 0")
        void confidenceBelowZero() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", -0.5);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("out of range");
        }

        @Test
        @DisplayName("normalizes percentage confidence (85 → 0.85)")
        void percentageConfidence() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 85);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(0.85f);
        }

        @Test
        @DisplayName("discards entry with confidence above 100")
        void confidenceAbove100() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 150);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("out of range");
        }

        @Test
        @DisplayName("truncates oversized title to 255 chars")
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
        @DisplayName("parses optional fields when present")
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
            assertThat(f.evidence().get("key").asText()).isEqualTo("value");
        }

        @Test
        @DisplayName("null optional fields are parsed as null")
        void nullOptionalFields() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            ValidatedFinding f = result.validFindings().get(0);
            assertThat(f.reasoning()).isNull();
            assertThat(f.guidance()).isNull();
            assertThat(f.evidence()).isNull();
        }

        @Test
        @DisplayName("accepts confidence of exactly 0.0")
        void confidenceExactlyZero() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 0.0);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(0.0f);
        }

        @Test
        @DisplayName("accepts confidence of exactly 1.0")
        void confidenceExactlyOne() {
            ObjectNode finding = validFindingNode();
            finding.put("confidence", 1.0);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).confidence()).isEqualTo(1.0f);
        }

        @Test
        @DisplayName("drops oversized evidence without discarding finding")
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
    @DisplayName("Mixed findings")
    class MixedFindings {

        @Test
        @DisplayName("valid and invalid entries produce correct split")
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
        @DisplayName("all invalid entries produce empty validFindings")
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
    @DisplayName("Per-finding suggestedDiffNotes parsing")
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
        @DisplayName("attaches suggestedDiffNotes to the validated finding")
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
        @DisplayName("absent suggestedDiffNotes yields an empty list")
        void absentNotesYieldsEmptyList() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes()).isEmpty();
        }

        @Test
        @DisplayName("skips invalid suggestedDiffNotes entries")
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
        @DisplayName("rejects suggestedDiffNotes pointing at internal workspace paths")
        void rejectsInternalPaths() {
            ObjectNode finding = findingWithSuggestedNotes(
                "error-handling",
                "NEGATIVE",
                "MAJOR",
                suggestedNote("context/target/foo.json", 1, "should be rejected"),
                suggestedNote("src/Real.java", 5, "should be kept")
            );
            String raw = "{\"findings\": [%s]}".formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings().get(0).suggestedDiffNotes()).hasSize(1);
            assertThat(result.validFindings().get(0).suggestedDiffNotes().get(0).filePath()).isEqualTo("src/Real.java");
        }

        @Test
        @DisplayName("preserves endLine when supplied")
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
    @DisplayName("Deduplication")
    class Deduplication {

        @Test
        @DisplayName("keeps all findings including multiple per practice")
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
    @DisplayName("JSON extraction from mixed text")
    class JsonExtractionFromMixedText {

        @Test
        @DisplayName("extracts JSON from text with phase markers")
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
        @DisplayName("returns empty when no valid JSON found in text")
        void returnsEmptyWhenNoJsonInText() {
            String text = "[PHASE0] no json here at all {notjson";

            ParseResult result = parser.parse(wrapRawOutput(text));

            assertThat(result.validFindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("JSON escape sanitization")
    class JsonEscapeSanitization {

        @Test
        @DisplayName("fixes Swift string interpolation \\(variable) in JSON")
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
        @DisplayName("sanitizeJsonEscapes fixes invalid \\( escape")
        void fixesInvalidParenEscape() {
            String input = "print(\\\"\\(error)\\\")";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isEqualTo("print(\\\"\\\\(error)\\\")");
        }

        @Test
        @DisplayName("sanitizeJsonEscapes handles already-escaped \\\\( correctly")
        void handlesAlreadyEscaped() {
            // \\( in the input means the text literally has \( which is valid JSON (\\)
            String input = "print(\\\\(error))";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isEqualTo(input);
        }
    }

    @Nested
    @DisplayName("Contract test")
    class ContractTest {

        @Test
        @DisplayName("parses sample agent output fixture")
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
