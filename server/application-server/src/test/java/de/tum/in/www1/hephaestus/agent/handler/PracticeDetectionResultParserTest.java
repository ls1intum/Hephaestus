package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiscardedEntry;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ParseResult;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PracticeDetectionResultParser")
class PracticeDetectionResultParserTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PracticeDetectionResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new PracticeDetectionResultParser(objectMapper, 100);
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
        @DisplayName("returns empty result when findings exceeds max")
        void exceedsMax() {
            var smallParser = new PracticeDetectionResultParser(objectMapper, 2);
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("findings");
            for (int i = 0; i < 3; i++) {
                arr.add(validFindingNode());
            }

            ParseResult result = smallParser.parse(wrapRawOutput(root.toString()));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("exceeds max");
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
            finding.put("guidanceMethod", "COACHING");
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("key", "value");
            finding.set("evidence", evidence);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            ValidatedFinding f = result.validFindings().get(0);
            assertThat(f.reasoning()).isEqualTo("Some reasoning");
            assertThat(f.guidance()).isEqualTo("Some guidance");
            assertThat(f.guidanceMethod()).isEqualTo(CaMethod.COACHING);
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
            assertThat(f.guidanceMethod()).isNull();
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

        @Test
        @DisplayName("invalid guidanceMethod is silently ignored (not discarded)")
        void invalidGuidanceMethod() {
            ObjectNode finding = validFindingNode();
            finding.put("guidanceMethod", "INVALID_METHOD");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).guidanceMethod()).isNull();
            assertThat(result.discarded()).isEmpty();
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
    @DisplayName("Delivery content extraction")
    class DeliveryContentExtraction {

        private String wrapWithDelivery(String deliveryJson) {
            return """
            {"findings": [%s], "delivery": %s}
            """.formatted(validFindingNode().toString(), deliveryJson);
        }

        @Test
        @DisplayName("delivery with mrNote and diffNotes parsed correctly")
        void deliveryPresentParsedCorrectly() {
            String raw = """
                {
                  "findings": [%s],
                  "delivery": {
                    "mrNote": "Please fix the tests.",
                    "diffNotes": [
                      {"filePath": "src/Foo.java", "startLine": 10, "endLine": 20, "body": "Add null check here."}
                    ]
                  }
                }
                """.formatted(validFindingNode().toString());
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isEqualTo("Please fix the tests.");
            assertThat(result.delivery().diffNotes()).hasSize(1);
            DiffNote note = result.delivery().diffNotes().get(0);
            assertThat(note.filePath()).isEqualTo("src/Foo.java");
            assertThat(note.startLine()).isEqualTo(10);
            assertThat(note.endLine()).isEqualTo(20);
            assertThat(note.body()).isEqualTo("Add null check here.");
        }

        @Test
        @DisplayName("missing delivery does not affect findings")
        void deliveryMissingFindingsStillValid() {
            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(validFindingNode())));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.delivery()).isNull();
        }

        @Test
        @DisplayName("malformed delivery does not affect findings")
        void deliveryMalformedFindingsStillValid() {
            String raw = """
                {"findings": [%s], "delivery": "not an object"}
                """.formatted(validFindingNode().toString());
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.delivery()).isNull();
        }

        @Test
        @DisplayName("mrNote null but diffNotes still parsed")
        void mrNoteNullDiffNotesStillParsed() {
            String raw = wrapWithDelivery(
                """
                {"diffNotes": [{"filePath": "a.txt", "startLine": 1, "body": "Fix"}]}
                """
            );
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
        }

        @Test
        @DisplayName("mrNote truncated at max length")
        void mrNoteTruncatedAtMaxLength() {
            String longNote = "x".repeat(PracticeDetectionResultParser.MAX_MR_NOTE_LENGTH + 1000);
            String raw = wrapWithDelivery("{\"mrNote\": \"%s\"}".formatted(longNote));
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).hasSize(PracticeDetectionResultParser.MAX_MR_NOTE_LENGTH);
        }

        @Test
        @DisplayName("diffNotes with missing fields are skipped")
        void diffNotesMissingFieldsSkipped() {
            String raw = wrapWithDelivery(
                """
                {
                  "diffNotes": [
                    {"startLine": 1, "body": "Missing filePath"},
                    {"filePath": "a.txt", "body": "Missing startLine"},
                    {"filePath": "b.txt", "startLine": 5, "body": "Valid note"}
                  ]
                }
                """
            );
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
            assertThat(result.delivery().diffNotes().get(0).filePath()).isEqualTo("b.txt");
        }

        @Test
        @DisplayName("diffNotes exceeding max are capped")
        void diffNotesExceedMaxCapped() {
            var sb = new StringBuilder("{\"diffNotes\": [");
            for (int i = 0; i < PracticeDetectionResultParser.MAX_DIFF_NOTES + 5; i++) {
                if (i > 0) sb.append(',');
                sb.append(
                    "{\"filePath\": \"f%d.txt\", \"startLine\": %d, \"body\": \"Note %d\"}".formatted(i, i + 1, i)
                );
            }
            sb.append("]}");
            String raw = wrapWithDelivery(sb.toString());
            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(PracticeDetectionResultParser.MAX_DIFF_NOTES);
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
            assertThat(first.guidanceMethod()).isEqualTo(CaMethod.COACHING);

            // Verify negative finding
            ValidatedFinding negative = result.validFindings().get(1);
            assertThat(negative.verdict()).isEqualTo(Verdict.NEGATIVE);
            assertThat(negative.severity()).isEqualTo(Severity.MAJOR);

            // Verify NOT_APPLICABLE and NEEDS_REVIEW verdicts
            assertThat(result.validFindings().get(3).verdict()).isEqualTo(Verdict.NOT_APPLICABLE);
            assertThat(result.validFindings().get(4).verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
        }
    }
}
