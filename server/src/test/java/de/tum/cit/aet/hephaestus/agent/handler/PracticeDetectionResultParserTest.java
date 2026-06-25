package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiscardedEntry;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ParseResult;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
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
        finding.put("presence", "PRESENT");
        finding.put("assessment", "GOOD");
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
            assertThat(f.presence()).isEqualTo(Presence.PRESENT);
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
        void notApplicableObservation() {
            ObjectNode finding = validFindingNode();
            finding.put("presence", "NOT_APPLICABLE");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).presence()).isEqualTo(Presence.NOT_APPLICABLE);
        }

        @Test
        void lowercaseObservation() {
            ObjectNode finding = validFindingNode();
            finding.put("presence", "present");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).presence()).isEqualTo(Presence.PRESENT);
        }

        @Test
        void invalidObservation() {
            ObjectNode finding = validFindingNode();
            finding.put("presence", "UNKNOWN_PRESENCE");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).isEmpty();
            assertThat(result.discarded().get(0).reason()).contains("invalid presence");
        }

        @Test
        void forwardVocabularyObservations() {
            for (Presence v : new Presence[] { Presence.PRESENT, Presence.ABSENT }) {
                ObjectNode finding = validFindingNode();
                finding.put("presence", v.name());
                // Non-NA presence requires a valence; pair PRESENT->GOOD, ABSENT->BAD for a coherent finding.
                finding.put("assessment", v == Presence.PRESENT ? "GOOD" : "BAD");

                ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

                assertThat(result.validFindings()).hasSize(1);
                assertThat(result.validFindings().get(0).presence()).isEqualTo(v);
            }
        }

        @Test
        void legacyObservationVocabularyIsRejected() {
            // Clean break (ADR 0022): the old OBSERVED/NOT_OBSERVED vocabulary no longer parses — it
            // is discarded exactly like any other unknown presence, matching the DB CHECK.
            for (String legacy : new String[] { "OBSERVED", "NOT_OBSERVED" }) {
                ObjectNode finding = validFindingNode();
                finding.put("presence", legacy);

                ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

                assertThat(result.validFindings()).isEmpty();
                assertThat(result.discarded().get(0).reason()).contains("invalid presence");
            }
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
        void missingSeverityDefaultsToInfoNotDiscarded() {
            // Regression: the model routinely omits severity on OBSERVED/NOT_APPLICABLE findings (the criteria
            // literally say "OBSERVED (no severity)"). Such a finding must be KEPT with severity INFO, never
            // discarded — coerceCoherence re-derives the band anyway, so dropping it silently loses coaching.
            ObjectNode finding = validFindingNode();
            finding.remove("severity");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).severity()).isEqualTo(Severity.INFO);
            assertThat(result.discarded()).isEmpty();
        }

        @Test
        void nullSeverityDefaultsToInfo() {
            ObjectNode finding = validFindingNode();
            finding.putNull("severity");

            ParseResult result = parser.parse(wrapRawOutput(wrapFindings(finding)));

            assertThat(result.validFindings()).hasSize(1);
            assertThat(result.validFindings().get(0).severity()).isEqualTo(Severity.INFO);
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
            invalid.put("presence", "BOGUS");

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
            String presence,
            String severity,
            ObjectNode... notes
        ) {
            ObjectNode finding = objectMapper.createObjectNode();
            finding.put("practiceSlug", slug);
            finding.put("title", "Issue found");
            finding.put("presence", presence);
            // error-handling is a former GOOD practice; an ABSENT gap is a BAD assessment.
            finding.put("assessment", "ABSENT".equalsIgnoreCase(presence) ? "BAD" : "GOOD");
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
                "ABSENT",
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
            finding.put("presence", "ABSENT");
            finding.put("assessment", "BAD");
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
                "ABSENT",
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
            ObjectNode finding = findingWithSuggestedNotes("error-handling", "ABSENT", "MAJOR", note);
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
                {"findings":[{"practiceSlug":"silent-failure","title":"Empty catch","presence":"ABSENT","assessment":"BAD","severity":"MAJOR","confidence":0.95,"guidance":"```swift\\nprint(\\"Error: \\(error)\\")\\n```"}]}
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
            assertThat(first.presence()).isEqualTo(Presence.PRESENT);

            // Verify negative finding
            ValidatedFinding negative = result.validFindings().get(1);
            assertThat(negative.presence()).isEqualTo(Presence.ABSENT);
            assertThat(negative.severity()).isEqualTo(Severity.MAJOR);

            // Verify remaining presences
            assertThat(result.validFindings().get(3).presence()).isEqualTo(Presence.PRESENT);
            assertThat(result.validFindings().get(4).presence()).isEqualTo(Presence.ABSENT);
        }
    }

    @Nested
    @DisplayName("coerceCoherence — structural (observation, severity) invariants")
    class CoerceCoherence {

        private ValidatedFinding finding(Presence presence, Severity severity) {
            // Derive the valence from presence for these structural cases: PRESENT->GOOD (a strength a
            // defect-detector must not emit), ABSENT->BAD (a gap that carries a band), NA->null.
            Assessment assessment =
                presence == Presence.NOT_APPLICABLE
                    ? null
                    : presence == Presence.PRESENT ? Assessment.GOOD : Assessment.BAD;
            return new ValidatedFinding(
                "p",
                "t",
                presence,
                assessment,
                severity,
                0.9f,
                null,
                "reasoning",
                "guidance",
                List.of()
            );
        }

        @Test
        @DisplayName("defect-detector PRESENT/GOOD is coerced to NOT_APPLICABLE (severity null) with an audit note")
        void defectDetectorObservedToNa() {
            var out = finding(Presence.PRESENT, Severity.MAJOR).coerceCoherence(true);
            assertThat(out.presence()).isEqualTo(Presence.NOT_APPLICABLE);
            // Severity is a band only for a BAD finding (ADR 0022); a coerced NA finding has none.
            assertThat(out.severity()).isNull();
            assertThat(out.reasoning()).startsWith("[auto-downgraded");
        }

        @Test
        @DisplayName("non-defect-detector PRESENT/GOOD keeps presence but nulls severity (no band for a strength)")
        void nonDefectObservedSeverityInfo() {
            var out = finding(Presence.PRESENT, Severity.MAJOR).coerceCoherence(false);
            assertThat(out.presence()).isEqualTo(Presence.PRESENT);
            // A GOOD (strength) finding carries no severity band under ADR 0022.
            assertThat(out.severity()).isNull();
        }

        @Test
        @DisplayName("NOT_OBSERVED with INFO severity is raised to MINOR (a gap must carry a band)")
        void notObservedInfoToMinor() {
            var out = finding(Presence.ABSENT, Severity.INFO).coerceCoherence(false);
            assertThat(out.presence()).isEqualTo(Presence.ABSENT);
            assertThat(out.severity()).isEqualTo(Severity.MINOR);
        }

        @Test
        @DisplayName("NOT_OBSERVED with a real band is unchanged (identity)")
        void notObservedMajorUnchanged() {
            var in = finding(Presence.ABSENT, Severity.MAJOR);
            assertThat(in.coerceCoherence(false)).isSameAs(in);
        }

        @Test
        @DisplayName("NOT_APPLICABLE severity is nulled (no band for an inapplicable practice)")
        void naSeverityInfo() {
            var out = finding(Presence.NOT_APPLICABLE, Severity.MAJOR).coerceCoherence(false);
            assertThat(out.presence()).isEqualTo(Presence.NOT_APPLICABLE);
            assertThat(out.severity()).isNull();
        }

        @Test
        @DisplayName("defect-detector NOT_OBSERVED defect is preserved with its band")
        void defectDetectorNotObservedPreserved() {
            var out = finding(Presence.ABSENT, Severity.MAJOR).coerceCoherence(true);
            assertThat(out.presence()).isEqualTo(Presence.ABSENT);
            assertThat(out.severity()).isEqualTo(Severity.MAJOR);
        }

        @Test
        @DisplayName("list helper applies the per-slug defect-detector flag")
        void listHelperPerSlug() {
            var dd = new ValidatedFinding(
                "sec",
                "t",
                Presence.PRESENT,
                Assessment.GOOD,
                Severity.INFO,
                0.9f,
                null,
                "r",
                "g",
                List.of()
            );
            var ok = new ValidatedFinding(
                "style",
                "t",
                Presence.PRESENT,
                Assessment.GOOD,
                Severity.MAJOR,
                0.9f,
                null,
                "r",
                "g",
                List.of()
            );
            var out = PracticeDetectionResultParser.coerceCoherence(List.of(dd, ok), Set.of("sec"));
            assertThat(out.get(0).presence()).isEqualTo(Presence.NOT_APPLICABLE);
            assertThat(out.get(1).presence()).isEqualTo(Presence.PRESENT);
            // A PRESENT/GOOD (strength) finding carries no severity band under ADR 0022.
            assertThat(out.get(1).severity()).isNull();
        }

        // Advisory ceiling: craft/process critiques may not present as merge-blockers.

        @Test
        @DisplayName("advisory practice: NOT_OBSERVED MAJOR is capped to MINOR (no merge-block)")
        void advisoryMajorCappedToMinor() {
            var out = finding(Presence.ABSENT, Severity.MAJOR).coerceCoherence(false, true);
            assertThat(out.presence()).isEqualTo(Presence.ABSENT);
            assertThat(out.severity()).isEqualTo(Severity.MINOR);
        }

        @Test
        @DisplayName("advisory practice: NOT_OBSERVED CRITICAL is also capped to MINOR")
        void advisoryCriticalCappedToMinor() {
            var out = finding(Presence.ABSENT, Severity.CRITICAL).coerceCoherence(false, true);
            assertThat(out.severity()).isEqualTo(Severity.MINOR);
        }

        @Test
        @DisplayName("blocking-eligible practice: NOT_OBSERVED MAJOR keeps its band")
        void blockingEligibleMajorPreserved() {
            var out = finding(Presence.ABSENT, Severity.MAJOR).coerceCoherence(false, false);
            assertThat(out.severity()).isEqualTo(Severity.MAJOR);
        }

        @Test
        @DisplayName("list helper: a craft slug's MAJOR is capped, a correctness slug's MAJOR survives")
        void listHelperAppliesAdvisoryCeilingBySlug() {
            var craft = new ValidatedFinding(
                "describe-what-and-why",
                "t",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                0.98f,
                null,
                "r",
                "g",
                List.of()
            );
            var correctness = new ValidatedFinding(
                "handles-errors-instead-of-swallowing-them",
                "t",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                0.95f,
                null,
                "r",
                "g",
                List.of()
            );
            var out = PracticeDetectionResultParser.coerceCoherence(List.of(craft, correctness), Set.of());
            assertThat(out.get(0).severity()).as("craft MAJOR -> MINOR").isEqualTo(Severity.MINOR);
            assertThat(out.get(1).severity()).as("correctness MAJOR preserved").isEqualTo(Severity.MAJOR);
        }

        @Test
        @DisplayName("blocking-eligible set is the curated correctness/security/data-integrity consequence class")
        void blockingEligibleSetIsPinned() {
            assertThat(PracticeDetectionResultParser.BLOCKING_ELIGIBLE_PRACTICES).containsExactlyInAnyOrder(
                "handles-errors-instead-of-swallowing-them",
                "validates-inputs-and-edge-cases-at-the-boundary",
                "avoids-unsafe-panics-and-chosen-crashes",
                "validates-and-escapes-untrusted-input",
                "avoids-insecure-defaults-and-over-broad-permissions",
                "keeps-the-test-suite-honest"
            );
        }
    }
}
