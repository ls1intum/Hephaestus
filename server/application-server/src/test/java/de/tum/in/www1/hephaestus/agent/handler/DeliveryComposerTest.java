package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("DeliveryComposer")
class DeliveryComposerTest {

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
            guidance,
            CaMethod.COACHING
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
        findings.add(negativeFinding(
            "hardcoded-secrets",
            "Hardcoded API key exposed in source",
            Severity.CRITICAL,
            List.of(new LocationSpec("Config/APIKeys.swift", 5)),
            List.of("let apiKey = \"sk-abc123\""),
            "An API key is hardcoded directly in source code. Anyone with repository access can extract this secret and use it to make authenticated API calls on your behalf.",
            "Store secrets in environment variables or a secure keychain. Use a configuration file excluded from version control (e.g., .gitignore) and load the key at runtime:\n```swift\nlet apiKey = ProcessInfo.processInfo.environment[\"API_KEY\"] ?? \"\"\n```"
        ));

        // MAJOR: fatal-error-crash
        findings.add(negativeFinding(
            "fatal-error-crash",
            "Force-unwrap causes crash on invalid URL",
            Severity.MAJOR,
            List.of(new LocationSpec("Views/StockView.swift", 42)),
            List.of("let url = URL(string: urlString)!"),
            "Force-unwrapping URL(string:) will crash the app if urlString contains invalid characters or is malformed. This is a common cause of App Store rejections.",
            "Use guard-let or if-let to safely unwrap:\n```swift\nguard let url = URL(string: urlString) else { return }\n```"
        ));

        // MINOR: code-hygiene
        findings.add(negativeFinding(
            "code-hygiene",
            "Commented-out code left in view",
            Severity.MINOR,
            List.of(new LocationSpec("Views/DashboardView.swift", 15)),
            null,
            "Commented-out code adds noise and makes diffs harder to review. Remove dead code and rely on version control history instead.",
            null
        ));

        // MINOR: meaningful-naming
        findings.add(negativeFinding(
            "meaningful-naming",
            "Non-descriptive type name 'Data'",
            Severity.MINOR,
            List.of(new LocationSpec("Models/Data.swift", 8)),
            null,
            "The type name 'Data' shadows Foundation.Data and conveys no domain meaning. Rename to something descriptive like 'PortfolioSnapshot' or 'StockQuote'.",
            null
        ));

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

        // Print the full MR note for quality evaluation
        System.out.println("=== MR NOTE OUTPUT (mixed findings) ===");
        System.out.println(mrNote);
        System.out.println("=== END MR NOTE ===");

        // Opening: names positive practices
        assertThat(mrNote).contains("error state handling");
        assertThat(mrNote).contains("view decomposition");
        assertThat(mrNote).contains("meaningful naming");

        // Issue count with merge-blocking language (has CRITICAL + MAJOR)
        assertThat(mrNote).contains("4 issues to address before merge");

        // Severity emojis and labels
        assertThat(mrNote).contains("[CRITICAL]");
        assertThat(mrNote).contains("[MAJOR]");
        assertThat(mrNote).contains("[MINOR]");
        // Red circle emoji for CRITICAL
        assertThat(mrNote).contains("\uD83D\uDD34");
        // Orange circle emoji for MAJOR
        assertThat(mrNote).contains("\uD83D\uDFE0");
        // Yellow circle emoji for MINOR
        assertThat(mrNote).contains("\uD83D\uDFE1");

        // Code snippets for CRITICAL/MAJOR
        assertThat(mrNote).contains("let apiKey = \"sk-abc123\"");
        assertThat(mrNote).contains("let url = URL(string: urlString)!");

        // Location references
        assertThat(mrNote).contains("Config/APIKeys.swift:5");
        assertThat(mrNote).contains("Views/StockView.swift:42");

        // Reasoning for CRITICAL finding
        assertThat(mrNote).contains("hardcoded directly in source code");
        // Guidance for CRITICAL finding
        assertThat(mrNote).contains("ProcessInfo.processInfo.environment");

        // Reasoning for MAJOR finding
        assertThat(mrNote).contains("Force-unwrapping");
        // Guidance for MAJOR finding
        assertThat(mrNote).contains("guard let url");

