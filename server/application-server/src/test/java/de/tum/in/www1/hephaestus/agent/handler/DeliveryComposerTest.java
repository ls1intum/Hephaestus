package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeliveryComposer")
class DeliveryComposerTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Evidence builders ──

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

    // ── Finding builders ──

    private ValidatedFinding positiveFinding(String slug) {
        return new ValidatedFinding(
            slug,
            humanizeTitle(slug) + " (positive)",
            Verdict.POSITIVE,
            Severity.INFO,
            0.90f,
            null,
            null,
            null
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
            Verdict.NEGATIVE,
            severity,
            0.92f,
            buildEvidence(locations, snippets),
            reasoning,
            guidance
        );
    }

    private static String humanizeTitle(String slug) {
        return slug.replace('-', ' ').substring(0, 1).toUpperCase() + slug.replace('-', ' ').substring(1);
    }

    // ── Realistic findings used across tests ──

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

    // =========================================================================
    // Test 1: Mixed findings
    // =========================================================================

    @Test
    @DisplayName("compose with mixed findings produces expected MR note")
    void compose_withMixedFindings_producesExpectedMrNote() {
        List<ValidatedFinding> findings = mixedFindings();

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        // Opening: names up to 2 positive practices (using positive labels, not raw slugs)
        assertThat(mrNote).contains("error state handling");
        assertThat(mrNote).contains("view decomposition");

        // Issue count: split blocking vs improvement (2 CRITICAL/MAJOR + 2 MINOR)
        assertThat(mrNote).contains("2 issues to fix before merging");
        assertThat(mrNote).contains("2 suggestions for improvement");

        // Severity emojis (no bracket labels — emoji is sufficient)
        assertThat(mrNote).doesNotContain("[CRITICAL]");
        assertThat(mrNote).doesNotContain("[MAJOR]");
        assertThat(mrNote).doesNotContain("[MINOR]");
        // Red circle emoji for CRITICAL
        assertThat(mrNote).contains("\uD83D\uDD34");
        // Orange circle emoji for MAJOR
        assertThat(mrNote).contains("\uD83D\uDFE0");
        // Yellow circle emoji for MINOR
        assertThat(mrNote).contains("\uD83D\uDFE1");

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

        // All 4 negatives get diff notes with full content
        assertThat(result.diffNotes()).hasSize(4);
        DiffNote secretsNote = result
            .diffNotes()
            .stream()
            .filter(n -> n.filePath().equals("Config/APIKeys.swift"))
            .findFirst()
            .orElseThrow();
        assertThat(secretsNote.body()).contains("ProcessInfo.processInfo.environment");
    }

    // =========================================================================
    // Test 2: All positive findings produce an approval comment
    // =========================================================================

    @Test
    @DisplayName("compose with all positive findings produces approval note")
    void compose_withAllPositive_producesApprovalNote() {
        List<ValidatedFinding> findings = List.of(
            positiveFinding("error-state-handling"),
            positiveFinding("view-decomposition"),
            positiveFinding("meaningful-naming")
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        assertThat(result.mrNote()).contains("No issues found");
        assertThat(result.mrNote()).contains("error state handling");
        assertThat(result.mrNote()).contains("view decomposition");
        assertThat(result.diffNotes()).isEmpty();
    }

    // =========================================================================
    // Test 3: Overflow with >5 negatives
    // =========================================================================

    @Test
    @DisplayName("compose with many negatives shows all in compact list")
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

        // All inlinable → compact list, no overflow message
        assertThat(mrNote).contains("2 issues to fix before merging");
        assertThat(mrNote).contains("5 suggestions for improvement");
        assertThat(mrNote).doesNotContain("Plus");

        // Severity ordering in compact list: CRITICAL first (🔴), then MAJOR (🟠)
        int criticalIdx = mrNote.indexOf("\uD83D\uDD34");
        int majorIdx = mrNote.indexOf("\uD83D\uDFE0");
        assertThat(criticalIdx).isGreaterThanOrEqualTo(0);
        assertThat(majorIdx).isGreaterThan(criticalIdx);

        // All 7 negatives get diff notes
        assertThat(result.diffNotes()).hasSize(7);
    }

    // =========================================================================
    // Test 4: All negatives get inline diff notes
    // =========================================================================

    @Test
    @DisplayName("compose creates diff notes for all negatives")
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

        // Diff note bodies include emoji severity + bold title header
        for (DiffNote dn : diffNotes) {
            assertThat(dn.body()).contains("**");
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    @DisplayName("compose with null input returns null")
    void compose_withNull_returnsNull() {
        assertThat(DeliveryComposer.compose(null)).isNull();
    }

    @Test
    @DisplayName("compose with empty list returns null")
    void compose_withEmptyList_returnsNull() {
        assertThat(DeliveryComposer.compose(List.of())).isNull();
    }

    @Test
    @DisplayName("compose renders non-inlinable findings in full in MR summary")
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
    @DisplayName("compose puts guidance for inlinable MINOR findings in diff notes")
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

        // Full detail is in the diff note, not MR summary
        assertThat(result.diffNotes()).hasSize(1);
        DiffNote note = result.diffNotes().get(0);
        assertThat(note.body()).contains("Commented-out code adds noise.");
        assertThat(note.body()).contains("Remove the commented-out block");
    }

    @Test
    @DisplayName("compose with only minor negatives uses improvement language")
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
}
