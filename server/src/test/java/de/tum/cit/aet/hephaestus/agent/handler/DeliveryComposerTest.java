package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Polarity;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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
            Observation.OBSERVED,
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
            Observation.NOT_OBSERVED,
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
    void compose_forIssueArtifact_dropsBeforeMergingFromBlockingCta() {
        // Issues are not merged: the blocking CTA must read "to fix", never "to fix before merging".
        DeliveryContent result = DeliveryComposer.compose(
            mixedFindings(),
            de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.ISSUE
        );

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).contains("2 issues to fix");
        assertThat(mrNote).doesNotContain("before merging");
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

        // Mixed reviews with multiple blocking issues should not front-load praise.
        assertThat(mrNote).doesNotContain("error state handling");
        assertThat(mrNote).doesNotContain("view decomposition");

        // Issue count: split blocking vs improvement (2 CRITICAL/MAJOR + 2 MINOR)
        assertThat(mrNote).contains("2 issues to fix before merging");
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
            Observation.OBSERVED,
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
        assertThat(mrNote).contains("2 issues to fix before merging");
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
        // BEFORE the emoji. Pins appendFindingHeader against the old MR-list drift where the emoji sat
        // outside the bold (`<emoji> **title**`).
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
    void compose_blockingIssue_suppressesAcknowledgement() {
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

        // Front-loading praise ahead of a blocking issue would read as a hollow feedback sandwich.
        assertThat(mrNote).doesNotContain("Nice work");
        assertThat(mrNote).contains("to fix before merging");
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
        // The exact grading-mechanics phrasing observed leaking into live student notes.
        String leaked1 = "The body has no rationale, which results in a NEGATIVE finding with MINOR severity.";
        String leaked2 =
            "This exceeds the ≤200 line threshold for a POSITIVE finding, placing it in the INFO severity band.";
        String leaked3 = "The title is generic, violating the practice that requires an imperative summary.";

        // The exact mid-paragraph leak observed on MR !13 (two sentences, second is pure rubric meta).
        String leaked4 =
            "The title is descriptive but the body only lists what was done without a quoted sentence " +
            "that explains why. The practice requires a specific 'why' sentence to be present for a " +
            "POSITIVE verdict; its absence leads to this point at the MINOR severity level.";

        for (String s : List.of(leaked1, leaked2, leaked3, leaked4)) {
            String clean = DeliveryComposer.sanitizeStudentText(s);
            assertThat(clean).doesNotContainIgnoringCase("NEGATIVE finding");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE finding");
            assertThat(clean).doesNotContainIgnoringCase("POSITIVE verdict");
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
        // Live regression (obsphera 582, deepseek): the model echoed the criteria's internal bucket maths
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

    @Test
    void sanitizeStudentText_stripsCrossPracticeOrchestrationLeaks() {
        // Live Obsphera eval (deepseek, pr1/pr6/pr7): the model narrated how findings were ROUTED between
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
    void sanitizeStudentText_preservesMarkdownListAndHeadingNewlines() {
        // Live regression (obsphera CR2, 2026-06-12): a bulleted acceptance-criteria block whose items
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
        // Live regression (obsphera MR575, 2026-06-13): deepseek terminated the describe-what-and-why
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

    // --- Live-E2E regression fixes (go98weh batch, 2026-06-11) ---

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
            Observation.OBSERVED,
            Severity.INFO,
            0.9f,
            null,
            "The practice requires a checkable outcome for a POSITIVE verdict.",
            null,
            List.of()
        );
        ValidatedFinding real = new ValidatedFinding(
            "issue-scoped-to-single-concern",
            "Single concern",
            Observation.OBSERVED,
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
            Observation.OBSERVED,
            Severity.INFO,
            0.9f,
            null,
            "The practice requires a checkable outcome for a POSITIVE verdict.",
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
            Observation.NOT_APPLICABLE,
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
            Observation.NOT_APPLICABLE,
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
    void compose_youWrote_stripsJsonEnvelopeLeakFromMetadataSnippet() {
        // The agent sometimes quotes a raw span of metadata.json, dragging JSON field syntax into the
        // "You wrote:" quote. The composed note must show the prose, never the "body": key/quotes.
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
        assertThat(dc.mrNote()).contains("You wrote:");
        assertThat(dc.mrNote()).contains("## Description");
        // No JSON envelope artifacts leak into the student-facing quote.
        assertThat(dc.mrNote()).doesNotContain("\"body\"");
        assertThat(dc.mrNote()).doesNotContain("\" : \"");
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
                List.of(new LocationSpec("inputs/sources/scm/repo/client/Obsphera/Services/APIClient.swift", 12)),
                null,
                "New logic added without a test.",
                "Add a unit test."
            )
        );
        var dc = DeliveryComposer.compose(findings, WorkArtifact.PULL_REQUEST);
        assertThat(dc).isNotNull();
        assertThat(dc.mrNote()).contains("client/Obsphera/Services/APIClient.swift");
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

    @Test
    void compose_blockingIssue_allowsSingleSubordinateProcessPositive() {
        // F5: under a blocking issue, suppress the cheerful opener but allow ONE subordinate process
        // positive — and only a process act, never a code-correctness positive.
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
        assertThat(dc.mrNote()).contains("Worth keeping");
        assertThat(dc.mrNote().indexOf("to fix before merging")).isLessThan(dc.mrNote().indexOf("Worth keeping"));
    }

    @Test
    void compose_blockingIssue_codeCorrectnessPositiveNeverSurfaces() {
        // F5 guard: a non-process (code-correctness) positive never leaks into a blocking note.
        var findings = List.of(
            positiveFinding("handles-errors-instead-of-swallowing-them"),
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
        assertThat(dc.mrNote()).doesNotContain("Worth keeping");
        assertThat(dc.mrNote()).doesNotContain("Nice work");
    }

    // ----- Improvement-tail prioritisation + cap (the proportionality fix) -----

    private ValidatedFinding negativeWithConfidence(String slug, String title, Severity severity, float confidence) {
        return new ValidatedFinding(
            slug,
            title,
            Observation.NOT_OBSERVED,
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
            "5 issues to fix before merging, plus 3 suggestions for improvement (+1 more minor suggestion):"
        );
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
    void undesirablePracticeObservedVerdictIsTreatedAsAProblem() {
        // An anti-pattern practice (polarity UNDESIRABLE) whose bad behaviour was OBSERVED is a problem,
        // not a strength (ADR 0021, F-6). The same finding under the default DESIRABLE reading is a
        // strength and surfaces no diff note — proving the partition consults polarity, not raw verdict.
        ValidatedFinding observed = new ValidatedFinding(
            "uses-force-unwrap",
            "Force-unwrap present in changed code",
            Observation.OBSERVED,
            Severity.MAJOR,
            0.92f,
            buildEvidence(List.of(new LocationSpec("Views/StockView.swift", 42)), List.of("let u = URL(s)!")),
            "Force-unwrapping crashes on nil.",
            "Use guard-let instead.",
            List.of()
        );

        DeliveryContent asProblem = DeliveryComposer.compose(
            List.of(observed),
            WorkArtifact.PULL_REQUEST,
            Map.of("uses-force-unwrap", Polarity.UNDESIRABLE)
        );
        DeliveryContent asStrength = DeliveryComposer.compose(List.of(observed), WorkArtifact.PULL_REQUEST, Map.of());

        assertThat(asProblem).isNotNull();
        assertThat(asProblem.diffNotes()).as("UNDESIRABLE+OBSERVED is a problem → inline diff note").isNotEmpty();
        assertThat(asProblem.mrNote()).contains("Force-unwrap present in changed code");

        assertThat(asStrength).isNotNull();
        assertThat(asStrength.diffNotes()).as("DESIRABLE+OBSERVED is a strength → no problem diff note").isEmpty();
    }

    @Test
    void stripsGraderMechanicsLeakFromStudentNote() {
        // Live Obsphera E2E eval: the model echoes the criteria's classifier flowchart into student-facing
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
        // Live Obsphera E2E: the agent dropped its own plumbing into the evidence snippet, which the
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
    void compose_synthesizedDiffNote_carriesFindingFindingFingerprint() {
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
        ).withFindingFingerprint("corr-synth-123");

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        assertThat(result.diffNotes().get(0).findingFingerprint()).isEqualTo("corr-synth-123");
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
        ).withFindingFingerprint("corr-delivered");
        ValidatedFinding failed = negativeFinding(
            "meaningful-naming",
            "Non-descriptive name 'Data'",
            Severity.MINOR,
            List.of(new LocationSpec("Models/Data.swift", 8)),
            null,
            "Rename to a domain term.",
            "Use PortfolioSnapshot."
        ).withFindingFingerprint("corr-failed");

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
        ).withFindingFingerprint("k-a");
        ValidatedFinding b = negativeFinding(
            "code-hygiene",
            "Dead code B",
            Severity.MINOR,
            List.of(new LocationSpec("B.swift", 2)),
            null,
            "Noise.",
            "Remove."
        ).withFindingFingerprint("k-b");

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
    void compose_agentSuggestedDiffNote_inheritsFindingFindingFingerprint() {
        // When the agent supplied its own suggestedDiffNote (which carries no key of its own), the finding's
        // stamped key must still be carried over to the emitted note.
        DiffNote suggested = new DiffNote("Views/DashboardView.swift", 20, null, "Consider extracting this.");
        ValidatedFinding stamped = new ValidatedFinding(
            "code-hygiene",
            "Long method",
            Observation.NOT_OBSERVED,
            Severity.MINOR,
            0.9f,
            buildEvidence(List.of(new LocationSpec("Views/DashboardView.swift", 20)), null),
            "The method does too much.",
            "Extract the rendering branch.",
            List.of(suggested)
        ).withFindingFingerprint("corr-suggested-456");

        DeliveryContent result = DeliveryComposer.compose(List.of(stamped));

        assertThat(result).isNotNull();
        assertThat(result.diffNotes()).hasSize(1);
        // Body comes from the agent's suggestion; the key comes from the (stamped) finding.
        assertThat(result.diffNotes().get(0).body()).isEqualTo("Consider extracting this.");
        assertThat(result.diffNotes().get(0).findingFingerprint()).isEqualTo("corr-suggested-456");
    }
}
