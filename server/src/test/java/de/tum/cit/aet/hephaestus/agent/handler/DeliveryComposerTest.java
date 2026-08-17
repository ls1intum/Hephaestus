package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedObservation;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.WithheldObservation;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class DeliveryComposerTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode buildEvidence(List<LocationSpec> locations, List<String> snippets) {
        ObjectNode evidence = objectMapper.createObjectNode();
        if (locations != null && !locations.isEmpty()) {
            ArrayNode citations = evidence.putArray("citations");
            for (LocationSpec loc : locations) {
                ObjectNode citation = citations.addObject();
                citation.put("sourceKind", loc.sourceKind);
                citation.put(
                    "artifactPath",
                    loc.sourceKind.equals("scm.pull-request.diff")
                        ? "inputs/context/diff.patch"
                        : "inputs/context/metadata.json"
                );
                citation.put("path", loc.path);
                if (loc.sourceKind.equals("scm.pull-request.diff")) {
                    citation.put("side", "NEW");
                }
                citation.put("startLine", loc.startLine);
                if (loc.endLine != null) {
                    citation.put("endLine", loc.endLine);
                }
                if (snippets != null && !snippets.isEmpty()) {
                    citation.put("quote", snippets.get(Math.min(citations.size() - 1, snippets.size() - 1)));
                }
                citation.put("quoteRedacted", false);
            }
        }
        return evidence;
    }

    private record LocationSpec(String path, int startLine, Integer endLine, String sourceKind) {
        LocationSpec(String path, int startLine) {
            this(path, startLine, null, "scm.pull-request.diff");
        }

        LocationSpec(String path, int startLine, String sourceKind) {
            this(path, startLine, null, sourceKind);
        }
    }

    private ValidatedObservation positiveObservation(String slug) {
        return new ValidatedObservation(
            slug,
            humanizeTitle(slug) + " (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            null
        );
    }

    private ValidatedObservation negativeObservation(
        String slug,
        String title,
        Severity severity,
        List<LocationSpec> locations,
        List<String> snippets,
        String reasoning
    ) {
        return new ValidatedObservation(
            slug,
            title,
            Presence.ABSENT,
            Assessment.BAD,
            severity,
            buildEvidence(locations, snippets),
            reasoning
        );
    }

    private static String humanizeTitle(String slug) {
        return slug.replace('-', ' ').substring(0, 1).toUpperCase() + slug.replace('-', ' ').substring(1);
    }

    private List<ValidatedObservation> mixedObservations() {
        List<ValidatedObservation> observations = new ArrayList<>();

        observations.add(positiveObservation("error-state-handling"));
        observations.add(positiveObservation("view-decomposition"));
        observations.add(positiveObservation("meaningful-naming"));

        observations.add(
            negativeObservation(
                "avoids-insecure-defaults-and-over-broad-permissions",
                "Hardcoded API key exposed in source",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/APIKeys.swift", 5)),
                List.of("let apiKey = \"sk-abc123\""),
                "An API key is hardcoded directly in source code. Anyone with repository access can extract this secret and use it to make authenticated API calls on your behalf."
            )
        );

        observations.add(
            negativeObservation(
                "fatal-error-crash",
                "Force-unwrap causes crash on invalid URL",
                Severity.MAJOR,
                List.of(new LocationSpec("Views/StockView.swift", 42)),
                List.of("let url = URL(string: urlString)!"),
                "Force-unwrapping URL(string:) will crash the app if urlString contains invalid characters or is malformed. This is a common cause of App Store rejections."
            )
        );

        observations.add(
            negativeObservation(
                "code-hygiene",
                "Commented-out code left in view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/DashboardView.swift", 15)),
                null,
                "Commented-out code adds noise and makes diffs harder to review. Remove dead code and rely on version control history instead."
            )
        );

        observations.add(
            negativeObservation(
                "meaningful-naming",
                "Non-descriptive type name 'Data'",
                Severity.MINOR,
                List.of(new LocationSpec("Models/Data.swift", 8)),
                null,
                "The type name 'Data' shadows Foundation.Data and conveys no domain meaning. Rename to something descriptive like 'PortfolioSnapshot' or 'StockQuote'."
            )
        );

        return observations;
    }

    @Test
    void compose_forIssueArtifact_usesNonBlockingTightenCta() {
        DeliveryContent result = DeliveryComposer.compose(mixedObservations(), ArtifactKinds.ISSUE);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).doesNotContain("to fix");
        assertThat(mrNote).contains("2 suggestions for improvement");
    }

    @Test
    void compose_withMixedObservations_producesExpectedMrNote() {
        List<ValidatedObservation> observations = mixedObservations();

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        assertThat(mrNote).doesNotContain("Nice work");
        assertThat(mrNote).contains("Worth keeping:");

        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).contains("2 suggestions for improvement");

        assertThat(mrNote).doesNotContain("[CRITICAL]");
        assertThat(mrNote).doesNotContain("[MAJOR]");
        assertThat(mrNote).doesNotContain("[MINOR]");
        assertThat(mrNote).contains("Hardcoded API key exposed in source");
        assertThat(mrNote).contains("Config/APIKeys.swift:5");
        assertThat(mrNote).contains("Force-unwrap causes crash on invalid URL");
        assertThat(mrNote).contains("Views/StockView.swift:42");
        assertThat(mrNote).contains("Commented-out code left in view");
        assertThat(mrNote).contains("Views/DashboardView.swift:15");
        assertThat(mrNote).contains("Non-descriptive type name 'Data'");
        assertThat(mrNote).contains("Models/Data.swift:8");

        assertThat(mrNote).doesNotContain("You wrote:");
        assertThat(mrNote).doesNotContain("ProcessInfo.processInfo.environment");
        assertThat(mrNote).doesNotContain("guard let url");
        assertThat(mrNote).doesNotContain("hardcoded directly in source code");
    }

    @Test
    void compose_withAllPositive_producesObservationNoteWithoutPraise() {
        List<ValidatedObservation> observations = List.of(
            positiveObservation("error-state-handling"),
            positiveObservation("view-decomposition"),
            positiveObservation("meaningful-naming")
        );

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("Reviewed against the active practices");
        assertThat(result.mrNote()).doesNotContain("Nice work").doesNotContain("stood out");
        assertThat(result.diffNotes()).isEmpty();
    }

    @Test
    void compose_withAllPositiveAndReasoning_listsEvidenceAnchoredObservations() {
        ValidatedObservation withReasoning = new ValidatedObservation(
            "error-state-handling",
            "Error state handling (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            "Network errors are surfaced to the user via an alert."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(withReasoning));

        assertThat(result).isNotNull();
        assertThat(result.mrNote())
            .contains("What's working well here")
            .contains("Error state handling")
            .contains("Network errors are surfaced")
            .doesNotContain("Nice work")
            .doesNotContain("No issues found");
        assertThat(result.diffNotes()).isEmpty();
    }

    @Test
    void compose_withManyNegatives_allInCompactList() {
        List<ValidatedObservation> observations = new ArrayList<>();

        observations.add(
            negativeObservation(
                "avoids-insecure-defaults-and-over-broad-permissions",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/Keys.swift", 1)),
                List.of("let key = \"secret\""),
                "Secret exposed."
            )
        );
        observations.add(
            negativeObservation(
                "fatal-error-crash",
                "Force unwrap crash",
                Severity.MAJOR,
                List.of(new LocationSpec("Views/A.swift", 10)),
                List.of("url!"),
                "Crash risk."
            )
        );
        observations.add(
            negativeObservation(
                "code-hygiene",
                "Dead code",
                Severity.MINOR,
                List.of(new LocationSpec("Views/B.swift", 20)),
                null,
                "Remove dead code."
            )
        );
        observations.add(
            negativeObservation(
                "meaningful-naming",
                "Bad name",
                Severity.MINOR,
                List.of(new LocationSpec("Models/C.swift", 30)),
                null,
                "Use descriptive names."
            )
        );
        observations.add(
            negativeObservation(
                "error-state-handling",
                "Missing error UI",
                Severity.MINOR,
                List.of(new LocationSpec("Views/D.swift", 40)),
                null,
                "Show errors to user."
            )
        );
        observations.add(
            negativeObservation(
                "view-decomposition",
                "Monolith view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/E.swift", 50)),
                null,
                "Break view into subviews."
            )
        );
        observations.add(
            negativeObservation(
                "accessibility",
                "Missing labels",
                Severity.INFO,
                List.of(new LocationSpec("Views/F.swift", 60)),
                null,
                "Add accessibility labels."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).contains("3 suggestions for improvement (+2 more minor suggestions):");

        int criticalIdx = mrNote.indexOf("\uD83D\uDD34");
        int majorIdx = mrNote.indexOf("\uD83D\uDFE0");
        assertThat(criticalIdx).isGreaterThanOrEqualTo(0);
        assertThat(majorIdx).isGreaterThan(criticalIdx);

        assertThat(result.diffNotes()).hasSize(5);
        assertThat(mrNote).doesNotContain("Missing labels");
    }

    @Test
    void compose_diffNotes_allNegativesGetInlineComments() {
        List<ValidatedObservation> observations = mixedObservations();

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        List<DiffNote> diffNotes = result.diffNotes();

        assertThat(diffNotes).hasSize(4);

        DiffNote secretsNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Config/APIKeys.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(secretsNote.startLine()).isEqualTo(5);
        assertThat(secretsNote.body()).contains("An API key is hardcoded directly in source code");

        DiffNote crashNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Views/StockView.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(crashNote.startLine()).isEqualTo(42);
        assertThat(crashNote.body()).contains("will crash the app if urlString contains invalid characters");

        DiffNote hygieneNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Views/DashboardView.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(hygieneNote.startLine()).isEqualTo(15);

        DiffNote namingNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Models/Data.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(namingNote.startLine()).isEqualTo(8);

        for (DiffNote dn : diffNotes) {
            assertThat(dn.body()).startsWith("**");
        }
    }

    @Test
    void compose_withNull_returnsNull() {
        assertThat(DeliveryComposer.compose(null)).isNull();
    }

    @Test
    void compose_withEmptyList_returnsNull() {
        assertThat(DeliveryComposer.compose(List.of())).isNull();
    }

    @Test
    void compose_nonInlinableObservations_renderedInFullInMrNote() {
        List<ValidatedObservation> observations = List.of(
            negativeObservation(
                "mr-description-quality",
                "MR description is empty",
                Severity.MAJOR,
                null,
                null,
                "The MR description is empty, making it hard for reviewers to understand the changes."
            ),
            negativeObservation(
                "code-hygiene",
                "Unused import",
                Severity.MINOR,
                List.of(new LocationSpec("src/components/Button.tsx", 1)),
                List.of("import React from 'react';"),
                "Remove unused imports."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(observations);
        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        assertThat(mrNote).contains("MR description is empty");
        assertThat(mrNote).contains("hard for reviewers to understand the changes");

        assertThat(mrNote).contains("Unused import");
        assertThat(mrNote).contains("src/components/Button.tsx:1");
        assertThat(mrNote).doesNotContain("Remove unused imports.");

        assertThat(mrNote).contains("Inline comments on the diff:");

        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("src/components/Button.tsx");
    }

    @Test
    void compose_minorObservations_reasoningInDiffNote() {
        List<ValidatedObservation> observations = List.of(
            negativeObservation(
                "code-hygiene",
                "Dead code in view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/DashboardView.swift", 15)),
                null,
                "Commented-out code adds noise."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(observations);
        assertThat(result).isNotNull();

        String mrNote = result.mrNote();
        assertThat(mrNote).contains("Dead code in view");
        assertThat(mrNote).contains("Views/DashboardView.swift:15");
        assertThat(mrNote).contains("Inline comments on the diff:");

        assertThat(result.diffNotes()).hasSize(1);
        DiffNote note = result.diffNotes().get(0);
        assertThat(note.body()).contains("Commented-out code adds noise.");
    }

    @Test
    void compose_withOnlyMinorNegatives_usesImprovementLanguage() {
        List<ValidatedObservation> observations = List.of(
            negativeObservation(
                "code-hygiene",
                "Dead code",
                Severity.MINOR,
                List.of(new LocationSpec("Views/X.swift", 10)),
                null,
                "Clean up dead code."
            ),
            negativeObservation(
                "meaningful-naming",
                "Poor name",
                Severity.INFO,
                List.of(new LocationSpec("Models/Y.swift", 5)),
                null,
                "Use better names."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        assertThat(mrNote).contains("2 suggestions for improvement");
        assertThat(mrNote).doesNotContain("before merging");
    }

    @Test
    void compose_suggestionsOnlyWithPositives_prependsTaskLevelAcknowledgement() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(positiveObservation("scope-one-reviewable-change"));
        observations.add(positiveObservation("ready-and-traceable-handoff"));
        observations.add(
            negativeObservation(
                "describe-what-and-why",
                "PR description lacks a rationale sentence",
                Severity.MINOR,
                List.of(),
                List.of(),
                "The body lists what changed but not why."
            )
        );

        String mrNote = DeliveryComposer.compose(observations).mrNote();

        assertThat(mrNote).startsWith("Nice work ");
        assertThat(mrNote).contains("keeping the change focused and reviewable");
        assertThat(mrNote).contains("linking the change to its issue");
        assertThat(mrNote).contains("to tighten:");
        assertThat(mrNote).contains("1 suggestion for improvement");
    }

    @Test
    void compose_blockingIssue_suppressesOpenerButStillAcknowledgesOneStrength() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(positiveObservation("scope-one-reviewable-change"));
        observations.add(
            negativeObservation(
                "avoids-insecure-defaults-and-over-broad-permissions",
                "Hardcoded API key",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/Keys.swift", 5)),
                List.of("let key = \"abc\""),
                "A secret is committed."
            )
        );

        String mrNote = DeliveryComposer.compose(observations).mrNote();

        assertThat(mrNote).doesNotContain("Nice work");
        assertThat(mrNote).contains("Worth keeping: you're keeping the change focused and reviewable.");
        assertThat(mrNote.indexOf("to tighten")).isLessThan(mrNote.indexOf("Worth keeping"));
        assertThat(mrNote).doesNotContain("before merging");
    }

    @Test
    void compose_uncuratedPositive_acknowledgesGenericallyNeverDropsSilently() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(positiveObservation("some-uncurated-practice-xyz"));
        observations.add(
            negativeObservation(
                "describe-what-and-why",
                "PR description lacks a rationale sentence",
                Severity.MINOR,
                List.of(),
                List.of(),
                "The body lists what changed but not why."
            )
        );

        String mrNote = DeliveryComposer.compose(observations).mrNote();

        assertThat(mrNote).startsWith("Nice work here");
        assertThat(mrNote).doesNotContain("some uncurated practice xyz");
        assertThat(mrNote).contains("to tighten:");
    }

    @Test
    void sanitizeStudentText_stripsInternalGradingVocabulary() {
        String leaked1 = "The body has no rationale, which results in a NEGATIVE observation with MINOR severity.";
        String leaked2 =
            "This exceeds the ≤200 line threshold for a POSITIVE observation, placing it in the INFO severity band.";
        String leaked3 = "The title is generic, violating the practice that requires an imperative summary.";

        String leaked4 =
            "The title is descriptive but the body only lists what was done without a quoted sentence " +
            "that explains why. The practice requires a specific 'why' sentence to be present for a " +
            "POSITIVE observation; its absence leads to this point at the MINOR severity level.";

        for (String s : List.of(leaked1, leaked2, leaked3, leaked4)) {
            String clean = DeliveryComposer.sanitizeStudentText(s);
            assertThat(clean).doesNotContainIgnoringCase("NEGATIVE observation");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE observation");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE observation");
            assertThat(clean).doesNotContainIgnoringCase("severity band");
            assertThat(clean).doesNotContainIgnoringCase("severity level");
            assertThat(clean).doesNotContainIgnoringCase("MINOR severity");
            assertThat(clean).doesNotContainIgnoringCase("the practice requires");
            assertThat(clean).doesNotContainIgnoringCase("line threshold");
        }
        assertThat(DeliveryComposer.sanitizeStudentText(leaked4)).contains("only lists what was done");

        String banded =
            "Your change is fine. The 201-400 range is the acceptable upper band, so this lands in the INFO bucket.";
        String cleanBanded = DeliveryComposer.sanitizeStudentText(banded);
        assertThat(cleanBanded).doesNotContainIgnoringCase("upper band");
        assertThat(cleanBanded).doesNotContainIgnoringCase("INFO bucket");
        assertThat(cleanBanded).contains("Your change is fine.");

        String secretGuidance = "Move the hardcoded secret/credential out of source into the environment.";
        assertThat(DeliveryComposer.sanitizeStudentText(secretGuidance)).isEqualTo(secretGuidance);
    }

    @Test
    void sanitizeStudentText_stripsRubricComputationLeaks() {
        String leak =
            "This change is large. enriched=true. Metadata: A=4094, D=326, A+D=4420, F=28. " +
            "Raw bucket: 4420 > 800 -> MAJOR; also 28 > 20 -> MAJOR. Generated/vendored check: none. " +
            "DEFECT-DETECTOR: only NEGATIVE or NOT_APPLICABLE. Consider splitting the change.";
        String clean = DeliveryComposer.sanitizeStudentText(leak);
        assertThat(clean).doesNotContain("Raw bucket");
        assertThat(clean).doesNotContain("-> MAJOR");
        assertThat(clean).doesNotContain("A+D=4420");
        assertThat(clean).doesNotContain("DEFECT-DETECTOR");
        assertThat(clean).doesNotContain("enriched=true");
        assertThat(clean).doesNotContain("Generated/vendored check");
        assertThat(clean).contains("This change is large.");
        assertThat(clean).contains("Consider splitting the change.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gptOssScoringMachineryLeaks")
    void sanitizeStudentText_stripsGptOssScoringMachineryLeak(
        String leakClass,
        String input,
        List<String> mustDrop,
        String mustKeep
    ) {
        String clean = DeliveryComposer.sanitizeStudentText(input);
        assertThat(clean).doesNotContain(mustDrop.toArray(String[]::new));
        if (mustKeep != null) {
            assertThat(clean).contains(mustKeep);
        }
    }

    static Stream<Arguments> gptOssScoringMachineryLeaks() {
        return Stream.of(
            Arguments.of(
                "noise-fraction + band word",
                "Two of the fourteen commit subjects are generic. The noise fraction (2/14 ≈ 0.14) is ≤ 0.25, so the severity is INFO. Prefer specific, imperative commit subjects.",
                List.of("noise fraction", "≤ 0.25", "severity is INFO"),
                "Prefer specific, imperative commit subjects."
            ),
            Arguments.of(
                "raw draft/WIP field names",
                "The PR is marked as ready (is_draft false, no WIP token), satisfying the traceability requirement.",
                List.of("is_draft", "WIP token", "satisfying the traceability requirement"),
                null
            ),
            Arguments.of(
                "the-practice-flags meta-voice",
                "Debug prints were left in the code. The practice flags such leftover scaffolding as a blemish. Remove them before merging.",
                List.of("The practice flags"),
                "Remove them before merging."
            ),
            Arguments.of(
                "parenthesised scoring counters",
                "Metadata lists 13 non-merge commits (T = 13). Three commit subjects combine distinct concerns with \"and\", giving K = 3. Separate each logical change into its own commit.",
                List.of("T = 13", "K = 3"),
                "Separate each logical change into its own commit."
            ),
            Arguments.of(
                "adr-0022 presence/assessment 'is' phrasing",
                "The PR body omits any rationale. The presence is ABSENT so the assessment is BAD. Add a short Why section.",
                List.of("presence is ABSENT", "assessment is BAD"),
                "Add a short Why section."
            ),
            Arguments.of(
                "adr-0022 presence/assessment tuple",
                "The error path is swallowed silently. This lands as (PRESENT, BAD) in the rubric. Rethrow or log the failure so it is visible.",
                List.of("(PRESENT, BAD)"),
                "Rethrow or log the failure so it is visible."
            ),
            Arguments.of(
                "adr-0022 assessment band-routing arrow",
                "No tests accompany the change. presence ABSENT -> BAD per the criteria. Add a test that exercises the new branch.",
                List.of("-> BAD"),
                "Add a test that exercises the new branch."
            )
        );
    }

    @Test
    void sanitizeStudentText_stripsCrossPracticeOrchestrationLeaks() {
        String leak =
            "test_presence.json reports zero test files. This is the sole owner (cross-practice) of this " +
            "lesson: ready-and-traceable-handoff suppressed its DoD-honesty contradiction, and " +
            "ships-tests-with-the-change emitted NOT_APPLICABLE, both deferring here. With zero test files, " +
            "any Definition-of-Done claim that all tests pass is vacuous. This is a team-wide standing nudge, " +
            "never a per-MR blocker.";
        String clean = DeliveryComposer.sanitizeStudentText(leak);
        assertThat(clean).doesNotContain("cross-practice");
        assertThat(clean).doesNotContain("sole owner");
        assertThat(clean).doesNotContain("deferring here");
        assertThat(clean).doesNotContain("suppressed its");
        assertThat(clean).doesNotContain("emitted NOT_APPLICABLE");
        assertThat(clean).doesNotContain("standing nudge");
        assertThat(clean).doesNotContain("per-MR blocker");
        assertThat(clean).contains("zero test files");
        assertThat(clean).contains("Definition-of-Done claim that all tests pass is vacuous");
    }

    @Test
    void sanitizeStudentText_stripsObservationJustificationLeaks() {
        String leak =
            "The body describes what changed but omits the why. Since the change touches only one file, the " +
            "combined observation is NOT_OBSERVED at MAJOR. Per the umbrella calibration this is MINOR " +
            "(a decomposition nudge), not MAJOR. Even a fully absent rationale would be capped at MINOR here. " +
            "No sentence uses a reason connective such as 'so that', 'because', or 'to avoid'. Add a short " +
            "Why section that states the problem this change solves.";
        String clean = DeliveryComposer.sanitizeStudentText(leak);
        assertThat(clean).doesNotContain("observation is NOT_OBSERVED");
        assertThat(clean).doesNotContain("umbrella calibration");
        assertThat(clean).doesNotContain("not MAJOR");
        assertThat(clean).doesNotContain("capped at MINOR");
        assertThat(clean).doesNotContain("reason connective");
        assertThat(clean).contains("describes what changed but omits the why");
        assertThat(clean).contains("Add a short Why section");
    }

    @Test
    void sanitizeStudentText_preservesMarkdownListAndHeadingNewlines() {
        String guidance =
            "Add an acceptance-criteria section, for example:\n\n" +
            "### Acceptance Criteria\n" +
            "- The workspace lists all capture sessions.\n" +
            "- Users can create, rename, and delete sessions.\n" +
            "- Sessions can be searched and filtered.\n\n" +
            "These criteria give a clear definition of done.";
        String clean = DeliveryComposer.sanitizeStudentText(guidance);
        assertThat(clean).contains("\n- The workspace lists all capture sessions.");
        assertThat(clean).contains("\n- Users can create, rename, and delete sessions.");
        assertThat(clean).contains("\n- Sessions can be searched and filtered.");
        assertThat(clean).contains("\n### Acceptance Criteria");
        assertThat(clean).doesNotContain("sessions. - Users");
        assertThat(clean).doesNotContain("\n\n\n");
    }

    @Test
    void sanitizeStudentText_repairsLeakedJsonEnvelopeCorruption() {
        String corrupt =
            "Add a single sentence under ## Description that states the motivation — for example: " +
            "\"## Why\nAdd a sentence: the problem this change solves, e.g. 'so the user knows to adjust " +
            "camera position for optimal scan quality'\"ws to adjust camera position for optimal scan quality'\"}\"";
        String clean = DeliveryComposer.sanitizeStudentText(corrupt);
        assertThat(clean).doesNotContain("}\"");
        assertThat(clean).doesNotContain("'\"ws");
        assertThat(clean).doesNotContain("scan quality'\"ws to adjust camera position for optimal scan quality");
        assertThat(clean).startsWith("Add a single sentence under ## Description that states the motivation");
        assertThat(clean).contains("so the user knows to adjust camera position for optimal scan quality");
    }

    @Test
    void sanitizeStudentText_leavesLegitimateBraceAndRepeatedPhraseGuidanceUntouched() {
        String jsonExample = "Pin the dependency, e.g. add a lockfile entry: {\"fastlane\": \"2.235.0\"}";
        assertThat(DeliveryComposer.sanitizeStudentText(jsonExample)).isEqualTo(jsonExample);
        String repeatedPhrase =
            "Add a Definition of Done section so the work is verifiable; the Definition of Done lists the " +
            "checkable outcomes.";
        assertThat(DeliveryComposer.sanitizeStudentText(repeatedPhrase)).isEqualTo(repeatedPhrase);
    }

    @Test
    void compose_forIssue_negativeObservationsExpandedInFull_neverDemotedToVanishingDiffNote() {
        ValidatedObservation f = negativeObservation(
            "issue-has-checkable-outcome",
            "Missing checkable outcome",
            Severity.MINOR,
            List.of(new LocationSpec("metadata.json", 2, "scm.issue.core")),
            null,
            "The issue does not state any acceptance criteria a maintainer could verify against."
        );

        DeliveryContent issue = DeliveryComposer.compose(List.of(f), ArtifactKinds.ISSUE);

        assertThat(issue).isNotNull();
        assertThat(issue.mrNote()).contains("acceptance criteria a maintainer could verify against");
        assertThat(issue.diffNotes()).isEmpty();
        assertThat(issue.mrNote()).doesNotContain("metadata.json");
    }

    @Test
    void compose_noIssuesNote_skipsObservationWhoseReasoningScrubsToBlank() {
        ValidatedObservation scrubbed = new ValidatedObservation(
            "issue-has-checkable-outcome",
            "Checkable outcome",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            "The practice requires a checkable outcome for a POSITIVE observation."
        );
        ValidatedObservation real = new ValidatedObservation(
            "issue-scoped-to-single-concern",
            "Single concern",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            "The issue describes one deliverable and stays within that single concern."
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(scrubbed, real), ArtifactKinds.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("one deliverable");
        assertThat(dc.mrNote()).doesNotContain(":** \n");
        assertThat(dc.mrNote()).doesNotContain("Checkable outcome:**\n");
    }

    @Test
    void compose_noIssuesNote_allReasoningScrubbed_fallsBackToNothingToChange() {
        ValidatedObservation scrubbed = new ValidatedObservation(
            "issue-has-checkable-outcome",
            "Checkable outcome",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            "The practice requires a checkable outcome for a POSITIVE observation."
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(scrubbed), ArtifactKinds.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("nothing to change here");
        assertThat(dc.mrNote()).doesNotContain("What I observed");
    }

    @Test
    void compose_acknowledgementCount_reflectsImprovementsNotStrengths() {
        List<ValidatedObservation> observations = List.of(
            positiveObservation("issue-scoped-to-single-concern"),
            negativeObservation(
                "issue-has-checkable-outcome",
                "Missing checkable outcome",
                Severity.MINOR,
                List.of(new LocationSpec("metadata.json", 2, "scm.issue.core")),
                null,
                "No acceptance criteria are stated."
            ),
            negativeObservation(
                "issue-states-an-actionable-problem",
                "Missing actionable problem",
                Severity.MINOR,
                List.of(new LocationSpec("metadata.json", 2, "scm.issue.core")),
                null,
                "The description does not frame a concrete problem."
            )
        );

        DeliveryContent dc = DeliveryComposer.compose(observations, ArtifactKinds.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("a couple of things to tighten:");
        assertThat(dc.mrNote()).doesNotContain("one thing to tighten:");
    }

    @Test
    void compose_allObservationsNotApplicable_returnsNullNoSpuriousAllClear() {
        ValidatedObservation na1 = new ValidatedObservation(
            "issue-scoped-to-single-concern",
            "n/a",
            Presence.NOT_APPLICABLE,
            null,
            Severity.INFO,
            null,
            ""
        );
        ValidatedObservation na2 = new ValidatedObservation(
            "issue-has-checkable-outcome",
            "n/a",
            Presence.NOT_APPLICABLE,
            null,
            Severity.INFO,
            null,
            ""
        );

        assertThat(DeliveryComposer.compose(List.of(na1, na2), ArtifactKinds.ISSUE)).isNull();
        assertThat(DeliveryComposer.compose(List.of(na1, na2), ArtifactKinds.PULL_REQUEST)).isNull();
    }

    @Test
    void compose_metadataObservation_dropsYouWroteEchoEntirely() {
        ValidatedObservation f = negativeObservation(
            "mr-description-quality",
            "PR description lacks clear motivation",
            Severity.MAJOR,
            List.of(new LocationSpec("metadata.json", 2, "scm.pull-request.core")),
            List.of(
                "#39: use Logger and package\", \"body\" : \"#39: use Logger and package ## Description - use logger"
            ),
            "The body does not explain why the change is needed."
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(f), ArtifactKinds.PULL_REQUEST);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).doesNotContain("You wrote:");
        assertThat(dc.mrNote()).doesNotContain("\"body\"");
        assertThat(dc.mrNote()).doesNotContain("\" : \"");
        assertThat(dc.mrNote()).doesNotContain("#39: use Logger");
        assertThat(dc.mrNote()).contains("does not explain why the change is needed");
    }

    @Test
    void compose_repositoryMetadataFileKeepsCodeLocation() {
        ValidatedObservation observation = negativeObservation(
            "code-hygiene",
            "Repository metadata needs validation",
            Severity.MINOR,
            List.of(new LocationSpec("metadata.json", 7)),
            List.of("\"enabled\": true"),
            "The repository configuration is not validated."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(observation), ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.diffNotes())
            .singleElement()
            .satisfies(note -> {
                assertThat(note.filePath()).isEqualTo("metadata.json");
                assertThat(note.startLine()).isEqualTo(7);
            });
    }

    @Test
    void compose_stripsLeadingRepoPrefixFromStudentLocation() {
        var observations = List.of(
            negativeObservation(
                "ships-tests-with-the-change",
                "Production logic without a test",
                Severity.MINOR,
                List.of(new LocationSpec("inputs/sources/scm/repo/client/App/Services/APIClient.swift", 12)),
                null,
                "New logic added without a test."
            )
        );
        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("client/App/Services/APIClient.swift");
        assertThat(dc.mrNote()).doesNotContain("inputs/sources/scm/repo/client/");
    }

    @Test
    void compose_epicIssue_collapsesOverlappingStructureObservations() {
        var observations = List.of(
            negativeObservation(
                "issue-scoped-to-single-concern",
                "Bundles concerns",
                Severity.MAJOR,
                null,
                null,
                "This epic mixes capture and export concerns."
            ),
            negativeObservation(
                "issue-has-checkable-outcome",
                "No checkable outcome",
                Severity.MINOR,
                null,
                null,
                "No acceptance criteria are stated."
            ),
            negativeObservation(
                "breaks-large-work-into-trackable-subtasks",
                "No subtasks",
                Severity.MINOR,
                null,
                null,
                "No subtask checklist exists."
            ),
            negativeObservation(
                "issue-states-an-actionable-problem",
                "Missing beneficiary",
                Severity.MINOR,
                null,
                null,
                "No who/why is stated."
            )
        );
        var dc = DeliveryComposer.compose(observations, ArtifactKinds.ISSUE);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("mixes capture and export"); // scoped lead kept
        assertThat(dc.mrNote()).contains("No who/why is stated"); // states-actionable: distinct, survives
        assertThat(dc.mrNote())
            .as("breaks-large-work is a distinct lesson and must NEVER be deduped away (G3)")
            .contains("No subtask checklist exists");
        assertThat(dc.mrNote())
            .as("the genuine near-duplicate sibling (checkable) is collapsed into the scoped lead")
            .doesNotContain("No acceptance criteria are stated");
    }

    @Test
    void compose_pullRequest_doesNotDedupStructureObservations() {
        var observations = List.of(
            negativeObservation(
                "issue-has-checkable-outcome",
                "x",
                Severity.MINOR,
                List.of(new LocationSpec("a.swift", 1)),
                null,
                "No acceptance criteria are stated."
            ),
            negativeObservation(
                "breaks-large-work-into-trackable-subtasks",
                "y",
                Severity.MINOR,
                List.of(new LocationSpec("b.swift", 1)),
                null,
                "No subtask checklist exists."
            )
        );
        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.diffNotes()).hasSize(2);
        assertThat(dc.diffNotes())
            .extracting(PracticeDetectionResultParser.DiffNote::filePath)
            .containsExactlyInAnyOrder("a.swift", "b.swift");
    }

    @Test
    void compose_coOccurringNoTestsFact_deliveredOnceNotAsTwoMajors() {
        var observations = List.of(
            negativeObservation(
                "ready-and-traceable-handoff",
                "Definition of Done claims all tests pass",
                Severity.MAJOR,
                List.of(new LocationSpec("README.md", 3)),
                null,
                "The DoD checklist ticks 'all tests pass' but no test files changed."
            ),
            negativeObservation(
                "ships-tests-with-the-change",
                "Production logic ships without a test",
                Severity.MAJOR,
                List.of(new LocationSpec("Sources/Calc.swift", 12)),
                null,
                "New logic added with no accompanying test."
            )
        );

        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("1 issue to tighten");
        assertThat(dc.mrNote()).doesNotContain("2 issues");
        assertThat(dc.mrNote() + dc.diffNotes().get(0).body()).contains("Production logic ships without a test");
        assertThat(dc.mrNote()).doesNotContain("Definition of Done claims all tests pass");
        assertThat(dc.diffNotes()).hasSize(1);
    }

    @Test
    void compose_coOccurrencePair_keepsHandoffWhenShipsTestsAbsent() {
        var observations = List.of(
            negativeObservation(
                "ready-and-traceable-handoff",
                "Definition of Done claims all tests pass",
                Severity.MAJOR,
                List.of(new LocationSpec("README.md", 3)),
                null,
                "The DoD checklist ticks 'all tests pass' but no test files changed."
            )
        );

        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("1 issue to tighten");
        assertThat(dc.mrNote() + dc.diffNotes().get(0).body()).contains("Definition of Done claims all tests pass");
    }

    @Test
    void compose_blockingIssue_allowsSingleSubordinateProcessPositive() {
        var observations = List.of(
            positiveObservation("engaging-with-inline-review-comments"),
            negativeObservation(
                "avoids-insecure-defaults-and-over-broad-permissions",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Keys.swift", 1)),
                List.of("let k=\"s\""),
                "Secret exposed."
            )
        );
        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).doesNotContain("Nice work");
        assertThat(dc.mrNote()).contains("Worth keeping: you're engaging with the review feedback.");
        assertThat(dc.mrNote().indexOf("to tighten")).isLessThan(dc.mrNote().indexOf("Worth keeping"));
        assertThat(dc.mrNote()).doesNotContain("before merging");
    }

    @Test
    void compose_blockingIssue_acknowledgesAnyHighConfidenceStrengthNotOnlyProcessActs() {
        var strength = new ValidatedObservation(
            "handles-errors-instead-of-swallowing-them",
            "Errors are surfaced (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            null
        );
        var observations = List.of(
            strength,
            negativeObservation(
                "avoids-insecure-defaults-and-over-broad-permissions",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Keys.swift", 1)),
                List.of("let k=\"s\""),
                "Secret exposed."
            )
        );
        var dc = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).doesNotContain("Nice work");
        assertThat(dc.mrNote()).contains("Worth keeping:");
        assertThat(dc.mrNote()).doesNotContain("handles errors instead of swallowing them");
        assertThat(dc.mrNote().split("Worth keeping:", -1)).hasSize(2);
    }

    private ValidatedObservation negativeObservation(String slug, String title, Severity severity) {
        return new ValidatedObservation(
            slug,
            title,
            Presence.ABSENT,
            Assessment.BAD,
            severity,
            buildEvidence(List.of(new LocationSpec(slug + ".swift", 10)), null),
            title + " reasoning."
        );
    }

    @Test
    void compose_capsMinorTail_keepsThreeAndDisclosesOverflowHonestly() {
        List<ValidatedObservation> observations = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            observations.add(negativeObservation("nudge-" + i, "Minor nudge " + i, Severity.MINOR));
        }

        DeliveryContent result = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("3 suggestions for improvement (+3 more minor suggestions):");
        assertThat(result.diffNotes()).hasSize(3);
    }

    @Test
    void compose_neverCapsBlocking_evenWithManyBlockers() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(negativeObservation("sec-1", "Secret 1", Severity.CRITICAL));
        observations.add(negativeObservation("sec-2", "Secret 2", Severity.CRITICAL));
        observations.add(negativeObservation("crash-1", "Crash 1", Severity.MAJOR));
        observations.add(negativeObservation("crash-2", "Crash 2", Severity.MAJOR));
        observations.add(negativeObservation("crash-3", "Crash 3", Severity.MAJOR));
        for (int i = 1; i <= 4; i++) {
            observations.add(negativeObservation("minor-" + i, "Minor " + i, Severity.MINOR));
        }

        DeliveryContent result = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains(
            "5 issues to tighten, plus 3 suggestions for improvement (+1 more minor suggestion):"
        );
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(result.diffNotes()).hasSize(8);
    }

    @Test
    void compose_underTheCap_noOverflowTail() {
        List<ValidatedObservation> observations = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            observations.add(negativeObservation("nudge-" + i, "Minor nudge " + i, Severity.MINOR));
        }

        DeliveryContent result = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("3 suggestions for improvement:");
        assertThat(result.mrNote()).doesNotContain("more minor suggestion");
        assertThat(result.diffNotes()).hasSize(3);
    }

    @Test
    void compose_equalBreadthUsesStableIdentityToChooseNudges() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(negativeObservation("z-low", "Z nudge", Severity.MINOR));
        observations.add(negativeObservation("a-high", "A nudge", Severity.MINOR));
        observations.add(negativeObservation("b-high", "B nudge", Severity.MINOR));
        observations.add(negativeObservation("c-high", "C nudge", Severity.MINOR));

        DeliveryContent result = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("3 suggestions for improvement (+1 more minor suggestion):");
        assertThat(mrNote).contains("A nudge", "B nudge", "C nudge").doesNotContain("Z nudge");
    }

    @Test
    void compose_keepsMinorOverInfo_infoIsTheFirstToCollapse() {
        List<ValidatedObservation> observations = new ArrayList<>();
        observations.add(negativeObservation("minor-1", "Minor one", Severity.MINOR));
        observations.add(negativeObservation("minor-2", "Minor two", Severity.MINOR));
        observations.add(negativeObservation("minor-3", "Minor three", Severity.MINOR));
        observations.add(negativeObservation("info-1", "Info one", Severity.INFO));
        observations.add(negativeObservation("info-2", "Info two", Severity.INFO));

        DeliveryContent result = DeliveryComposer.compose(observations, ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("3 suggestions for improvement (+2 more minor suggestions):");
        assertThat(mrNote).contains("Minor one");
        assertThat(mrNote).contains("Minor two");
        assertThat(mrNote).contains("Minor three");
        assertThat(mrNote).doesNotContain("Info one");
        assertThat(mrNote).doesNotContain("Info two");
    }

    @Test
    void undesirablePracticeObservedObservationIsTreatedAsAProblem() {
        JsonNode evidence = buildEvidence(
            List.of(new LocationSpec("Views/StockView.swift", 42)),
            List.of("let u = URL(s)!")
        );
        ValidatedObservation asProblemObservation = new ValidatedObservation(
            "uses-force-unwrap",
            "Force-unwrap present in changed code",
            Presence.PRESENT,
            Assessment.BAD,
            Severity.MAJOR,
            evidence,
            "Force-unwrapping crashes on nil."
        );
        ValidatedObservation asStrengthObservation = new ValidatedObservation(
            "uses-force-unwrap",
            "Force-unwrap present in changed code",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.MAJOR,
            evidence,
            "Force-unwrapping crashes on nil."
        );

        DeliveryContent asProblem = DeliveryComposer.compose(List.of(asProblemObservation), ArtifactKinds.PULL_REQUEST);
        DeliveryContent asStrength = DeliveryComposer.compose(
            List.of(asStrengthObservation),
            ArtifactKinds.PULL_REQUEST
        );

        assertThat(asProblem).isNotNull();
        assertThat(asProblem.diffNotes()).as("(PRESENT, BAD) is a problem → inline diff note").isNotEmpty();
        assertThat(asProblem.mrNote()).contains("Force-unwrap present in changed code");

        assertThat(asStrength).isNotNull();
        assertThat(asStrength.diffNotes()).as("(PRESENT, GOOD) is a strength → no problem diff note").isEmpty();
    }

    @Test
    void stripsGraderMechanicsLeakFromStudentNote() {
        String leakyReasoning = String.join(
            " ",
            "The 28-file spread means a reviewer cannot review this as a single coherent change.",
            "Per the fixed bucketing: >20 files → MAJOR, nowhere near the 70% threshold for downgrade.",
            "This triggers the largeness gate (signal ii — >=3 distinct parts in prose), so this is a non-epic body.",
            "Combined severity is MAJOR (the most severe sub-result).",
            "This matches the significance catalogue entry 'AUTH / SECURITY MECHANISM'.",
            "But diff_stat.txt lists 28 files and diff_summary.md shows 28 changed files — a material disagreement, so the diff is trusted.",
            "After scanning metadata.body, no sub_issues_total rollup is present (sub_issues_total is null)."
        );
        ValidatedObservation leaky = negativeObservation(
            "scope-one-reviewable-change",
            "28 files spread degrades review effectiveness",
            Severity.MAJOR,
            List.of(new LocationSpec("Views/Foo.swift", 10)),
            List.of("x"),
            leakyReasoning
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(leaky), ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String note =
            result.mrNote() + "\n" + result.diffNotes().stream().map(DiffNote::body).collect(Collectors.joining("\n"));
        assertThat(note).contains("28 files spread degrades review effectiveness");
        assertThat(note).contains("reviewer cannot review this as a single coherent change");
        for (String leak : new String[] {
            "Per the fixed bucketing",
            "→ MAJOR",
            "70% threshold",
            "largeness gate",
            "signal ii",
            "non-epic body",
            "Combined severity",
            "most severe sub-result",
            "significance catalogue",
            "diff_stat.txt",
            "diff_summary.md",
            "material disagreement",
            "so the diff is trusted",
            "After scanning",
            "metadata.body",
            "sub_issues_total",
            "rollup",
        }) {
            assertThat(note).as("leak token must be scrubbed: %s", leak).doesNotContain(leak);
        }
    }

    @Test
    void suppressesYouWroteQuoteWhenEvidenceCarriesGraderMechanics() {
        ValidatedObservation f = negativeObservation(
            "mr-description-quality",
            "PR body lacks a quotable WHY",
            Severity.MAJOR,
            List.of(), // no code location → metadata "You wrote: “…”" path
            List.of(
                "diff_stat.txt lists 28 changed files — metadata.changed_files=14 is stale (material disagreement); trusting the diff"
            ),
            "The body enumerates what changed but never states why."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(f), ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        String note = result.mrNote();
        assertThat(note).doesNotContain("You wrote:");
        assertThat(note).doesNotContain("diff_stat.txt");
        assertThat(note).doesNotContain("material disagreement");
        assertThat(note).contains("PR body lacks a quotable WHY");
        assertThat(note).contains("enumerates what changed but never states why");
    }

    @Test
    void compose_synthesizedDiffNote_carriesObservationFingerprint() {
        ValidatedObservation stamped = negativeObservation(
            "code-hygiene",
            "Dead code in view",
            Severity.MINOR,
            List.of(new LocationSpec("Views/DashboardView.swift", 15)),
            null,
            "Commented-out code adds noise."
        ).withKeys(new ObservationKeys("occ-corr-synth-123", "corr-synth-123"));

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).recurrenceKey()).isEqualTo("corr-synth-123");
    }

    @Test
    void recomposeMrNote_demotesDeliveredInlineObservationToPointer_keepsFullLineForUndelivered() {
        ValidatedObservation delivered = negativeObservation(
            "code-hygiene",
            "Dead code in view",
            Severity.MINOR,
            List.of(new LocationSpec("Views/DashboardView.swift", 15)),
            null,
            "Commented-out code adds noise."
        ).withKeys(new ObservationKeys("occ-corr-delivered", "corr-delivered"));
        ValidatedObservation failed = negativeObservation(
            "meaningful-naming",
            "Non-descriptive name 'Data'",
            Severity.MINOR,
            List.of(new LocationSpec("Models/Data.swift", 8)),
            null,
            "Rename to a domain term."
        ).withKeys(new ObservationKeys("occ-corr-failed", "corr-failed"));

        List<ValidatedObservation> observations = List.of(delivered, failed);

        String firstPass = DeliveryComposer.recomposeMrNote(
            observations,
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of()
        );
        assertThat(firstPass).contains("Dead code in view").contains("Non-descriptive name 'Data'");
        assertThat(firstPass).doesNotContain("see the");

        String demoted = DeliveryComposer.recomposeMrNote(
            observations,
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of("corr-delivered")
        );

        assertThat(demoted).doesNotContain("Dead code in view");
        assertThat(demoted).contains("**Inline comments on the diff:** see the 1 inline comment below.");
        assertThat(demoted).contains("Non-descriptive name 'Data'");
        assertThat(demoted).contains("Models/Data.swift:8");
    }

    @Test
    void recomposeMrNote_allInlineDelivered_collapsesWholeListToPointer() {
        ValidatedObservation a = negativeObservation(
            "code-hygiene",
            "Dead code A",
            Severity.MINOR,
            List.of(new LocationSpec("A.swift", 1)),
            null,
            "Noise."
        ).withKeys(new ObservationKeys("occ-k-a", "k-a"));
        ValidatedObservation b = negativeObservation(
            "code-hygiene",
            "Dead code B",
            Severity.MINOR,
            List.of(new LocationSpec("B.swift", 2)),
            null,
            "Noise."
        ).withKeys(new ObservationKeys("occ-k-b", "k-b"));

        String demoted = DeliveryComposer.recomposeMrNote(
            List.of(a, b),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of("k-a", "k-b")
        );

        assertThat(demoted).contains("**Inline comments on the diff:** see the 2 inline comments below.");
        assertThat(demoted).doesNotContain("Dead code A");
        assertThat(demoted).doesNotContain("Dead code B");
    }

    @Test
    void recomposeMrNote_keylessObservationNeverDemoted_evenWithMatchingEmptyKey() {
        ValidatedObservation keyless = negativeObservation(
            "code-hygiene",
            "Keyless dead code",
            Severity.MINOR,
            List.of(new LocationSpec("Z.swift", 3)),
            null,
            "Noise."
        );

        String demoted = DeliveryComposer.recomposeMrNote(
            List.of(keyless),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of("some-other-key")
        );

        assertThat(demoted).contains("Keyless dead code");
        assertThat(demoted).doesNotContain("see the");
    }

    @Test
    void compose_synthesizedDiffNote_anchorPathIsRepoRelativised() {
        var f = negativeObservation(
            "code-hygiene",
            "Unused import",
            Severity.MINOR,
            List.of(new LocationSpec("inputs/sources/scm/repo/src/components/Button.tsx", 1)),
            null,
            "Remove unused imports."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(f));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("src/components/Button.tsx");
    }

    private static final String SCOPE_WHY =
        "A reviewer can only hold so much in their head at once; a focused change gets read carefully.";

    @Test
    void compose_withWhyBySlug_surfacesTransferablePrincipleOnCritique() {
        var f = negativeObservation(
            "scope-one-reviewable-change",
            "Change spans many unrelated concerns",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "This MR touches authentication, UI, and the build config in one diff."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(f),
            ArtifactKinds.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        );

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("_Why this matters:_ " + SCOPE_WHY);
    }

    @Test
    void compose_withWhyBySlug_emptyMapIsBehaviourIdentical() {
        var f = negativeObservation(
            "scope-one-reviewable-change",
            "Change spans many unrelated concerns",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Touches three concerns."
        );

        String withoutMap = DeliveryComposer.compose(List.of(f), ArtifactKinds.PULL_REQUEST).mrNote();
        String withEmptyMap = DeliveryComposer.compose(List.of(f), ArtifactKinds.PULL_REQUEST, Map.of()).mrNote();

        assertThat(withEmptyMap).isEqualTo(withoutMap);
        assertThat(withEmptyMap).doesNotContain("Why this matters");
    }

    @Test
    void compose_withWhyBySlug_surfacesPrincipleOncePerDelivery() {
        var a = negativeObservation(
            "scope-one-reviewable-change",
            "Concern A bundled in",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Bundles concern A."
        );
        var b = negativeObservation(
            "scope-one-reviewable-change",
            "Concern B bundled in",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Bundles concern B."
        );

        String note = DeliveryComposer.compose(
            List.of(a, b),
            ArtifactKinds.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).containsOnlyOnce("_Why this matters:_");
    }

    @Test
    void compose_withWhyBySlug_skipsPrincipleOnInfoNudge() {
        var info = negativeObservation(
            "leaves-the-code-clean-with-intent-revealing-comments",
            "A stray TODO remains",
            Severity.INFO,
            List.of(),
            List.of(),
            "One leftover TODO."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(info),
            ArtifactKinds.PULL_REQUEST,
            Map.of(
                "leaves-the-code-clean-with-intent-revealing-comments",
                "Intent-revealing code lowers the next reader's cost."
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).doesNotContain("Why this matters");
    }

    @Test
    void compose_withWhyBySlug_atMostOneAdvisoryPrincipleAcrossDelivery() {
        var a = negativeObservation(
            "describe-what-and-why",
            "Thin description",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Body is thin."
        );
        var b = negativeObservation(
            "scope-one-reviewable-change",
            "PR is large",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Touches many files."
        );

        String note = DeliveryComposer.compose(
            List.of(a, b),
            ArtifactKinds.PULL_REQUEST,
            Map.of(
                "describe-what-and-why",
                "A clear description lets a reviewer orient before reading the diff.",
                "scope-one-reviewable-change",
                SCOPE_WHY
            )
        ).mrNote();

        assertThat(note).containsOnlyOnce("_Why this matters:_");
    }

    @Test
    void compose_withWhyBySlug_blockingKeepsPrinciplePlusOneAdvisory() {
        var blocking = negativeObservation(
            "handles-errors-instead-of-swallowing-them",
            "Swallowed error",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Error is dropped."
        );
        var advisory = negativeObservation(
            "describe-what-and-why",
            "Thin description",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Body is thin."
        );

        String note = DeliveryComposer.compose(
            List.of(blocking, advisory),
            ArtifactKinds.PULL_REQUEST,
            Map.of(
                "handles-errors-instead-of-swallowing-them",
                "A swallowed error turns a loud failure into a silent one nobody can debug.",
                "describe-what-and-why",
                "A clear description lets a reviewer orient before reading the diff."
            )
        ).mrNote();

        assertThat(note.split("_Why this matters:_", -1)).hasSize(3); // 2 occurrences => 3 split parts
    }

    private ValidatedObservation positiveWithReasoning(String slug, String reasoning) {
        return new ValidatedObservation(
            slug,
            humanizeTitle(slug) + " (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            reasoning
        );
    }

    @Test
    void compose_allGoodPath_rendersTransferablePrinciple() {
        var observed = List.of(
            positiveWithReasoning("scope-one-reviewable-change", "The change stays focused on a single concern.")
        );

        String note = DeliveryComposer.compose(
            observed,
            ArtifactKinds.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).contains("What's working well here");
        assertThat(note).contains("_Why this matters:_ " + SCOPE_WHY);
    }

    @Test
    void compose_allGoodPath_principleRenderedAtMostOnce() {
        var observed = List.of(
            positiveWithReasoning("scope-one-reviewable-change", "Focused on one concern."),
            positiveWithReasoning("scope-one-reviewable-change", "Each commit is scoped.")
        );

        String note = DeliveryComposer.compose(
            observed,
            ArtifactKinds.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).containsOnlyOnce("_Why this matters:_");
    }

    @Test
    void compose_allGoodPath_noPrincipleWhenNoneAuthored() {
        var observed = List.of(positiveWithReasoning("scope-one-reviewable-change", "The change stays focused."));

        String note = DeliveryComposer.compose(observed, ArtifactKinds.PULL_REQUEST, Map.of()).mrNote();

        assertThat(note).contains("What's working well here");
        assertThat(note).doesNotContain("_Why this matters:_");
    }

    private static final String REAL_DIFF =
        "diff --git a/Sources/Capture/DepthData.swift b/Sources/Capture/DepthData.swift\n" +
        "--- a/Sources/Capture/DepthData.swift\n" +
        "+++ b/Sources/Capture/DepthData.swift\n" +
        "@@ -10,3 +10,4 @@\n" +
        " struct DepthData {\n" +
        "+    let confidence: Float\n" +
        " }\n";

    @Test
    void groundingGuard_hallucinatedPath_anchorDropped_observationStillDelivers() {
        ValidatedObservation hallucinated = negativeObservation(
            "code-hygiene",
            "Dead code",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Ghost/FrameRecorder.swift", 76)),
            List.of("let x = 0"),
            "There is dead code here."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(hallucinated),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote()).contains("Dead code");
    }

    @Test
    void groundingGuard_realPathAndSnippet_anchorKept() {
        ValidatedObservation grounded = negativeObservation(
            "code-hygiene",
            "Missing doc on new field",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Capture/DepthData.swift", 11)),
            List.of("let confidence: Float"),
            "The new field is undocumented."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(grounded),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("Sources/Capture/DepthData.swift");
    }

    @Test
    void groundingGuard_realPathButSnippetNotInHunk_anchorDropped() {
        ValidatedObservation fabricatedSnippet = negativeObservation(
            "code-hygiene",
            "Phantom evidence",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Capture/DepthData.swift", 11)),
            List.of("deleteEverything() // never written"),
            "This line is a problem."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(fabricatedSnippet),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty(); // ungrounded snippet ⇒ no inline anchor
        assertThat(result.mrNote()).contains("Phantom evidence"); // observation still delivered in summary
    }

    @Test
    void groundingGuard_issueArtifact_forcesNoFileLocus() {
        ValidatedObservation issueObservation = negativeObservation(
            "issue-states-an-actionable-problem",
            "Vague problem statement",
            Severity.MINOR,
            List.of(new LocationSpec("metadata.json", 1, "scm.issue.core")),
            List.of("\"title\": \"do stuff\""),
            "The issue does not state a concrete problem."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(issueObservation),
            ArtifactKinds.ISSUE,
            Map.of(),
            null // issues have no diff; force-no-locus still applies via the ISSUE branch
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote()).contains("Vague problem statement");
    }

    @Test
    void groundingGuard_noDiffSupplied_isNoOp_anchorKept() {
        ValidatedObservation observation = negativeObservation(
            "code-hygiene",
            "Some inline issue",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Whatever.swift", 5)),
            List.of("anything"),
            "An inline issue."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(observation),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            (String) null
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1); // no-op guard ⇒ anchor kept
    }

    @Test
    void compose_withheld_reportsCappedImprovementTailAsPolicyFloorDrop() {
        List<ValidatedObservation> observations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            observations.add(
                negativeObservation(
                    "nudge-" + i,
                    "Nudge " + i,
                    Severity.MINOR,
                    List.of(new LocationSpec("Views/N" + i + ".swift", 10 + i)),
                    null,
                    "Reasoning " + i + "."
                ).withKeys(new ObservationKeys("occ-" + i, "rk-" + i))
            );
        }

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        assertThat(result.withheld())
            .hasSize(2)
            .allSatisfy(w -> assertThat(w.reason()).isEqualTo(FeedbackSuppressionReason.VOLUME_CAPPED));
        Map<String, String> titleByKey = observations
            .stream()
            .collect(Collectors.toMap(ValidatedObservation::occurrenceKey, ValidatedObservation::summary));
        for (WithheldObservation w : result.withheld()) {
            assertThat(result.mrNote()).doesNotContain(titleByKey.get(w.occurrenceKey()));
        }
    }

    @Test
    void compose_withheld_addressesOneObservation_notTheWholeLocus() {
        List<ValidatedObservation> sameLocus = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sameLocus.add(
                negativeObservation(
                    "ships-tests-with-the-change",
                    "Gap " + i,
                    Severity.MINOR,
                    List.of(new LocationSpec("src/Foo.java", 10)),
                    null,
                    "A gap."
                ).withKeys(new ObservationKeys("occ-" + i, "shared-locus"))
            );
        }

        List<WithheldObservation> withheld = DeliveryComposer.compose(sameLocus).withheld();

        assertThat(withheld).hasSize(1);
        assertThat(withheld.get(0).occurrenceKey()).startsWith("occ-").isNotEqualTo("shared-locus");
    }

    @Test
    void compose_withheld_reportsCoOccurrenceDedupAsComposerDeduped() {
        ValidatedObservation redundant = negativeObservation(
            "ready-and-traceable-handoff",
            "DoD checkbox claims tests pass",
            Severity.MAJOR,
            List.of(),
            null,
            "The DoD claims all tests pass but no tests changed."
        ).withKeys(new ObservationKeys("occ-rk-redundant", "rk-redundant"));
        ValidatedObservation preferred = negativeObservation(
            "ships-tests-with-the-change",
            "No tests shipped with the change",
            Severity.MAJOR,
            List.of(new LocationSpec("Sources/Feature.swift", 12)),
            null,
            "The change adds behaviour without tests."
        ).withKeys(new ObservationKeys("occ-rk-preferred", "rk-preferred"));

        DeliveryContent result = DeliveryComposer.compose(List.of(redundant, preferred));

        assertThat(result).isNotNull();
        assertThat(result.withheld()).containsExactly(
            new WithheldObservation("occ-rk-redundant", FeedbackSuppressionReason.COMPOSER_DEDUPED)
        );
    }

    @Test
    void compose_withheld_skipsUnkeyedObservations() {
        List<ValidatedObservation> observations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            observations.add(
                negativeObservation(
                    "nudge-" + i,
                    "Nudge " + i,
                    Severity.MINOR,
                    List.of(new LocationSpec("Views/N" + i + ".swift", 10 + i)),
                    null,
                    "Reasoning " + i + "."
                )
            );
        }

        DeliveryContent result = DeliveryComposer.compose(observations);

        assertThat(result).isNotNull();
        assertThat(result.withheld()).isEmpty();
    }

    @Test
    void compose_withheld_epicStructureDedupOnIssue_reportsComposerDeduped() {
        ValidatedObservation scoped = negativeObservation(
            "issue-scoped-to-single-concern",
            "Issue bundles several concerns",
            Severity.MAJOR,
            List.of(),
            null,
            "The issue mixes several concerns."
        ).withKeys(new ObservationKeys("occ-rk-scoped", "rk-scoped"));
        ValidatedObservation checkable = negativeObservation(
            "issue-has-checkable-outcome",
            "No checkable outcome",
            Severity.MINOR,
            List.of(),
            null,
            "The issue has no checkable outcome."
        ).withKeys(new ObservationKeys("occ-rk-checkable", "rk-checkable"));

        DeliveryContent result = DeliveryComposer.compose(List.of(scoped, checkable), ArtifactKinds.ISSUE);

        assertThat(result).isNotNull();
        assertThat(result.withheld()).containsExactly(
            new WithheldObservation("occ-rk-checkable", FeedbackSuppressionReason.COMPOSER_DEDUPED)
        );
    }

    private static final String COMPOSED_BODY = "Nothing in this change exercises the tax-exempt branch you added.";
    private static final String COMPOSED_NEXT_STEP =
        "Write the assertion that distinguishes the exempt case, then run the suite.";

    private ComposedFeedbackUnit inContextUnit(String slug, String title, String body, String nextStep) {
        return inContextUnit(
            slug,
            title,
            nextStep,
            new ComposedFeedbackUnit.InContextPlacement(
                ComposedFeedbackUnit.InContextPlacement.PlacementKind.DIFF,
                new ComposedFeedbackUnit.ResolvedAnchor("obs-0", 0, "Billing/Invoice.java", "NEW", 42, 42)
            )
        );
    }

    private ComposedFeedbackUnit artifactInContextUnit(String slug, String title, String nextStep) {
        return inContextUnit(
            slug,
            title,
            nextStep,
            new ComposedFeedbackUnit.InContextPlacement(
                ComposedFeedbackUnit.InContextPlacement.PlacementKind.ARTIFACT,
                null
            )
        );
    }

    private ComposedFeedbackUnit inContextUnit(
        String slug,
        String title,
        String nextStep,
        ComposedFeedbackUnit.InContextPlacement placement
    ) {
        return new ComposedFeedbackUnit(
            FeedbackChannel.IN_CONTEXT,
            slug,
            List.of("obs-0"),
            ComposedFeedbackUnit.Action.NEW,
            null,
            null,
            title,
            null,
            nextStep,
            null,
            placement
        );
    }

    @Test
    @DisplayName("an observation whose reasoning scrubs to nothing places no inline note at all")
    void compose_reasoningScrubbedToNothing_placesNoInlineNoteRatherThanABareHeader() {
        ValidatedObservation onlyMeta = negativeObservation(
            "ships-tests-with-the-change",
            "New branch ships without a test",
            Severity.MAJOR,
            List.of(new LocationSpec("Billing/Invoice.java", 42)),
            List.of("if (customer.isTaxExempt()) {"),
            "Per the fixed bucketing: >20 files \u2192 MAJOR, nowhere near the 70% threshold for downgrade."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(onlyMeta), ArtifactKinds.PULL_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote()).contains("New branch ships without a test").contains("Billing/Invoice.java:42");
    }

    @Test
    @DisplayName("a cut that lands inside a fenced block closes the fence it opened")
    void closeDanglingCodeFence_reopensNothingAndClosesAnOpenBlock() {
        assertThat(DeliveryComposer.closeDanglingCodeFence("Body text.\n```java\nint x = 1;")).endsWith("\n```");
        assertThat(DeliveryComposer.closeDanglingCodeFence("Body text.\n```java\nint x = 1;\n```")).doesNotEndWith(
            "```\n```"
        );
        assertThat(DeliveryComposer.closeDanglingCodeFence("Write ``` to open a block.")).isEqualTo(
            "Write ``` to open a block."
        );
    }

    private ValidatedObservation untestedBranchObservation() {
        return negativeObservation(
            "ships-tests-with-the-change",
            "New branch ships without a test",
            Severity.MAJOR,
            List.of(new LocationSpec("Billing/Invoice.java", 42)),
            List.of("if (customer.isTaxExempt()) {"),
            "MEASURED REASONING: the change adds a branch and no test covers it."
        );
    }

    @Test
    void compose_composedInContextUnit_usesServerEvidenceAndTheComposedNextStep() {
        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(inContextUnit("ships-tests-with-the-change", "Untested branch", COMPOSED_BODY, COMPOSED_NEXT_STEP))
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        String note = result.diffNotes().get(0).body();
        assertThat(note).contains(COMPOSED_NEXT_STEP).doesNotContain(COMPOSED_BODY);
        assertThat(note).doesNotContain("MEASURED REASONING").doesNotContain("MEASURED GUIDANCE");
        assertThat(note).contains("Untested branch").doesNotContain("New branch ships without a test");
        assertThat(note).contains("🟠");
        assertThat(result.mrNote()).contains("Untested branch").doesNotContain("New branch ships without a test");
    }

    @Test
    void compose_noComposedUnit_fallsBackToTheMeasurementTimeReasoningAlone() {
        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        String body = result.diffNotes().get(0).body();
        assertThat(body).contains("MEASURED REASONING").contains("New branch ships without a test");
        assertThat(body).doesNotContain("MEASURED GUIDANCE");
    }

    @Test
    void compose_inContextIgnoresLegacyBodyAndUsesTheNextStep() {
        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(
                inContextUnit(
                    "ships-tests-with-the-change",
                    "Untested branch",
                    "The practice requires an assertion for every new branch.",
                    COMPOSED_NEXT_STEP
                )
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        String note = result.diffNotes().get(0).body();
        assertThat(note).contains(COMPOSED_NEXT_STEP);
        assertThat(note).doesNotContain("The practice requires").doesNotContain("MEASURED REASONING");
    }

    @Test
    void compose_inContextNeverRendersTheLegacyBody() {
        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(
                inContextUnit(
                    "ships-tests-with-the-change",
                    "Untested branch",
                    COMPOSED_BODY + " The practice requires an assertion for every new branch.",
                    COMPOSED_NEXT_STEP
                )
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).body())
            .contains(COMPOSED_NEXT_STEP)
            .doesNotContain(COMPOSED_BODY)
            .doesNotContain("The practice requires");
    }

    @Test
    void compose_composedUnitForAPracticeThatCannotBeAnchored_landsInTheSummaryInsteadOfInventingAPlacement() {
        ValidatedObservation f = negativeObservation(
            "describe-what-and-why",
            "Description does not say why",
            Severity.MAJOR,
            List.of(),
            null,
            "MEASURED REASONING: the description lists what changed only."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(f),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(artifactInContextUnit("describe-what-and-why", "Unexplained change", COMPOSED_NEXT_STEP))
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote())
            .contains("Unexplained change")
            .doesNotContain(COMPOSED_BODY)
            .contains(COMPOSED_NEXT_STEP)
            .doesNotContain("MEASURED REASONING");
    }

    @Test
    void compose_twoLociOfOnePractice_rendersItsSingleComposedMessageOnceAtTheMostSevereLocus() {
        ValidatedObservation severe = untestedBranchObservation();
        ValidatedObservation lesser = negativeObservation(
            "ships-tests-with-the-change",
            "Second untested branch",
            Severity.MINOR,
            List.of(new LocationSpec("Billing/Refund.java", 9)),
            List.of("if (order.isRefundable()) {"),
            "SECOND LOCUS REASONING: another branch with no test."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(lesser, severe),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(inContextUnit("ships-tests-with-the-change", "Untested branch", COMPOSED_BODY, COMPOSED_NEXT_STEP))
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(2);
        assertThat(
            result
                .diffNotes()
                .stream()
                .filter(n -> n.body().contains(COMPOSED_NEXT_STEP))
                .count()
        ).isEqualTo(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("Billing/Invoice.java");
        assertThat(result.diffNotes().get(0).body()).contains(COMPOSED_NEXT_STEP).doesNotContain(COMPOSED_BODY);
        assertThat(result.diffNotes().get(1).body()).contains("SECOND LOCUS REASONING");
    }

    @Test
    void compose_withholdUnit_leavesThePracticeOnTodaysRenderingRatherThanSilencingIt() {
        ComposedFeedbackUnit withheld = new ComposedFeedbackUnit(
            FeedbackChannel.IN_CONTEXT,
            "ships-tests-with-the-change",
            List.of("obs-0"),
            ComposedFeedbackUnit.Action.WITHHOLD,
            null,
            ComposedFeedbackUnit.WithholdReason.ALREADY_SAID,
            null,
            null,
            null,
            null,
            null
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(withheld)
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).body()).contains("MEASURED REASONING");
    }

    @Test
    void compose_inAppUnitForTheSamePractice_isNotBorrowedByTheInContextLane() {
        ComposedFeedbackUnit inApp = new ComposedFeedbackUnit(
            FeedbackChannel.IN_APP,
            "ships-tests-with-the-change",
            List.of("prior:ships-tests-with-the-change"),
            ComposedFeedbackUnit.Action.NEW,
            null,
            null,
            "A habit across three changes",
            "IN_APP BODY: this keeps happening.",
            "IN_APP NEXT STEP: write the test first next time.",
            null,
            null
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(untestedBranchObservation()),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(inApp)
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).body()).contains("MEASURED REASONING").doesNotContain("IN_APP BODY");
    }

    @Test
    void compose_composedUnitOnTheAgentsOwnSuggestedAnchor_keepsThePlacementAndTakesTheWords() {
        ValidatedObservation f = new ValidatedObservation(
            "ships-tests-with-the-change",
            "New branch ships without a test",
            Presence.ABSENT,
            Assessment.BAD,
            Severity.MAJOR,
            buildEvidence(
                List.of(new LocationSpec("Billing/Invoice.java", 42)),
                List.of("if (customer.isTaxExempt()) {")
            ),
            "MEASURED REASONING: the change adds a branch and no test covers it."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(f),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(inContextUnit("ships-tests-with-the-change", "Untested branch", COMPOSED_BODY, COMPOSED_NEXT_STEP))
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        DiffNote note = result.diffNotes().get(0);
        assertThat(note.filePath()).isEqualTo("Billing/Invoice.java");
        assertThat(note.startLine()).isEqualTo(42);
        assertThat(note.endLine()).isEqualTo(42);
        assertThat(note.body())
            .doesNotContain(COMPOSED_BODY)
            .contains(COMPOSED_NEXT_STEP)
            .doesNotContain("SUGGESTED NOTE BODY");
    }

    @Test
    void compose_strengthsOnlyRun_prefersTheComposedMessageInTheBullet() {
        ValidatedObservation good = new ValidatedObservation(
            "ships-tests-with-the-change",
            "Tests ship with the change",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            null,
            "MEASURED REASONING: the new branch is covered."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(good),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            null,
            List.of(
                inContextUnit("ships-tests-with-the-change", "Tests landed with it", COMPOSED_BODY, COMPOSED_NEXT_STEP)
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote())
            .doesNotContain(COMPOSED_BODY)
            .contains(COMPOSED_NEXT_STEP)
            .doesNotContain("MEASURED REASONING");
    }

    @Test
    void recomposeMrNote_withTheSameComposedUnits_namesTheSameThingTheFirstPassDid() {
        ValidatedObservation f = untestedBranchObservation().withKeys(new ObservationKeys("occ-1", "rk-1"));
        List<ComposedFeedbackUnit> composed = List.of(
            inContextUnit("ships-tests-with-the-change", "Untested branch", COMPOSED_BODY, COMPOSED_NEXT_STEP)
        );

        String firstPass = DeliveryComposer.recomposeMrNote(
            List.of(f),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of(),
            composed
        );
        String demoted = DeliveryComposer.recomposeMrNote(
            List.of(f),
            ArtifactKinds.PULL_REQUEST,
            Map.of(),
            Set.of("rk-1"),
            composed
        );

        assertThat(firstPass).contains("Untested branch");
        assertThat(demoted).contains("see the 1 inline comment below.").doesNotContain("Untested branch");
    }
}
