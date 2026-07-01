package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    // Evidence builders

    private JsonNode buildEvidence(List<LocationSpec> locations, List<String> snippets) {
        ObjectNode evidence = objectMapper.createObjectNode();
        if (locations != null && !locations.isEmpty()) {
            ArrayNode locArr = evidence.putArray("locations");
            for (LocationSpec loc : locations) {
                ObjectNode locNode = objectMapper.createObjectNode();
                locNode.put("path", loc.path);
                locNode.put("startLine", loc.startLine);
                if (loc.endLine != null) {
                    locNode.put("endLine", loc.endLine);
                }
                locArr.add(locNode);
            }
        }
        if (snippets != null && !snippets.isEmpty()) {
            ArrayNode snipArr = evidence.putArray("snippets");
            for (String s : snippets) {
                snipArr.add(s);
            }
        }
        return evidence;
    }

    private record LocationSpec(String path, int startLine, Integer endLine) {
        LocationSpec(String path, int startLine) {
            this(path, startLine, null);
        }
    }

    // Finding builders

    private ValidatedFinding positiveFinding(String slug) {
        return new ValidatedFinding(
            slug,
            humanizeTitle(slug) + " (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.90f,
            null,
            null,
            null,
            List.of()
        );
    }

    private ValidatedFinding negativeFinding(
        String slug,
        String title,
        Severity severity,
        List<LocationSpec> locations,
        List<String> snippets,
        String reasoning,
        String guidance
    ) {
        return new ValidatedFinding(
            slug,
            title,
            Presence.ABSENT,
            Assessment.BAD,
            severity,
            0.92f,
            buildEvidence(locations, snippets),
            reasoning,
            guidance,
            List.of()
        );
    }

    private static String humanizeTitle(String slug) {
        return slug.replace('-', ' ').substring(0, 1).toUpperCase() + slug.replace('-', ' ').substring(1);
    }

    // Realistic findings used across tests

    private List<ValidatedFinding> mixedFindings() {
        List<ValidatedFinding> findings = new ArrayList<>();

        // 3 positives
        findings.add(positiveFinding("error-state-handling"));
        findings.add(positiveFinding("view-decomposition"));
        findings.add(positiveFinding("meaningful-naming"));

        // CRITICAL: hardcoded-secrets
        findings.add(
            negativeFinding(
                "hardcoded-secrets",
                "Hardcoded API key exposed in source",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/APIKeys.swift", 5)),
                List.of("let apiKey = \"sk-abc123\""),
                "An API key is hardcoded directly in source code. Anyone with repository access can extract this secret and use it to make authenticated API calls on your behalf.",
                "Store secrets in environment variables or a secure keychain. Use a configuration file excluded from version control (e.g., .gitignore) and load the key at runtime:\n```swift\nlet apiKey = ProcessInfo.processInfo.environment[\"API_KEY\"] ?? \"\"\n```"
            )
        );

        // MAJOR: fatal-error-crash
        findings.add(
            negativeFinding(
                "fatal-error-crash",
                "Force-unwrap causes crash on invalid URL",
                Severity.MAJOR,
                List.of(new LocationSpec("Views/StockView.swift", 42)),
                List.of("let url = URL(string: urlString)!"),
                "Force-unwrapping URL(string:) will crash the app if urlString contains invalid characters or is malformed. This is a common cause of App Store rejections.",
                "Use guard-let or if-let to safely unwrap:\n```swift\nguard let url = URL(string: urlString) else { return }\n```"
            )
        );

        // MINOR: code-hygiene
        findings.add(
            negativeFinding(
                "code-hygiene",
                "Commented-out code left in view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/DashboardView.swift", 15)),
                null,
                "Commented-out code adds noise and makes diffs harder to review. Remove dead code and rely on version control history instead.",
                null
            )
        );

        // MINOR: meaningful-naming
        findings.add(
            negativeFinding(
                "meaningful-naming",
                "Non-descriptive type name 'Data'",
                Severity.MINOR,
                List.of(new LocationSpec("Models/Data.swift", 8)),
                null,
                "The type name 'Data' shadows Foundation.Data and conveys no domain meaning. Rename to something descriptive like 'PortfolioSnapshot' or 'StockQuote'.",
                null
            )
        );

        return findings;
    }

    @Test
    void compose_forIssueArtifact_usesNonBlockingTightenCta() {
        // W3: Hephaestus is non-blocking. The CTA is state-neutral feed-forward ("to tighten"), never the
        // gatekeeping "to fix before merging" — and that holds for issues (which are never merged) too.
        DeliveryContent result = DeliveryComposer.compose(
            mixedFindings(),
            de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.ISSUE
        );

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).doesNotContain("to fix");
        assertThat(mrNote).contains("2 suggestions for improvement");
    }

    // Test 1: Mixed findings

    @Test
    void compose_withMixedFindings_producesExpectedMrNote() {
        List<ValidatedFinding> findings = mixedFindings();

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        // Mixed reviews with multiple blocking issues should not front-load a cheerful multi-strength opener.
        assertThat(mrNote).doesNotContain("Nice work");
        // W1: a single earned strength line still lands (the uncurated positives → the generic form), so the
        // student hears one thing to keep doing even under blocking issues — never a person-level grade.
        assertThat(mrNote).contains("Worth keeping:");

        // W3: non-blocking CTA, split blocking vs improvement (2 CRITICAL/MAJOR + 2 MINOR).
        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).contains("2 suggestions for improvement");

        // Severity emojis (no bracket labels — emoji is sufficient)
        assertThat(mrNote).doesNotContain("[CRITICAL]");
        assertThat(mrNote).doesNotContain("[MAJOR]");
        assertThat(mrNote).doesNotContain("[MINOR]");
        // All 4 negatives are inlinable → compact list in MR summary (title + location only)
        assertThat(mrNote).contains("Hardcoded API key exposed in source");
        assertThat(mrNote).contains("Config/APIKeys.swift:5");
        assertThat(mrNote).contains("Force-unwrap causes crash on invalid URL");
        assertThat(mrNote).contains("Views/StockView.swift:42");
        assertThat(mrNote).contains("Commented-out code left in view");
        assertThat(mrNote).contains("Views/DashboardView.swift:15");
        assertThat(mrNote).contains("Non-descriptive type name 'Data'");
        assertThat(mrNote).contains("Models/Data.swift:8");

        // Full detail NOT in MR summary (moved to diff notes for inlinable findings)
        assertThat(mrNote).doesNotContain("You wrote:");
        assertThat(mrNote).doesNotContain("ProcessInfo.processInfo.environment");
        assertThat(mrNote).doesNotContain("guard let url");
        assertThat(mrNote).doesNotContain("hardcoded directly in source code");
        // Diff-note content is asserted by compose_diffNotes_allNegativesGetInlineComments.
    }

    // Test 2: All positive findings produce an approval comment

    @Test
    void compose_withAllPositive_producesObservationNoteWithoutPraise() {
        List<ValidatedFinding> findings = List.of(
            positiveFinding("error-state-handling"),
            positiveFinding("view-decomposition"),
            positiveFinding("meaningful-naming")
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("Reviewed against the active practices");
        // Mentoring stance: feedback stays on the work, never self-level praise.
        assertThat(result.mrNote()).doesNotContain("Nice work").doesNotContain("stood out");
        assertThat(result.diffNotes()).isEmpty();
    }

    @Test
    void compose_withAllPositiveAndReasoning_listsEvidenceAnchoredObservations() {
        ValidatedFinding withReasoning = new ValidatedFinding(
            "error-state-handling",
            "Error state handling (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.95f,
            null,
            "Network errors are surfaced to the user via an alert.",
            null,
            List.of()
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

    // Test 3: Overflow with >5 negatives

    @Test
    void compose_withManyNegatives_allInCompactList() {
        List<ValidatedFinding> findings = new ArrayList<>();

        // 7 negatives: all inlinable (have file locations)
        findings.add(
            negativeFinding(
                "hardcoded-secrets",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/Keys.swift", 1)),
                List.of("let key = \"secret\""),
                "Secret exposed.",
                "Use env vars."
            )
        );
        findings.add(
            negativeFinding(
                "fatal-error-crash",
                "Force unwrap crash",
                Severity.MAJOR,
                List.of(new LocationSpec("Views/A.swift", 10)),
                List.of("url!"),
                "Crash risk.",
                "Use guard."
            )
        );
        findings.add(
            negativeFinding(
                "code-hygiene",
                "Dead code",
                Severity.MINOR,
                List.of(new LocationSpec("Views/B.swift", 20)),
                null,
                "Remove dead code.",
                null
            )
        );
        findings.add(
            negativeFinding(
                "meaningful-naming",
                "Bad name",
                Severity.MINOR,
                List.of(new LocationSpec("Models/C.swift", 30)),
                null,
                "Use descriptive names.",
                null
            )
        );
        findings.add(
            negativeFinding(
                "error-state-handling",
                "Missing error UI",
                Severity.MINOR,
                List.of(new LocationSpec("Views/D.swift", 40)),
                null,
                "Show errors to user.",
                null
            )
        );
        findings.add(
            negativeFinding(
                "view-decomposition",
                "Monolith view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/E.swift", 50)),
                null,
                "Break view into subviews.",
                null
            )
        );
        findings.add(
            negativeFinding(
                "accessibility",
                "Missing labels",
                Severity.INFO,
                List.of(new LocationSpec("Views/F.swift", 60)),
                null,
                "Add accessibility labels.",
                null
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        // 2 blocking ALWAYS kept; the 5 non-blocking (4 MINOR + 1 INFO) tail is capped to 3, with the
        // remaining 2 disclosed honestly as overflow. The INFO and the lowest MINOR are the ones folded.
        assertThat(mrNote).contains("2 issues to tighten");
        assertThat(mrNote).doesNotContain("before merging");
        assertThat(mrNote).contains("3 suggestions for improvement (+2 more minor suggestions):");

        // Severity ordering in compact list: CRITICAL first (🔴), then MAJOR (🟠)
        int criticalIdx = mrNote.indexOf("\uD83D\uDD34");
        int majorIdx = mrNote.indexOf("\uD83D\uDFE0");
        assertThat(criticalIdx).isGreaterThanOrEqualTo(0);
        assertThat(majorIdx).isGreaterThan(criticalIdx);

        // Only the kept findings get diff notes: 2 blocking + 3 improvements = 5 (not the raw 7). A
        // capped/dropped nudge leaves no inline comment behind.
        assertThat(result.diffNotes()).hasSize(5);
        assertThat(mrNote).doesNotContain("Missing labels");
    }

    // Test 4: All negatives get inline diff notes

    @Test
    void compose_diffNotes_allNegativesGetInlineComments() {
        List<ValidatedFinding> findings = mixedFindings();

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        List<DiffNote> diffNotes = result.diffNotes();

        // All 4 negatives should have diff notes
        assertThat(diffNotes).hasSize(4);

        // Verify file paths and line numbers match evidence
        DiffNote secretsNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Config/APIKeys.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(secretsNote.startLine()).isEqualTo(5);
        assertThat(secretsNote.body()).contains("ProcessInfo.processInfo.environment");

        DiffNote crashNote = diffNotes
            .stream()
            .filter(n -> n.filePath().equals("Views/StockView.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(crashNote.startLine()).isEqualTo(42);
        assertThat(crashNote.body()).contains("guard let url");

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

        // Header is the canonical emoji-INSIDE-bold form (**<emoji> <title>**): the body opens with "**"
        // BEFORE the emoji. Pins appendFindingHeader against the emoji sitting outside the bold
        // (`<emoji> **title**`).
        for (DiffNote dn : diffNotes) {
            assertThat(dn.body()).startsWith("**");
        }
    }

    // Edge cases

    @Test
    void compose_withNull_returnsNull() {
        assertThat(DeliveryComposer.compose(null)).isNull();
    }

    @Test
    void compose_withEmptyList_returnsNull() {
        assertThat(DeliveryComposer.compose(List.of())).isNull();
    }

    @Test
    void compose_nonInlinableFindings_renderedInFullInMrNote() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "mr-description-quality",
                "MR description is empty",
                Severity.MAJOR,
                null,
                null,
                "The MR description is empty, making it hard for reviewers to understand the changes.",
                "Describe what this MR does and why."
            ),
            negativeFinding(
                "code-hygiene",
                "Unused import",
                Severity.MINOR,
                List.of(new LocationSpec("src/components/Button.tsx", 1)),
                List.of("import React from 'react';"),
                "Remove unused imports.",
                "Delete the import."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);
        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        // Non-inlinable (mr-description-quality): full detail in MR summary
        assertThat(mrNote).contains("MR description is empty");
        assertThat(mrNote).contains("Describe what this MR does");

        // Inlinable (code-hygiene): compact list entry only
        assertThat(mrNote).contains("Unused import");
        assertThat(mrNote).contains("src/components/Button.tsx:1");
        // Detail NOT in MR summary for inlinable finding
        assertThat(mrNote).doesNotContain("Remove unused imports.");

        // Inline header appears when there are both non-inlinable and inlinable
        assertThat(mrNote).contains("Inline comments on the diff:");

        // Only the inlinable finding gets a diff note
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("src/components/Button.tsx");
    }

    @Test
    void compose_minorFindings_guidanceInDiffNote() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "code-hygiene",
                "Dead code in view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/DashboardView.swift", 15)),
                null,
                "Commented-out code adds noise.",
                "Remove the commented-out block and rely on git history."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);
        assertThat(result).isNotNull();

        // MR note: compact list only (inlinable finding)
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("Dead code in view");
        assertThat(mrNote).contains("Views/DashboardView.swift:15");
        // The compact list is always labelled, even with NO non-inlinable findings — otherwise a clean PR
        // shows an unlabelled wall of duplicated headers right after the count opener.
        assertThat(mrNote).contains("Inline comments on the diff:");

        // Full detail is in the diff note, not MR summary
        assertThat(result.diffNotes()).hasSize(1);
        DiffNote note = result.diffNotes().get(0);
        assertThat(note.body()).contains("Commented-out code adds noise.");
        assertThat(note.body()).contains("Remove the commented-out block");
    }

    @Test
    void compose_withOnlyMinorNegatives_usesImprovementLanguage() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "code-hygiene",
                "Dead code",
                Severity.MINOR,
                List.of(new LocationSpec("Views/X.swift", 10)),
                null,
                "Clean up dead code.",
                null
            ),
            negativeFinding(
                "meaningful-naming",
                "Poor name",
                Severity.INFO,
                List.of(new LocationSpec("Models/Y.swift", 5)),
                null,
                "Use better names.",
                null
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        // No CRITICAL/MAJOR => improvement language, not merge-blocking
        assertThat(mrNote).contains("2 suggestions for improvement");
        assertThat(mrNote).doesNotContain("before merging");
    }

    @Test
    void compose_suggestionsOnlyWithPositives_prependsTaskLevelAcknowledgement() {
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(positiveFinding("scope-one-reviewable-change"));
        findings.add(positiveFinding("ready-and-traceable-handoff"));
        findings.add(
            negativeFinding(
                "describe-what-and-why",
                "PR description lacks a rationale sentence",
                Severity.MINOR,
                List.of(),
                List.of(),
                "The body lists what changed but not why.",
                "Add a sentence explaining the motivation."
            )
        );

        String mrNote = DeliveryComposer.compose(findings).mrNote();

        // Strengths named in task language, then the improvement — never deficit-only.
        assertThat(mrNote).startsWith("Nice work ");
        assertThat(mrNote).contains("keeping the change focused and reviewable");
        assertThat(mrNote).contains("linking the change to its issue");
        assertThat(mrNote).contains("to tighten:");
        assertThat(mrNote).contains("1 suggestion for improvement");
    }

    @Test
    void compose_blockingIssue_suppressesOpenerButStillAcknowledgesOneStrength() {
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(positiveFinding("scope-one-reviewable-change"));
        findings.add(
            negativeFinding(
                "hardcoded-secrets",
                "Hardcoded API key",
                Severity.CRITICAL,
                List.of(new LocationSpec("Config/Keys.swift", 5)),
                List.of("let key = \"abc\""),
                "A secret is committed.",
                "Move it to the environment."
            )
        );

        String mrNote = DeliveryComposer.compose(findings).mrNote();

        // The cheerful multi-strength opener is still suppressed (no hollow feedback sandwich)...
        assertThat(mrNote).doesNotContain("Nice work");
        // ...but W1 still names ONE earned strength, subordinate, after the issue count — task-level only.
        assertThat(mrNote).contains("Worth keeping: you're keeping the change focused and reviewable.");
        assertThat(mrNote.indexOf("to tighten")).isLessThan(mrNote.indexOf("Worth keeping"));
        // W3: non-blocking CTA.
        assertThat(mrNote).doesNotContain("before merging");
    }

    @Test
    void compose_uncuratedPositive_acknowledgesGenericallyNeverDropsSilently() {
        // C2: a real OBSERVED strength whose practice has no curated gerund phrase must still be acknowledged
        // (generically, grammatically) — not silently dropped, and not dumped as a raw ungrammatical slug.
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(positiveFinding("some-uncurated-practice-xyz"));
        findings.add(
            negativeFinding(
                "describe-what-and-why",
                "PR description lacks a rationale sentence",
                Severity.MINOR,
                List.of(),
                List.of(),
                "The body lists what changed but not why.",
                "Add a sentence explaining the motivation."
            )
        );

        String mrNote = DeliveryComposer.compose(findings).mrNote();

        assertThat(mrNote).startsWith("Nice work here");
        // never dump the raw slug into the opener
        assertThat(mrNote).doesNotContain("some uncurated practice xyz");
        assertThat(mrNote).contains("to tighten:");
    }

    @Test
    void sanitizeStudentText_stripsInternalGradingVocabulary() {
        // Grading-mechanics phrasing the model can emit; it must never reach the student.
        String leaked1 = "The body has no rationale, which results in a NEGATIVE finding with MINOR severity.";
        String leaked2 =
            "This exceeds the ≤200 line threshold for a POSITIVE finding, placing it in the INFO severity band.";
        String leaked3 = "The title is generic, violating the practice that requires an imperative summary.";

        // A mid-paragraph leak: two sentences where the second is pure rubric meta.
        String leaked4 =
            "The title is descriptive but the body only lists what was done without a quoted sentence " +
            "that explains why. The practice requires a specific 'why' sentence to be present for a " +
            "POSITIVE observation; its absence leads to this point at the MINOR severity level.";

        for (String s : List.of(leaked1, leaked2, leaked3, leaked4)) {
            String clean = DeliveryComposer.sanitizeStudentText(s);
            assertThat(clean).doesNotContainIgnoringCase("NEGATIVE finding");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE finding");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE observation");
            assertThat(clean).doesNotContainIgnoringCase("severity band");
            assertThat(clean).doesNotContainIgnoringCase("severity level");
            assertThat(clean).doesNotContainIgnoringCase("MINOR severity");
            assertThat(clean).doesNotContainIgnoringCase("the practice requires");
            assertThat(clean).doesNotContainIgnoringCase("line threshold");
        }
        // The useful, non-grading sentence survives the scrub.
        assertThat(DeliveryComposer.sanitizeStudentText(leaked4)).contains("only lists what was done");

        // Bare band/bucket phrasings (no "severity" prefix) the scrubber also catches.
        String banded =
            "Your change is fine. The 201-400 range is the acceptable upper band, so this lands in the INFO bucket.";
        String cleanBanded = DeliveryComposer.sanitizeStudentText(banded);
        assertThat(cleanBanded).doesNotContainIgnoringCase("upper band");
        assertThat(cleanBanded).doesNotContainIgnoringCase("INFO bucket");
        assertThat(cleanBanded).contains("Your change is fine.");

        // Domain vocabulary in legitimate guidance must survive untouched.
        String secretGuidance = "Move the hardcoded secret/credential out of source into the environment.";
        assertThat(DeliveryComposer.sanitizeStudentText(secretGuidance)).isEqualTo(secretGuidance);
    }

    @Test
    void sanitizeStudentText_stripsRubricComputationLeaks() {
        // Regression: the model echoed the criteria's internal bucket maths
        // and preamble tags verbatim into student-facing reasoning. These must never reach the student.
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
        // The legitimate student-facing sentences survive.
        assertThat(clean).contains("This change is large.");
        assertThat(clean).contains("Consider splitting the change.");
    }

    // Scoring sentences the model can emit must never survive into the delivered text: the lesson stays on
    // its title + guidance, while the arithmetic, band words, raw field names, and scoring counters are
    // dropped. One parameter row per leak class so a failure localises.
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
            // ADR-0022 presence/assessment rubric vocabulary the model is now prompted on.
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
        // Regression: the model narrated how findings were ROUTED between
        // practices into student-facing reasoning — the grader talking to itself about ownership/delivery.
        // The whole orchestration sentence must be dropped; the real student feedback around it survives.
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
        // The genuine student-facing sentences survive intact.
        assertThat(clean).contains("zero test files");
        assertThat(clean).contains("Definition-of-Done claim that all tests pass is vacuous");
    }

    @Test
    void sanitizeStudentText_stripsObservationJustificationLeaks() {
        // Regression: the grader's observation/severity JUSTIFICATION leaked verbatim to students — the
        // developer "watches the rubric get scored" instead of hearing a colleague. Severity is carried by
        // the icon; these sentences are pure machinery and must drop, while the real lesson survives.
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
        // The genuine, actionable feedback survives.
        assertThat(clean).contains("describes what changed but omits the why");
        assertThat(clean).contains("Add a short Why section");
    }

    @Test
    void sanitizeStudentText_preservesMarkdownListAndHeadingNewlines() {
        // Regression: a bulleted acceptance-criteria block whose items
        // each end in '.' was collapsed onto one run-on line ("session. - Users can create") because the
        // sentence-split rejoined every sentence with a blank space, eating the list newlines.
        String guidance =
            "Add an acceptance-criteria section, for example:\n\n" +
            "### Acceptance Criteria\n" +
            "- The workspace lists all capture sessions.\n" +
            "- Users can create, rename, and delete sessions.\n" +
            "- Sessions can be searched and filtered.\n\n" +
            "These criteria give a clear definition of done.";
        String clean = DeliveryComposer.sanitizeStudentText(guidance);
        // Every list item stays on its own line (the newline before "- " survives).
        assertThat(clean).contains("\n- The workspace lists all capture sessions.");
        assertThat(clean).contains("\n- Users can create, rename, and delete sessions.");
        assertThat(clean).contains("\n- Sessions can be searched and filtered.");
        // The heading stays on its own line, not folded into the preceding sentence.
        assertThat(clean).contains("\n### Acceptance Criteria");
        // No two list items run together on one line.
        assertThat(clean).doesNotContain("sessions. - Users");
        // Paragraph breaks are preserved (capped at one blank line).
        assertThat(clean).doesNotContain("\n\n\n");
    }

    @Test
    void sanitizeStudentText_repairsLeakedJsonEnvelopeCorruption() {
        // Regression: the model terminated the describe-what-and-why
        // guidance with a leaked serialized-object boundary ('"}") after echoing the final clause, so the
        // student received garbled text ("…optimal scan quality'\"ws to adjust…quality'\"}\"").
        String corrupt =
            "Add a single sentence under ## Description that states the motivation — for example: " +
            "\"## Why\nAdd a sentence: the problem this change solves, e.g. 'so the user knows to adjust " +
            "camera position for optimal scan quality'\"ws to adjust camera position for optimal scan quality'\"}\"";
        String clean = DeliveryComposer.sanitizeStudentText(corrupt);
        // The leaked envelope boundary is gone and the duplicated trailing clause is collapsed.
        assertThat(clean).doesNotContain("}\"");
        assertThat(clean).doesNotContain("'\"ws");
        assertThat(clean).doesNotContain("scan quality'\"ws to adjust camera position for optimal scan quality");
        // The salvageable guidance survives intact up to the corruption point.
        assertThat(clean).startsWith("Add a single sentence under ## Description that states the motivation");
        assertThat(clean).contains("so the user knows to adjust camera position for optimal scan quality");
    }

    @Test
    void sanitizeStudentText_leavesLegitimateBraceAndRepeatedPhraseGuidanceUntouched() {
        // The envelope-corruption repair must be scoped to the leak signature only: guidance that ends in a
        // real JSON/code example (closing AT a brace, no trailing outer quote) or that legitimately repeats a
        // phrase must pass through byte-for-byte.
        String jsonExample = "Pin the dependency, e.g. add a lockfile entry: {\"fastlane\": \"2.235.0\"}";
        assertThat(DeliveryComposer.sanitizeStudentText(jsonExample)).isEqualTo(jsonExample);
        String repeatedPhrase =
            "Add a Definition of Done section so the work is verifiable; the Definition of Done lists the " +
            "checkable outcomes.";
        assertThat(DeliveryComposer.sanitizeStudentText(repeatedPhrase)).isEqualTo(repeatedPhrase);
    }

    @Test
    void compose_forIssue_negativeFindingsExpandedInFull_neverDemotedToVanishingDiffNote() {
        // An issue finding with an ordinary location would be "inlinable" on a PR — but issues carry no
        // diff, so it must be expanded in full in the note, not demoted to a diff note that never posts.
        ValidatedFinding f = negativeFinding(
            "issue-has-checkable-outcome",
            "Missing checkable outcome",
            Severity.MINOR,
            List.of(new LocationSpec("metadata.json", 2)),
            null,
            "The issue does not state any acceptance criteria a maintainer could verify against.",
            "Add a short checklist of done conditions, e.g. a `- [ ]` list of observable outcomes."
        );

        DeliveryContent issue = DeliveryComposer.compose(List.of(f), WorkArtifact.ISSUE);

        assertThat(issue).isNotNull();
        // Full reasoning + guidance reach the student inside the issue note itself.
        assertThat(issue.mrNote()).contains("acceptance criteria");
        assertThat(issue.mrNote()).contains("checklist of done conditions");
        // No positional notes are produced for an artifact that cannot carry them.
        assertThat(issue.diffNotes()).isEmpty();
        // The synthetic metadata.json envelope is never surfaced as a student-facing location.
        assertThat(issue.mrNote()).doesNotContain("metadata.json");
    }

    @Test
    void compose_noIssuesNote_skipsFindingWhoseReasoningScrubsToBlank() {
        // First positive: reasoning is ENTIRELY grading-meta → sanitises to blank → must not emit a bare
        // "- **...:**" bullet with nothing after it. Second positive carries a real observation.
        ValidatedFinding scrubbed = new ValidatedFinding(
            "issue-has-checkable-outcome",
            "Checkable outcome",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.9f,
            null,
            "The practice requires a checkable outcome for a POSITIVE observation.",
            null,
            List.of()
        );
        ValidatedFinding real = new ValidatedFinding(
            "issue-scoped-to-single-concern",
            "Single concern",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.9f,
            null,
            "The issue describes one deliverable and stays within that single concern.",
            null,
            List.of()
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(scrubbed, real), WorkArtifact.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("one deliverable");
        // No dangling empty bullet for the scrubbed finding.
        assertThat(dc.mrNote()).doesNotContain(":** \n");
        assertThat(dc.mrNote()).doesNotContain("Checkable outcome:**\n");
    }

    @Test
    void compose_noIssuesNote_allReasoningScrubbed_fallsBackToNothingToChange() {
        ValidatedFinding scrubbed = new ValidatedFinding(
            "issue-has-checkable-outcome",
            "Checkable outcome",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.9f,
            null,
            "The practice requires a checkable outcome for a POSITIVE observation.",
            null,
            List.of()
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(scrubbed), WorkArtifact.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("nothing to change here");
        assertThat(dc.mrNote()).doesNotContain("What I observed");
    }

    @Test
    void compose_acknowledgementCount_reflectsImprovementsNotStrengths() {
        // One strength in front of TWO suggestions must read "a couple of things to tighten:", never
        // "one thing to tighten:" (the lead-in counts the improvements that follow).
        List<ValidatedFinding> findings = List.of(
            positiveFinding("issue-scoped-to-single-concern"),
            negativeFinding(
                "issue-has-checkable-outcome",
                "Missing checkable outcome",
                Severity.MINOR,
                List.of(new LocationSpec("metadata.json", 2)),
                null,
                "No acceptance criteria are stated.",
                "Add a done checklist."
            ),
            negativeFinding(
                "issue-states-an-actionable-problem",
                "Missing actionable problem",
                Severity.MINOR,
                List.of(new LocationSpec("metadata.json", 2)),
                null,
                "The description does not frame a concrete problem.",
                "State the who/what/why."
            )
        );

        DeliveryContent dc = DeliveryComposer.compose(findings, WorkArtifact.ISSUE);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("a couple of things to tighten:");
        assertThat(dc.mrNote()).doesNotContain("one thing to tighten:");
    }

    @Test
    void compose_allFindingsNotApplicable_returnsNullNoSpuriousAllClear() {
        // Every practice abstained: the artifact could not be assessed, so we must deliver NOTHING —
        // never a "nothing to change here" all-clear on work that was never actually evaluated.
        ValidatedFinding na1 = new ValidatedFinding(
            "issue-scoped-to-single-concern",
            "n/a",
            Presence.NOT_APPLICABLE,
            null,
            Severity.INFO,
            0.9f,
            null,
            "",
            null,
            List.of()
        );
        ValidatedFinding na2 = new ValidatedFinding(
            "issue-has-checkable-outcome",
            "n/a",
            Presence.NOT_APPLICABLE,
            null,
            Severity.INFO,
            0.9f,
            null,
            "",
            null,
            List.of()
        );

        assertThat(DeliveryComposer.compose(List.of(na1, na2), WorkArtifact.ISSUE)).isNull();
        assertThat(DeliveryComposer.compose(List.of(na1, na2), WorkArtifact.PULL_REQUEST)).isNull();
    }

    @Test
    void compose_metadataFinding_dropsYouWroteEchoEntirely() {
        // A metadata-field finding (location metadata.json) never echoes a "You wrote: …" quote: the agent's
        // metadata span is frequently a truncated heading / single token / title==body echo / raw JSON
        // envelope ("[Feat", "false", "Analysis Object Model (AOM)", "body": "…") that reads as broken output
        // and leaks raw fields to the student. The lesson stands on the reasoning + guidance instead.
        ValidatedFinding f = negativeFinding(
            "mr-description-quality",
            "PR description lacks clear motivation",
            Severity.MAJOR,
            List.of(new LocationSpec("metadata.json", 2)),
            List.of(
                "#39: use Logger and package\", \"body\" : \"#39: use Logger and package ## Description - use logger"
            ),
            "The body does not explain why the change is needed.",
            "Add a short Why paragraph."
        );

        DeliveryContent dc = DeliveryComposer.compose(List.of(f), WorkArtifact.PULL_REQUEST);

        assertThat(dc).isNotNull();
        // No echo, and therefore none of the raw metadata-snippet / JSON-envelope artifacts can leak.
        assertThat(dc.mrNote()).doesNotContain("You wrote:");
        assertThat(dc.mrNote()).doesNotContain("\"body\"");
        assertThat(dc.mrNote()).doesNotContain("\" : \"");
        assertThat(dc.mrNote()).doesNotContain("#39: use Logger");
        // The student still receives the actual lesson (reasoning + guidance).
        assertThat(dc.mrNote()).contains("does not explain why the change is needed");
        assertThat(dc.mrNote()).contains("Add a short Why paragraph");
    }

    // --- Cross-context follow-up fixes (F3 repo-strip, F4 epic dedup, F5 subordinate positive) ---

    @Test
    void compose_stripsLeadingRepoPrefixFromStudentLocation() {
        // F3: the agent sees the repo mounted at /workspace/inputs/sources/scm/repo and sometimes cites
        // "inputs/sources/scm/repo/<path>"; the student-facing location must be repo-relative.
        var findings = List.of(
            negativeFinding(
                "ships-tests-with-the-change",
                "Production logic without a test",
                Severity.MINOR,
                List.of(new LocationSpec("inputs/sources/scm/repo/client/App/Services/APIClient.swift", 12)),
                null,
                "New logic added without a test.",
                "Add a unit test."
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("client/App/Services/APIClient.swift");
        assertThat(dc.mrNote()).doesNotContain("inputs/sources/scm/repo/client/");
    }

    @Test
    void compose_epicIssue_collapsesOverlappingStructureFindings() {
        // F4: on an epic ISSUE the three structure detectors say the same thing — keep the highest-
        // severity lead, drop the redundant siblings, but never drop a DISTINCT lesson.
        var findings = List.of(
            negativeFinding(
                "issue-scoped-to-single-concern",
                "Bundles concerns",
                Severity.MAJOR,
                null,
                null,
                "This epic mixes capture and export concerns.",
                "Split it."
            ),
            negativeFinding(
                "issue-has-checkable-outcome",
                "No checkable outcome",
                Severity.MINOR,
                null,
                null,
                "No acceptance criteria are stated.",
                "Add criteria."
            ),
            negativeFinding(
                "breaks-large-work-into-trackable-subtasks",
                "No subtasks",
                Severity.MINOR,
                null,
                null,
                "No subtask checklist exists.",
                "Add a checklist."
            ),
            negativeFinding(
                "issue-states-an-actionable-problem",
                "Missing beneficiary",
                Severity.MINOR,
                null,
                null,
                "No who/why is stated.",
                "State the beneficiary."
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.ISSUE);
        assertThat(dc).isNotNull();
        // The MAJOR "is this issue well-formed?" lead (scoped) survives; its near-duplicate sibling
        // (checkable) is collapsed away. The DISTINCT lessons — breaks-large-work and states-actionable —
        // ALWAYS survive (G3: breaks-large-work is no longer in EPIC_STRUCTURE_PRACTICES).
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
    void compose_pullRequest_doesNotDedupStructureFindings() {
        // F4 guard: dedup is ISSUE-only — a PR keeps every finding.
        var findings = List.of(
            negativeFinding(
                "issue-has-checkable-outcome",
                "x",
                Severity.MINOR,
                List.of(new LocationSpec("a.swift", 1)),
                null,
                "No acceptance criteria are stated.",
                "g"
            ),
            negativeFinding(
                "breaks-large-work-into-trackable-subtasks",
                "y",
                Severity.MINOR,
                List.of(new LocationSpec("b.swift", 1)),
                null,
                "No subtask checklist exists.",
                "g"
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);
        // Both structure findings carry locations, so on a PR they become inline diff notes — and BOTH
        // survive: dropping the `artifact == ISSUE` guard on dedupEpicStructure would collapse them to one.
        assertThat(dc).isNotNull();
        assertThat(dc.diffNotes()).hasSize(2);
        assertThat(dc.diffNotes())
            .extracting(PracticeDetectionResultParser.DiffNote::filePath)
            .containsExactlyInAnyOrder("a.swift", "b.swift");
    }

    // --- W4: co-occurrence dedup — the same root fact must not deliver as two stacked MAJORs ---

    @Test
    void compose_coOccurringNoTestsFact_deliveredOnceNotAsTwoMajors() {
        // The no-tests fact fires twice: ready-and-traceable-handoff flags a DoD checkbox claiming "all tests
        // pass" (with no tests changed), and ships-tests-with-the-change flags the absent tests. A student
        // should read one root cause once — via the more actionable ships-tests finding — not two MAJORs.
        var findings = List.of(
            negativeFinding(
                "ready-and-traceable-handoff",
                "Definition of Done claims all tests pass",
                Severity.MAJOR,
                List.of(new LocationSpec("README.md", 3)),
                null,
                "The DoD checklist ticks 'all tests pass' but no test files changed.",
                "Untick it or add the tests."
            ),
            negativeFinding(
                "ships-tests-with-the-change",
                "Production logic ships without a test",
                Severity.MAJOR,
                List.of(new LocationSpec("Sources/Calc.swift", 12)),
                null,
                "New logic added with no accompanying test.",
                "Add a unit test that exercises the new branch."
            )
        );

        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(dc).isNotNull();
        // ONE blocking issue surfaced, not two — the redundant DoD-honesty finding is folded into ships-tests.
        assertThat(dc.mrNote()).contains("1 issue to tighten");
        assertThat(dc.mrNote()).doesNotContain("2 issues");
        // The kept (more actionable) lesson survives; the redundant sibling is gone.
        assertThat(dc.mrNote() + dc.diffNotes().get(0).body()).contains("Production logic ships without a test");
        assertThat(dc.mrNote()).doesNotContain("Definition of Done claims all tests pass");
        assertThat(dc.diffNotes()).hasSize(1);
    }

    @Test
    void compose_coOccurrencePair_keepsHandoffWhenShipsTestsAbsent() {
        // Guard: the redundant member is only dropped when its preferred partner co-occurs. With ships-tests
        // NOT present, ready-and-traceable-handoff stands on its own — never silently removed.
        var findings = List.of(
            negativeFinding(
                "ready-and-traceable-handoff",
                "Definition of Done claims all tests pass",
                Severity.MAJOR,
                List.of(new LocationSpec("README.md", 3)),
                null,
                "The DoD checklist ticks 'all tests pass' but no test files changed.",
                "Untick it or add the tests."
            )
        );

        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("1 issue to tighten");
        assertThat(dc.mrNote() + dc.diffNotes().get(0).body()).contains("Definition of Done claims all tests pass");
    }

    @Test
    void compose_blockingIssue_allowsSingleSubordinateProcessPositive() {
        // W1: under a blocking issue, suppress the cheerful opener but still land ONE subordinate strength.
        var findings = List.of(
            positiveFinding("engaging-with-inline-review-comments"),
            negativeFinding(
                "hardcoded-secrets",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Keys.swift", 1)),
                List.of("let k=\"s\""),
                "Secret exposed.",
                "Use env."
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).doesNotContain("Nice work");
        assertThat(dc.mrNote()).contains("Worth keeping: you're engaging with the review feedback.");
        // W3: subordinate strength lands AFTER the non-blocking count, never as a sandwich opener.
        assertThat(dc.mrNote().indexOf("to tighten")).isLessThan(dc.mrNote().indexOf("Worth keeping"));
        assertThat(dc.mrNote()).doesNotContain("before merging");
    }

    @Test
    void compose_blockingIssue_acknowledgesAnyHighConfidenceStrengthNotOnlyProcessActs() {
        // W1: formative feedback requires naming what to keep doing, so the single subordinate line surfaces
        // the run's highest-confidence GOOD finding of ANY practice — not only the named process acts. Here
        // the sole strength is a code-craft GOOD (handles-errors...), which previously was censored under a
        // blocking note; it must now be acknowledged, task-level, once.
        var strength = new ValidatedFinding(
            "handles-errors-instead-of-swallowing-them",
            "Errors are surfaced (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.99f,
            null,
            null,
            null,
            List.of()
        );
        var findings = List.of(
            strength,
            negativeFinding(
                "hardcoded-secrets",
                "Hardcoded secret",
                Severity.CRITICAL,
                List.of(new LocationSpec("Keys.swift", 1)),
                List.of("let k=\"s\""),
                "Secret exposed.",
                "Use env."
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).doesNotContain("Nice work");
        // An uncurated GOOD slug is acknowledged generically (grammatical), never dropped or dumped raw.
        assertThat(dc.mrNote()).contains("Worth keeping:");
        assertThat(dc.mrNote()).doesNotContain("handles errors instead of swallowing them");
        // Exactly one earned strength line — bounded, not a multi-strength sandwich.
        assertThat(dc.mrNote().split("Worth keeping:", -1)).hasSize(2);
    }

    // ----- Improvement-tail prioritisation + cap (the proportionality fix) -----

    private ValidatedFinding negativeWithConfidence(String slug, String title, Severity severity, float confidence) {
        return new ValidatedFinding(
            slug,
            title,
            Presence.ABSENT,
            Assessment.BAD,
            severity,
            confidence,
            buildEvidence(List.of(new LocationSpec(slug + ".swift", 10)), null),
            title + " reasoning.",
            null,
            List.of()
        );
    }

    @Test
    void compose_capsMinorTail_keepsThreeAndDisclosesOverflowHonestly() {
        // 6 MINOR improvements, zero blocking — the long-tail pile-on the bar rejects. Cap to 3 + "+3 more".
        List<ValidatedFinding> findings = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            findings.add(negativeWithConfidence("nudge-" + i, "Minor nudge " + i, Severity.MINOR, 0.90f));
        }

        DeliveryContent result = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        // Only 3 surfaced; the remaining 3 are disclosed, never silently dropped.
        assertThat(mrNote).contains("3 suggestions for improvement (+3 more minor suggestions):");
        // Cap also caps the inline diff notes — collapsed nudges leave no comment behind.
        assertThat(result.diffNotes()).hasSize(3);
    }

    @Test
    void compose_neverCapsBlocking_evenWithManyBlockers() {
        // 5 blocking (CRITICAL/MAJOR) + 4 MINOR. Blocking must ALL survive; only the MINOR tail is capped.
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(negativeWithConfidence("sec-1", "Secret 1", Severity.CRITICAL, 0.95f));
        findings.add(negativeWithConfidence("sec-2", "Secret 2", Severity.CRITICAL, 0.95f));
        findings.add(negativeWithConfidence("crash-1", "Crash 1", Severity.MAJOR, 0.9f));
        findings.add(negativeWithConfidence("crash-2", "Crash 2", Severity.MAJOR, 0.9f));
        findings.add(negativeWithConfidence("crash-3", "Crash 3", Severity.MAJOR, 0.9f));
        for (int i = 1; i <= 4; i++) {
            findings.add(negativeWithConfidence("minor-" + i, "Minor " + i, Severity.MINOR, 0.9f));
        }

        DeliveryContent result = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        // All 5 blockers kept, MINOR tail capped 4→3, one collapsed.
        assertThat(mrNote).contains(
            "5 issues to tighten, plus 3 suggestions for improvement (+1 more minor suggestion):"
        );
        assertThat(mrNote).doesNotContain("before merging");
        // 5 blocking + 3 improvements = 8 diff notes (raw was 9).
        assertThat(result.diffNotes()).hasSize(8);
    }

    @Test
    void compose_underTheCap_noOverflowTail() {
        // Exactly 3 improvements: at the cap, nothing collapses, no overflow tail.
        List<ValidatedFinding> findings = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            findings.add(negativeWithConfidence("nudge-" + i, "Minor nudge " + i, Severity.MINOR, 0.9f));
        }

        DeliveryContent result = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("3 suggestions for improvement:");
        assertThat(result.mrNote()).doesNotContain("more minor suggestion");
        assertThat(result.diffNotes()).hasSize(3);
    }

    @Test
    void compose_tieBreaksByConfidence_keepsMostCertainNudges() {
        // 4 MINORs, distinct confidences. Cap keeps the 3 highest-confidence; the lowest is collapsed.
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(negativeWithConfidence("low", "Low confidence nudge", Severity.MINOR, 0.50f));
        findings.add(negativeWithConfidence("high-a", "High A nudge", Severity.MINOR, 0.95f));
        findings.add(negativeWithConfidence("high-b", "High B nudge", Severity.MINOR, 0.92f));
        findings.add(negativeWithConfidence("high-c", "High C nudge", Severity.MINOR, 0.90f));

        DeliveryContent result = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("3 suggestions for improvement (+1 more minor suggestion):");
        // The three certain nudges survive; the least-certain one is the collapsed overflow.
        assertThat(mrNote).contains("High A nudge");
        assertThat(mrNote).contains("High B nudge");
        assertThat(mrNote).contains("High C nudge");
        assertThat(mrNote).doesNotContain("Low confidence nudge");
    }

    @Test
    void compose_keepsMinorOverInfo_infoIsTheFirstToCollapse() {
        // 3 MINOR + 2 INFO, cap 3. Severity beats confidence: the two INFO collapse first regardless of conf.
        List<ValidatedFinding> findings = new ArrayList<>();
        findings.add(negativeWithConfidence("minor-1", "Minor one", Severity.MINOR, 0.60f));
        findings.add(negativeWithConfidence("minor-2", "Minor two", Severity.MINOR, 0.60f));
        findings.add(negativeWithConfidence("minor-3", "Minor three", Severity.MINOR, 0.60f));
        findings.add(negativeWithConfidence("info-1", "Info one", Severity.INFO, 0.99f));
        findings.add(negativeWithConfidence("info-2", "Info two", Severity.INFO, 0.99f));

        DeliveryContent result = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("3 suggestions for improvement (+2 more minor suggestions):");
        assertThat(mrNote).contains("Minor one");
        assertThat(mrNote).contains("Minor two");
        assertThat(mrNote).contains("Minor three");
        // High-confidence INFO still collapses — proportionality favours the more-severe lesson.
        assertThat(mrNote).doesNotContain("Info one");
        assertThat(mrNote).doesNotContain("Info two");
    }

    @Test
    void undesirablePracticeObservedObservationIsTreatedAsAProblem() {
        // The assessment lives on the finding (ADR 0022): a present bad behaviour (PRESENT, BAD) is a
        // problem and surfaces an inline diff note, while the same present behaviour read as a strength
        // (PRESENT, GOOD) surfaces none — proving the partition consults assessment, not raw presence.
        JsonNode evidence = buildEvidence(
            List.of(new LocationSpec("Views/StockView.swift", 42)),
            List.of("let u = URL(s)!")
        );
        ValidatedFinding asProblemFinding = new ValidatedFinding(
            "uses-force-unwrap",
            "Force-unwrap present in changed code",
            Presence.PRESENT,
            Assessment.BAD,
            Severity.MAJOR,
            0.92f,
            evidence,
            "Force-unwrapping crashes on nil.",
            "Use guard-let instead.",
            List.of()
        );
        ValidatedFinding asStrengthFinding = new ValidatedFinding(
            "uses-force-unwrap",
            "Force-unwrap present in changed code",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.MAJOR,
            0.92f,
            evidence,
            "Force-unwrapping crashes on nil.",
            "Use guard-let instead.",
            List.of()
        );

        DeliveryContent asProblem = DeliveryComposer.compose(List.of(asProblemFinding), WorkArtifact.PULL_REQUEST);
        DeliveryContent asStrength = DeliveryComposer.compose(List.of(asStrengthFinding), WorkArtifact.PULL_REQUEST);

        assertThat(asProblem).isNotNull();
        assertThat(asProblem.diffNotes()).as("(PRESENT, BAD) is a problem → inline diff note").isNotEmpty();
        assertThat(asProblem.mrNote()).contains("Force-unwrap present in changed code");

        assertThat(asStrength).isNotNull();
        assertThat(asStrength.diffNotes()).as("(PRESENT, GOOD) is a strength → no problem diff note").isEmpty();
    }

    @Test
    void stripsGraderMechanicsLeakFromStudentNote() {
        // Regression: the model echoes the criteria's classifier flowchart into student-facing
        // reasoning. Each rubric sentence must be dropped while the title + guidance keep the lesson. This
        // locks the leak-guard so a regression can never ship band maths / gate predicates / pipeline plumbing.
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
        ValidatedFinding leaky = negativeFinding(
            "scope-one-reviewable-change",
            "28 files spread degrades review effectiveness",
            Severity.MAJOR,
            List.of(new LocationSpec("Views/Foo.swift", 10)),
            List.of("x"),
            leakyReasoning,
            "Split this MR into two stacked changes so each is reviewable on its own."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(leaky), WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        // The full student-facing surface = the MR summary + every inline diff note (inline-first puts the
        // detail on the note). Scrub-and-substance must hold across both.
        String note =
            result.mrNote() + "\n" + result.diffNotes().stream().map(DiffNote::body).collect(Collectors.joining("\n"));
        // Lesson (title + guidance + the one clean fact) survives.
        assertThat(note).contains("28 files spread degrades review effectiveness");
        assertThat(note).contains("Split this MR into two stacked changes");
        assertThat(note).contains("reviewer cannot review this as a single coherent change");
        // Every rubric-mechanics / pipeline-plumbing token is scrubbed.
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
        // Regression: the agent dropped its own plumbing into the evidence snippet, which the
        // "You wrote:" quote rendered verbatim past the reasoning sanitizer. A real student quote never
        // contains pipeline tokens, so the whole quote is suppressed when it does.
        ValidatedFinding f = negativeFinding(
            "mr-description-quality",
            "PR body lacks a quotable WHY",
            Severity.MAJOR,
            List.of(), // no code location → metadata "You wrote: “…”" path
            List.of(
                "diff_stat.txt lists 28 changed files — metadata.changed_files=14 is stale (material disagreement); trusting the diff"
            ),
            "The body enumerates what changed but never states why.",
            "Add a '## Why' section naming the user problem this solves."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(f), WorkArtifact.PULL_REQUEST);

        assertThat(result).isNotNull();
        String note = result.mrNote();
        assertThat(note).doesNotContain("You wrote:");
        assertThat(note).doesNotContain("diff_stat.txt");
        assertThat(note).doesNotContain("material disagreement");
        // The actual lesson still lands.
        assertThat(note).contains("PR body lacks a quotable WHY");
        assertThat(note).contains("Add a '## Why' section");
    }

    @Test
    void compose_synthesizedDiffNote_carriesFindingObservationFingerprint() {
        // A stamped finding (no agent suggestedDiffNotes) must propagate its correlation key onto the
        // synthesized diff note so the inline channel can match the placement back to the persisted finding.
        ValidatedFinding stamped = negativeFinding(
            "code-hygiene",
            "Dead code in view",
            Severity.MINOR,
            List.of(new LocationSpec("Views/DashboardView.swift", 15)),
            null,
            "Commented-out code adds noise.",
            "Remove the commented-out block."
        ).withRecurrenceKey("corr-synth-123");

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).recurrenceKey()).isEqualTo("corr-synth-123");
    }

    // ----- Signal-driven inline-section demotion (C1) -----

    @Test
    void recomposeMrNote_demotesDeliveredInlineFindingToPointer_keepsFullLineForUndelivered() {
        // Two inlinable findings, both with correlation keys. After inline delivery, ONE landed (its key is
        // in deliveredKeys) and must collapse to a "see inline comments" pointer — its full header line is
        // gone from the summary because the detail now lives on the diff. The OTHER did NOT land, so its full
        // header line MUST survive in the summary as the delivery-failure fallback.
        ValidatedFinding delivered = negativeFinding(
            "code-hygiene",
            "Dead code in view",
            Severity.MINOR,
            List.of(new LocationSpec("Views/DashboardView.swift", 15)),
            null,
            "Commented-out code adds noise.",
            "Remove it."
        ).withRecurrenceKey("corr-delivered");
        ValidatedFinding failed = negativeFinding(
            "meaningful-naming",
            "Non-descriptive name 'Data'",
            Severity.MINOR,
            List.of(new LocationSpec("Models/Data.swift", 8)),
            null,
            "Rename to a domain term.",
            "Use PortfolioSnapshot."
        ).withRecurrenceKey("corr-failed");

        List<ValidatedFinding> findings = List.of(delivered, failed);

        // Baseline (no signals yet): both findings keep their full summary lines, no pointer.
        String firstPass = DeliveryComposer.recomposeMrNote(findings, WorkArtifact.PULL_REQUEST, Map.of(), Set.of());
        assertThat(firstPass).contains("Dead code in view").contains("Non-descriptive name 'Data'");
        assertThat(firstPass).doesNotContain("see the");

        // After delivery: only corr-delivered landed.
        String demoted = DeliveryComposer.recomposeMrNote(
            findings,
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            Set.of("corr-delivered")
        );

        // The delivered finding's full header line is gone (demoted to the pointer).
        assertThat(demoted).doesNotContain("Dead code in view");
        // The pointer counts exactly the one delivered inline comment.
        assertThat(demoted).contains("**Inline comments on the diff:** see the 1 inline comment below.");
        // The finding whose inline note FAILED keeps its full summary line (the fallback).
        assertThat(demoted).contains("Non-descriptive name 'Data'");
        assertThat(demoted).contains("Models/Data.swift:8");
    }

    @Test
    void recomposeMrNote_allInlineDelivered_collapsesWholeListToPointer() {
        // Both inlinable findings landed → the inline section is just the header + a plural pointer, with NO
        // per-finding lines left in the summary. A no-op that ignored deliveredKeys would still list both.
        ValidatedFinding a = negativeFinding(
            "code-hygiene",
            "Dead code A",
            Severity.MINOR,
            List.of(new LocationSpec("A.swift", 1)),
            null,
            "Noise.",
            "Remove."
        ).withRecurrenceKey("k-a");
        ValidatedFinding b = negativeFinding(
            "code-hygiene",
            "Dead code B",
            Severity.MINOR,
            List.of(new LocationSpec("B.swift", 2)),
            null,
            "Noise.",
            "Remove."
        ).withRecurrenceKey("k-b");

        String demoted = DeliveryComposer.recomposeMrNote(
            List.of(a, b),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            Set.of("k-a", "k-b")
        );

        assertThat(demoted).contains("**Inline comments on the diff:** see the 2 inline comments below.");
        assertThat(demoted).doesNotContain("Dead code A");
        assertThat(demoted).doesNotContain("Dead code B");
    }

    @Test
    void recomposeMrNote_keylessFindingNeverDemoted_evenWithMatchingEmptyKey() {
        // A finding with no correlation key can never be demoted (it cannot be matched back to a posted note),
        // so its full line always survives — and Set.of().contains(null) must not blow up.
        ValidatedFinding keyless = negativeFinding(
            "code-hygiene",
            "Keyless dead code",
            Severity.MINOR,
            List.of(new LocationSpec("Z.swift", 3)),
            null,
            "Noise.",
            "Remove."
        );

        String demoted = DeliveryComposer.recomposeMrNote(
            List.of(keyless),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            Set.of("some-other-key")
        );

        assertThat(demoted).contains("Keyless dead code");
        assertThat(demoted).doesNotContain("see the");
    }

    @Test
    void compose_agentSuggestedDiffNote_inheritsFindingObservationFingerprint() {
        // When the agent supplied its own suggestedDiffNote (which carries no key of its own), the finding's
        // stamped key must still be carried over to the emitted note.
        DiffNote suggested = new DiffNote("Views/DashboardView.swift", 20, null, "Consider extracting this.");
        ValidatedFinding stamped = new ValidatedFinding(
            "code-hygiene",
            "Long method",
            Presence.ABSENT,
            Assessment.BAD,
            Severity.MINOR,
            0.9f,
            buildEvidence(List.of(new LocationSpec("Views/DashboardView.swift", 20)), null),
            "The method does too much.",
            "Extract the rendering branch.",
            List.of(suggested)
        ).withRecurrenceKey("corr-suggested-456");

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        // Body comes from the agent's suggestion; the key comes from the (stamped) finding.
        assertThat(result.diffNotes().get(0).body()).isEqualTo("Consider extracting this.");
        assertThat(result.diffNotes().get(0).recurrenceKey()).isEqualTo("corr-suggested-456");
    }

    @Test
    void compose_agentSuggestedDiffNote_scrubsGradingMetaFromBody() {
        // The agent's own note body is raw model output; grading-meta in it must be stripped before it reaches
        // the student, exactly like the synthesized fallback path — this branch must not bypass
        // sanitizeStudentText and leak rubric vocabulary verbatim on the inline note.
        DiffNote suggested = new DiffNote(
            "Views/DashboardView.swift",
            20,
            null,
            "Add a test for the parser. The practice requires coverage for a OBSERVED observation."
        );
        ValidatedFinding stamped = new ValidatedFinding(
            "code-hygiene",
            "Missing test",
            Presence.ABSENT,
            Assessment.BAD,
            Severity.MINOR,
            0.9f,
            buildEvidence(List.of(new LocationSpec("Views/DashboardView.swift", 20)), null),
            "The method does too much.",
            "Extract the rendering branch.",
            List.of(suggested)
        ).withRecurrenceKey("corr-scrub-789");

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        String body = result.diffNotes().get(0).body();
        assertThat(body).contains("Add a test for the parser.");
        assertThat(body).doesNotContainIgnoringCase("the practice requires");
        assertThat(body).doesNotContain("OBSERVED");
        // Key still propagates after the sanitize-and-rebuild.
        assertThat(result.diffNotes().get(0).recurrenceKey()).isEqualTo("corr-scrub-789");
    }

    @Test
    void compose_synthesizedDiffNote_anchorPathIsRepoRelativised() {
        // A1: the synthesized inline note's filePath must be stripped of the repo-mount prefix
        // (inputs/sources/scm/repo/…) so it anchors on the same repo-relative path the summary shows —
        // a raw-prefixed anchor mis-anchors/drops the note downstream while the summary still names the finding.
        var f = negativeFinding(
            "code-hygiene",
            "Unused import",
            Severity.MINOR,
            List.of(new LocationSpec("inputs/sources/scm/repo/src/components/Button.tsx", 1)),
            null,
            "Remove unused imports.",
            "Delete the import."
        );

        DeliveryContent result = DeliveryComposer.compose(List.of(f));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("src/components/Button.tsx");
    }

    @Test
    void compose_agentSuggestedDiffNote_anchorPathIsRepoRelativised() {
        // A1: the agent's suggested note carries a raw repo-mount path; the emitted note must be repo-relative.
        // A grounding diff (b/ side = repo-relative path) admits the anchor so the suggested branch runs.
        DiffNote suggested = new DiffNote(
            "inputs/sources/scm/repo/src/components/Button.tsx",
            1,
            null,
            "Remove this unused import."
        );
        ValidatedFinding stamped = new ValidatedFinding(
            "code-hygiene",
            "Unused import",
            Presence.ABSENT,
            Assessment.BAD,
            Severity.MINOR,
            0.9f,
            buildEvidence(List.of(new LocationSpec("inputs/sources/scm/repo/src/components/Button.tsx", 1)), null),
            "Remove unused imports.",
            "Delete the import.",
            List.of(suggested)
        ).withRecurrenceKey("corr-relativise-1");

        String diff =
            "diff --git a/src/components/Button.tsx b/src/components/Button.tsx\n" +
            "@@ -1,1 +1,1 @@\n" +
            "+import React from 'react';\n";

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped), WorkArtifact.PULL_REQUEST, Map.of(), diff);

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("src/components/Button.tsx");
    }

    // Transferable-principle ("Why this matters") surfacing — the catalogue whyItMatters wired into delivery.

    private static final String SCOPE_WHY =
        "A reviewer can only hold so much in their head at once; a focused change gets read carefully.";

    @Test
    void compose_withWhyBySlug_surfacesTransferablePrincipleOnCritique() {
        var f = negativeFinding(
            "scope-one-reviewable-change",
            "Change spans many unrelated concerns",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "This MR touches authentication, UI, and the build config in one diff.",
            "Split each concern into its own MR."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(f),
            WorkArtifact.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        );

        assertThat(result).isNotNull();
        // The authored principle is surfaced verbatim, labelled, between the observation and the next step.
        assertThat(result.mrNote()).contains("_Why this matters:_ " + SCOPE_WHY);
    }

    @Test
    void compose_withWhyBySlug_emptyMapIsBehaviourIdentical() {
        var f = negativeFinding(
            "scope-one-reviewable-change",
            "Change spans many unrelated concerns",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Touches three concerns.",
            "Split it up."
        );

        String withoutMap = DeliveryComposer.compose(List.of(f), WorkArtifact.PULL_REQUEST).mrNote();
        String withEmptyMap = DeliveryComposer.compose(List.of(f), WorkArtifact.PULL_REQUEST, Map.of()).mrNote();

        assertThat(withEmptyMap).isEqualTo(withoutMap);
        assertThat(withEmptyMap).doesNotContain("Why this matters");
    }

    @Test
    void compose_withWhyBySlug_surfacesPrincipleOncePerDelivery() {
        // Two critiques of the SAME practice must carry the principle exactly once (no repetition).
        var a = negativeFinding(
            "scope-one-reviewable-change",
            "Concern A bundled in",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Bundles concern A.",
            "Extract A."
        );
        var b = negativeFinding(
            "scope-one-reviewable-change",
            "Concern B bundled in",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Bundles concern B.",
            "Extract B."
        );

        String note = DeliveryComposer.compose(
            List.of(a, b),
            WorkArtifact.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).containsOnlyOnce("_Why this matters:_");
    }

    @Test
    void compose_withWhyBySlug_skipsPrincipleOnInfoNudge() {
        // INFO-severity nudges are too low-value to carry the extra principle line (cognitive-load budget).
        var info = negativeFinding(
            "leaves-the-code-clean-with-intent-revealing-comments",
            "A stray TODO remains",
            Severity.INFO,
            List.of(),
            List.of(),
            "One leftover TODO.",
            "Drop or resolve it."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(info),
            WorkArtifact.PULL_REQUEST,
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
        // Two DIFFERENT advisory (MINOR) critiques: only the lead one carries a principle — no wall of rationale.
        var a = negativeFinding(
            "describe-what-and-why",
            "Thin description",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Body is thin.",
            "Add a why."
        );
        var b = negativeFinding(
            "scope-one-reviewable-change",
            "PR is large",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Touches many files.",
            "Split it."
        );

        String note = DeliveryComposer.compose(
            List.of(a, b),
            WorkArtifact.PULL_REQUEST,
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
        // A blocking critique keeps its principle; one advisory teaching moment also lands → two lines total.
        var blocking = negativeFinding(
            "handles-errors-instead-of-swallowing-them",
            "Swallowed error",
            Severity.MAJOR,
            List.of(),
            List.of(),
            "Error is dropped.",
            "Surface it."
        );
        var advisory = negativeFinding(
            "describe-what-and-why",
            "Thin description",
            Severity.MINOR,
            List.of(),
            List.of(),
            "Body is thin.",
            "Add a why."
        );

        String note = DeliveryComposer.compose(
            List.of(blocking, advisory),
            WorkArtifact.PULL_REQUEST,
            Map.of(
                "handles-errors-instead-of-swallowing-them",
                "A swallowed error turns a loud failure into a silent one nobody can debug.",
                "describe-what-and-why",
                "A clear description lets a reviewer orient before reading the diff."
            )
        ).mrNote();

        assertThat(note.split("_Why this matters:_", -1)).hasSize(3); // 2 occurrences => 3 split parts
    }

    // --- W7: feed-up on the all-GOOD path — an above-bar student hears the standard affirmed, not silence ---

    private ValidatedFinding positiveWithReasoning(String slug, String reasoning) {
        return new ValidatedFinding(
            slug,
            humanizeTitle(slug) + " (positive)",
            Presence.PRESENT,
            Assessment.GOOD,
            Severity.INFO,
            0.95f,
            null,
            reasoning,
            null,
            List.of()
        );
    }

    @Test
    void compose_allGoodPath_rendersTransferablePrinciple() {
        // The strongest students (no issues) must still receive the feed-up + transferable layer: the
        // catalogue "Why this matters" line, on the lead strength bullet — not silence.
        var observed = List.of(
            positiveWithReasoning("scope-one-reviewable-change", "The change stays focused on a single concern.")
        );

        String note = DeliveryComposer.compose(
            observed,
            WorkArtifact.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).contains("What's working well here");
        assertThat(note).contains("_Why this matters:_ " + SCOPE_WHY);
    }

    @Test
    void compose_allGoodPath_principleRenderedAtMostOnce() {
        // Two strengths of the same practice on the all-GOOD path carry the principle exactly once.
        var observed = List.of(
            positiveWithReasoning("scope-one-reviewable-change", "Focused on one concern."),
            positiveWithReasoning("scope-one-reviewable-change", "Each commit is scoped.")
        );

        String note = DeliveryComposer.compose(
            observed,
            WorkArtifact.PULL_REQUEST,
            Map.of("scope-one-reviewable-change", SCOPE_WHY)
        ).mrNote();

        assertThat(note).containsOnlyOnce("_Why this matters:_");
    }

    @Test
    void compose_allGoodPath_noPrincipleWhenNoneAuthored() {
        // No authored principle for the slug → the all-GOOD note renders the observation but no Why line
        // (empty whyBySlug is a strict no-op — behaviour identical to before W7).
        var observed = List.of(positiveWithReasoning("scope-one-reviewable-change", "The change stays focused."));

        String note = DeliveryComposer.compose(observed, WorkArtifact.PULL_REQUEST, Map.of()).mrNote();

        assertThat(note).contains("What's working well here");
        assertThat(note).doesNotContain("_Why this matters:_");
    }

    // ---- M1: server-side grounding guard (drop a hallucinated inline anchor before it lands on a student) ----

    /** A minimal unified diff: one real changed file with one real added line. */
    private static final String REAL_DIFF =
        "diff --git a/Sources/Capture/DepthData.swift b/Sources/Capture/DepthData.swift\n" +
        "--- a/Sources/Capture/DepthData.swift\n" +
        "+++ b/Sources/Capture/DepthData.swift\n" +
        "@@ -10,3 +10,4 @@\n" +
        " struct DepthData {\n" +
        "+    let confidence: Float\n" +
        " }\n";

    @Test
    void groundingGuard_hallucinatedPath_anchorDropped_findingStillDelivers() {
        // A BAD finding anchored to a file that is NOT in the diff at all — a hallucinated locus.
        ValidatedFinding hallucinated = negativeFinding(
            "code-hygiene",
            "Dead code",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Ghost/FrameRecorder.swift", 76)),
            List.of("let x = 0"),
            "There is dead code here.",
            "Remove it."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(hallucinated),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        // The ungrounded inline anchor is dropped...
        assertThat(result.diffNotes()).isEmpty();
        // ...but the finding is NOT silently discarded — its detail survives in the summary.
        assertThat(result.mrNote()).contains("Dead code");
    }

    @Test
    void groundingGuard_realPathAndSnippet_anchorKept() {
        // A BAD finding whose file IS in the diff and whose snippet IS substring-present in the hunk.
        ValidatedFinding grounded = negativeFinding(
            "code-hygiene",
            "Missing doc on new field",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Capture/DepthData.swift", 11)),
            List.of("let confidence: Float"),
            "The new field is undocumented.",
            "Add a doc comment."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(grounded),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).filePath()).isEqualTo("Sources/Capture/DepthData.swift");
    }

    @Test
    void groundingGuard_realPathButSnippetNotInHunk_anchorDropped() {
        // File is in the diff, but the quoted snippet never appears in its hunk — a fabricated evidence line.
        ValidatedFinding fabricatedSnippet = negativeFinding(
            "code-hygiene",
            "Phantom evidence",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Capture/DepthData.swift", 11)),
            List.of("deleteEverything() // never written"),
            "This line is a problem.",
            "Fix it."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(fabricatedSnippet),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            REAL_DIFF
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).isEmpty(); // ungrounded snippet ⇒ no inline anchor
        assertThat(result.mrNote()).contains("Phantom evidence"); // finding still delivered in summary
    }

    @Test
    void groundingGuard_issueArtifact_forcesNoFileLocus() {
        // An ISSUE finding carries a file location the agent should never have set — it must not become an anchor.
        ValidatedFinding issueFinding = negativeFinding(
            "issue-states-an-actionable-problem",
            "Vague problem statement",
            Severity.MINOR,
            List.of(new LocationSpec("metadata.json", 1)),
            List.of("\"title\": \"do stuff\""),
            "The issue does not state a concrete problem.",
            "State the problem as <one sentence>."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(issueFinding),
            WorkArtifact.ISSUE,
            Map.of(),
            null // issues have no diff; force-no-locus still applies via the ISSUE branch
        );

        assertThat(result).isNotNull();
        // Issues never inline anyway, but the guard makes the no-locus contract explicit.
        assertThat(result.diffNotes()).isEmpty();
        assertThat(result.mrNote()).contains("Vague problem statement");
    }

    @Test
    void groundingGuard_noDiffSupplied_isNoOp_anchorKept() {
        // Without a diff the guard is INACTIVE: a PR finding's anchor passes through unchanged, preserving the
        // existing delivery layout for callers that cannot produce the diff (the downstream line validator
        // remains the only check).
        ValidatedFinding finding = negativeFinding(
            "code-hygiene",
            "Some inline issue",
            Severity.MINOR,
            List.of(new LocationSpec("Sources/Whatever.swift", 5)),
            List.of("anything"),
            "An inline issue.",
            "Fix it."
        );

        DeliveryContent result = DeliveryComposer.compose(
            List.of(finding),
            WorkArtifact.PULL_REQUEST,
            Map.of(),
            (String) null
        );

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1); // no-op guard ⇒ anchor kept
    }
}