        // MINOR: only reasoning, no code snippets in the note body
        assertThat(mrNote).contains("Commented-out code adds noise");
        assertThat(mrNote).contains("The type name 'Data' shadows Foundation.Data");

        // MINOR findings show reasoning (and guidance if present)
        assertThat(mrNote).contains("Views/DashboardView.swift:15");
        assertThat(mrNote).contains("Models/Data.swift:8");

        // Code blocks should use detected language from file extension
        assertThat(mrNote).contains("```swift\n");
        assertThat(mrNote).doesNotContain("```java");
    }

    // =========================================================================
    // Test 2: All positive findings return null
    // =========================================================================

    @Test
    @DisplayName("compose with all positive findings returns null")
    void compose_withAllPositive_returnsNull() {
        List<ValidatedFinding> findings = List.of(
            positiveFinding("error-state-handling"),
            positiveFinding("view-decomposition"),
            positiveFinding("meaningful-naming")
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNull();

        System.out.println("=== ALL POSITIVE ===");
        System.out.println("Result: null (silence = approval, no comment posted)");
        System.out.println("=== END ALL POSITIVE ===");
    }

    // =========================================================================
    // Test 3: Overflow with >5 negatives
    // =========================================================================

    @Test
    @DisplayName("compose with overflow shows overflow message")
    void compose_withOverflow_showsOverflowMessage() {
        List<ValidatedFinding> findings = new ArrayList<>();

        // 7 negatives: exceed the MAX_MR_NOTE_FINDINGS (5)
        findings.add(negativeFinding(
            "hardcoded-secrets", "Hardcoded secret", Severity.CRITICAL,
            List.of(new LocationSpec("Config/Keys.swift", 1)),
            List.of("let key = \"secret\""),
            "Secret exposed.", "Use env vars."
        ));
        findings.add(negativeFinding(
            "fatal-error-crash", "Force unwrap crash", Severity.MAJOR,
            List.of(new LocationSpec("Views/A.swift", 10)),
            List.of("url!"),
            "Crash risk.", "Use guard."
        ));
        findings.add(negativeFinding(
            "code-hygiene", "Dead code", Severity.MINOR,
            List.of(new LocationSpec("Views/B.swift", 20)),
            null,
            "Remove dead code.", null
        ));
        findings.add(negativeFinding(
            "meaningful-naming", "Bad name", Severity.MINOR,
            List.of(new LocationSpec("Models/C.swift", 30)),
            null,
            "Use descriptive names.", null
        ));
        findings.add(negativeFinding(
            "error-state-handling", "Missing error UI", Severity.MINOR,
            List.of(new LocationSpec("Views/D.swift", 40)),
            null,
            "Show errors to user.", null
        ));
        findings.add(negativeFinding(
            "view-decomposition", "Monolith view", Severity.MINOR,
            List.of(new LocationSpec("Views/E.swift", 50)),
            null,
            "Break view into subviews.", null
        ));
        findings.add(negativeFinding(
            "accessibility", "Missing labels", Severity.INFO,
            List.of(new LocationSpec("Views/F.swift", 60)),
            null,
            "Add accessibility labels.", null
        ));

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();
        assertThat(mrNote).isNotNull();

        System.out.println("=== MR NOTE OUTPUT (overflow) ===");
        System.out.println(mrNote);
        System.out.println("=== END MR NOTE (overflow) ===");

        // Should show exactly 5 findings in the note (the most severe ones)
        // and an overflow message for the remaining 2
        assertThat(mrNote).contains("7 issues to address before merge");
        assertThat(mrNote).contains("Plus 2 more issues noted as inline comments on the diff.");

        // The 5 shown should be severity-ordered: CRITICAL first, then MAJOR, then MINORs
        int criticalIdx = mrNote.indexOf("[CRITICAL]");
        int majorIdx = mrNote.indexOf("[MAJOR]");
        assertThat(criticalIdx).isGreaterThanOrEqualTo(0);
        assertThat(majorIdx).isGreaterThan(criticalIdx);

        // All 7 negatives get diff notes (not capped to 5)
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

        System.out.println("=== DIFF NOTES ===");
        for (DiffNote dn : diffNotes) {
            System.out.println("File: " + dn.filePath() + ":" + dn.startLine()
                + (dn.endLine() != null ? "-" + dn.endLine() : ""));
            System.out.println("Body: " + dn.body());
            System.out.println("---");
        }
        System.out.println("=== END DIFF NOTES ===");

        // All 4 negatives should have diff notes
        assertThat(diffNotes).hasSize(4);

        // Verify file paths and line numbers match evidence
        DiffNote secretsNote = diffNotes.stream()
            .filter(n -> n.filePath().equals("Config/APIKeys.swift"))
            .findFirst().orElseThrow();
        assertThat(secretsNote.startLine()).isEqualTo(5);
        assertThat(secretsNote.body()).contains("Hardcoded API key");

        DiffNote crashNote = diffNotes.stream()
            .filter(n -> n.filePath().equals("Views/StockView.swift"))
            .findFirst().orElseThrow();
        assertThat(crashNote.startLine()).isEqualTo(42);
        assertThat(crashNote.body()).contains("Force-unwrap");

        DiffNote hygieneNote = diffNotes.stream()
            .filter(n -> n.filePath().equals("Views/DashboardView.swift"))
            .findFirst().orElseThrow();
        assertThat(hygieneNote.startLine()).isEqualTo(15);

        DiffNote namingNote = diffNotes.stream()
            .filter(n -> n.filePath().equals("Models/Data.swift"))
            .findFirst().orElseThrow();
        assertThat(namingNote.startLine()).isEqualTo(8);

        // Diff note bodies should contain the title
        for (DiffNote dn : diffNotes) {
            assertThat(dn.body()).startsWith("**");
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
    @DisplayName("compose detects language from file extension for code blocks")
    void compose_detectsLanguage_fromFileExtension() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "fatal-error-crash", "NullPointerException risk",
                Severity.CRITICAL,
                List.of(new LocationSpec("src/main/java/App.java", 42)),
                List.of("String val = map.get(key);"),
                "This can return null.", "Use Optional."
            ),
            negativeFinding(
                "code-hygiene", "Unused import",
                Severity.MAJOR,
                List.of(new LocationSpec("src/components/Button.tsx", 1)),
                List.of("import React from 'react';"),
                "Remove unused imports.", "Delete the import."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);
        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        System.out.println("=== MR NOTE OUTPUT (language detection) ===");
        System.out.println(mrNote);
        System.out.println("=== END MR NOTE (language detection) ===");

        // Java file should get ```java fence
        assertThat(mrNote).contains("```java\n");
        // TSX file should get ```tsx fence
        assertThat(mrNote).contains("```tsx\n");
        // No ```swift should appear
        assertThat(mrNote).doesNotContain("```swift");
    }

    @Test
    @DisplayName("compose includes guidance for MINOR findings")
    void compose_minorFindings_includeGuidance() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "code-hygiene", "Dead code in view",
                Severity.MINOR,
                List.of(new LocationSpec("Views/DashboardView.swift", 15)),
                null,
                "Commented-out code adds noise.",
                "Remove the commented-out block and rely on git history."
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);
        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        // MINOR should now include BOTH reasoning and guidance
        assertThat(mrNote).contains("Commented-out code adds noise.");
        assertThat(mrNote).contains("Remove the commented-out block");
    }

    @Test
    @DisplayName("compose with only minor negatives uses improvement language")
    void compose_withOnlyMinorNegatives_usesImprovementLanguage() {
        List<ValidatedFinding> findings = List.of(
            negativeFinding(
                "code-hygiene", "Dead code", Severity.MINOR,
                List.of(new LocationSpec("Views/X.swift", 10)),
                null,
                "Clean up dead code.", null
            ),
            negativeFinding(
                "meaningful-naming", "Poor name", Severity.INFO,
                List.of(new LocationSpec("Models/Y.swift", 5)),
                null,
                "Use better names.", null
            )
        );

        DeliveryContent result = DeliveryComposer.compose(findings);

        assertThat(result).isNotNull();
        String mrNote = result.mrNote();

        System.out.println("=== MR NOTE OUTPUT (minor only) ===");
        System.out.println(mrNote);
        System.out.println("=== END MR NOTE (minor only) ===");

        // No CRITICAL/MAJOR => improvement language, not merge-blocking
        assertThat(mrNote).contains("2 issues to improve");
        assertThat(mrNote).doesNotContain("before merge");
    }
}
