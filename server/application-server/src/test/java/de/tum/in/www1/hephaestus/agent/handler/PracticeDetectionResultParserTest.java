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
        @DisplayName("truncates findings when count exceeds max instead of rejecting all")
        void exceedsMaxTruncates() {
            var smallParser = new PracticeDetectionResultParser(objectMapper, 2);
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode arr = root.putArray("findings");
            for (int i = 0; i < 5; i++) {
                ObjectNode f = validFindingNode();
                f.put("practiceSlug", "practice-" + i);
                arr.add(f);
            }

            ParseResult result = smallParser.parse(wrapRawOutput(root.toString()));

            // Should keep first 2, not reject all 5
            assertThat(result.validFindings()).hasSize(2);
            assertThat(result.validFindings().get(0).practiceSlug()).isEqualTo("practice-0");
            assertThat(result.validFindings().get(1).practiceSlug()).isEqualTo("practice-1");
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
    @DisplayName("SuggestedDiffNotes fallback")
    class SuggestedDiffNotesFallback {

        /** Creates a NEGATIVE finding with suggestedDiffNotes. */
        private ObjectNode negativeFindingWithSuggestedNotes(String slug, String severity, ObjectNode... notes) {
            ObjectNode finding = objectMapper.createObjectNode();
            finding.put("practiceSlug", slug);
            finding.put("title", "Issue found");
            finding.put("verdict", "NEGATIVE");
            finding.put("severity", severity);
            finding.put("confidence", 0.90);
            ArrayNode arr = finding.putArray("suggestedDiffNotes");
            for (ObjectNode note : notes) {
                arr.add(note);
            }
            return finding;
        }

        /** Creates a valid suggestedDiffNote JSON object. */
        private ObjectNode suggestedNote(String filePath, int startLine, String body) {
            ObjectNode note = objectMapper.createObjectNode();
            note.put("filePath", filePath);
            note.put("startLine", startLine);
            note.put("body", body);
            return note;
        }

        @Test
        @DisplayName("collects suggestedDiffNotes from NEGATIVE findings when delivery.diffNotes is empty")
        void collectsFromNegativeFindingsWhenDeliveryEmpty() {
            ObjectNode finding = negativeFindingWithSuggestedNotes(
                "error-handling", "MAJOR",
                suggestedNote("src/Main.java", 42, "Add error handling here.")
            );
            // Delivery present but with empty diffNotes
            String raw = """
                {
                  "findings": [%s],
                  "delivery": {"mrNote": "Summary note", "diffNotes": []}
                }
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isEqualTo("Summary note");
            assertThat(result.delivery().diffNotes()).hasSize(1);
            DiffNote note = result.delivery().diffNotes().get(0);
            assertThat(note.filePath()).isEqualTo("src/Main.java");
            assertThat(note.startLine()).isEqualTo(42);
            assertThat(note.body()).isEqualTo("Add error handling here.");
        }

        @Test
        @DisplayName("collects suggestedDiffNotes when delivery is absent")
        void collectsWhenDeliveryAbsent() {
            ObjectNode finding = negativeFindingWithSuggestedNotes(
                "error-handling", "MAJOR",
                suggestedNote("src/Foo.java", 10, "Fix this.")
            );
            String raw = """
                {"findings": [%s]}
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
        }

        @Test
        @DisplayName("does not collect suggestedDiffNotes from POSITIVE findings")
        void ignoresPositiveFindings() {
            ObjectNode positive = validFindingNode(); // POSITIVE verdict
            ArrayNode arr = positive.putArray("suggestedDiffNotes");
            ObjectNode note = objectMapper.createObjectNode();
            note.put("filePath", "src/Good.java");
            note.put("startLine", 1);
            note.put("body", "This should not appear");
            arr.add(note);

            String raw = """
                {"findings": [%s]}
                """.formatted(positive.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            // No delivery since POSITIVE findings are excluded from fallback
            assertThat(result.delivery()).isNull();
        }

        @Test
        @DisplayName("does not use fallback when delivery.diffNotes already has entries")
        void noFallbackWhenDeliveryHasDiffNotes() {
            ObjectNode finding = negativeFindingWithSuggestedNotes(
                "error-handling", "MAJOR",
                suggestedNote("src/Fallback.java", 5, "Fallback note")
            );
            String raw = """
                {
                  "findings": [%s],
                  "delivery": {
                    "diffNotes": [
                      {"filePath": "src/Original.java", "startLine": 1, "body": "Original note"}
                    ]
                  }
                }
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
            assertThat(result.delivery().diffNotes().get(0).filePath()).isEqualTo("src/Original.java");
        }

        @Test
        @DisplayName("prioritizes higher severity findings (CRITICAL before MINOR)")
        void prioritizesBySeverity() {
            // Create 12 notes total across MINOR and CRITICAL findings to test cap + priority
            ObjectNode minorFinding = negativeFindingWithSuggestedNotes(
                "code-style", "MINOR",
                suggestedNote("src/Minor1.java", 1, "minor-1"),
                suggestedNote("src/Minor2.java", 2, "minor-2"),
                suggestedNote("src/Minor3.java", 3, "minor-3"),
                suggestedNote("src/Minor4.java", 4, "minor-4"),
                suggestedNote("src/Minor5.java", 5, "minor-5"),
                suggestedNote("src/Minor6.java", 6, "minor-6")
            );
            ObjectNode criticalFinding = negativeFindingWithSuggestedNotes(
                "hardcoded-secrets", "CRITICAL",
                suggestedNote("src/Critical1.java", 10, "critical-1"),
                suggestedNote("src/Critical2.java", 20, "critical-2"),
                suggestedNote("src/Critical3.java", 30, "critical-3"),
                suggestedNote("src/Critical4.java", 40, "critical-4"),
                suggestedNote("src/Critical5.java", 50, "critical-5"),
                suggestedNote("src/Critical6.java", 60, "critical-6")
            );

            // MINOR finding listed first in array, but CRITICAL should take priority
            String raw = """
                {"findings": [%s, %s]}
                """.formatted(minorFinding.toString(), criticalFinding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(12); // 6 critical + 6 minor, all under cap

            // First 6 should be CRITICAL (sorted by severity), remaining 6 should be MINOR
            for (int i = 0; i < 6; i++) {
                assertThat(result.delivery().diffNotes().get(i).body()).startsWith("critical-");
            }
            for (int i = 6; i < 12; i++) {
                assertThat(result.delivery().diffNotes().get(i).body()).startsWith("minor-");
            }
        }

        @Test
        @DisplayName("caps at MAX_DIFF_NOTES total")
        void capsAtMaxDiffNotes() {
            // Create one finding with more than MAX_DIFF_NOTES suggestedDiffNotes
            int count = PracticeDetectionResultParser.MAX_DIFF_NOTES + 5;
            ObjectNode[] notes = new ObjectNode[count];
            for (int i = 0; i < count; i++) {
                notes[i] = suggestedNote("src/File" + i + ".java", i + 1, "Note " + i);
            }
            ObjectNode finding = negativeFindingWithSuggestedNotes("error-handling", "MAJOR", notes);

            String raw = """
                {"findings": [%s]}
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(PracticeDetectionResultParser.MAX_DIFF_NOTES);
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
            // Invalid: missing filePath
            ObjectNode bad1 = objectMapper.createObjectNode();
            bad1.put("startLine", 1);
            bad1.put("body", "missing file path");
            arr.add(bad1);
            // Invalid: missing body
            ObjectNode bad2 = objectMapper.createObjectNode();
            bad2.put("filePath", "src/A.java");
            bad2.put("startLine", 1);
            arr.add(bad2);
            // Valid
            arr.add(suggestedNote("src/Valid.java", 5, "Valid note"));

            String raw = """
                {"findings": [%s]}
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
            assertThat(result.delivery().diffNotes().get(0).filePath()).isEqualTo("src/Valid.java");
        }

        @Test
        @DisplayName("preserves mrNote from delivery when fallback fills diffNotes")
        void preservesMrNoteFromDelivery() {
            ObjectNode finding = negativeFindingWithSuggestedNotes(
                "error-handling", "MAJOR",
                suggestedNote("src/Fix.java", 1, "Fix this")
            );
            String raw = """
                {
                  "findings": [%s],
                  "delivery": {"mrNote": "Important summary"}
                }
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isEqualTo("Important summary");
            assertThat(result.delivery().diffNotes()).hasSize(1);
        }

        @Test
        @DisplayName("handles endLine in suggestedDiffNotes")
        void handlesEndLine() {
            ObjectNode note = suggestedNote("src/Range.java", 10, "Multi-line issue");
            note.put("endLine", 20);
            ObjectNode finding = negativeFindingWithSuggestedNotes("error-handling", "MAJOR", note);

            String raw = """
                {"findings": [%s]}
                """.formatted(finding.toString());

            ParseResult result = parser.parse(wrapRawOutput(raw));

            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().diffNotes()).hasSize(1);
            DiffNote diffNote = result.delivery().diffNotes().get(0);
            assertThat(diffNote.startLine()).isEqualTo(10);
            assertThat(diffNote.endLine()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Deduplication")
    class Deduplication {

        @Test
        @DisplayName("deduplicates by practiceSlug keeping highest confidence")
        void deduplicatesByPracticeSlug() {
            ObjectNode f1 = validFindingNode();
            f1.put("practiceSlug", "error-handling");
            f1.put("confidence", 0.85);
            f1.put("title", "Lower confidence");

            ObjectNode f2 = validFindingNode();
            f2.put("practiceSlug", "error-handling");
            f2.put("confidence", 0.95);
            f2.put("title", "Higher confidence");

            ObjectNode f3 = validFindingNode();
            f3.put("practiceSlug", "code-hygiene");
            f3.put("confidence", 0.90);

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(f1, f2, f3)));

            assertThat(result.validFindings()).hasSize(2);
            // error-handling: should keep higher confidence (0.95)
            ValidatedFinding errorHandling = result.validFindings().stream()
                .filter(f -> f.practiceSlug().equals("error-handling"))
                .findFirst().orElseThrow();
            assertThat(errorHandling.confidence()).isEqualTo(0.95f);
            assertThat(errorHandling.title()).isEqualTo("Higher confidence");
            // code-hygiene: should remain
            assertThat(result.validFindings().stream()
                .anyMatch(f -> f.practiceSlug().equals("code-hygiene"))).isTrue();
        }

        @Test
        @DisplayName("no dedup needed when all slugs are unique")
        void noDedupWhenAllUnique() {
            ObjectNode f1 = validFindingNode();
            f1.put("practiceSlug", "slug-a");
            ObjectNode f2 = validFindingNode();
            f2.put("practiceSlug", "slug-b");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(f1, f2)));

            assertThat(result.validFindings()).hasSize(2);
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
                {"findings": [%s], "delivery": {"mrNote": "Review", "diffNotes": []}}
                """.formatted(validFindingNode().toString());

            ParseResult result = parser.parse(wrapRawOutput(mixed));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.delivery()).isNotNull();
            assertThat(result.delivery().mrNote()).isEqualTo("Review");
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
        @DisplayName("sanitizeJsonEscapes preserves valid escapes")
        void preservesValidEscapes() {
            String input = "hello\\nworld\\t\\\"quoted\\\"\\\\backslash";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isEqualTo(input);
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

        @Test
        @DisplayName("sanitizeJsonEscapes is no-op for text without backslashes")
        void noOpWithoutBackslashes() {
            String input = "just plain text with no escapes";
            String result = PracticeDetectionResultParser.sanitizeJsonEscapes(input);
            assertThat(result).isSameAs(input);
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
            assertThat(first.guidanceMethod()).isNull();

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
