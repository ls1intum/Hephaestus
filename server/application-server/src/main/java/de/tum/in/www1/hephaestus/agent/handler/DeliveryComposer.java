package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;

/**
 * Composes delivery content (mrNote + diffNotes) from structured findings.
 *
 * <p>Server-side "step 2" of the two-step architecture: agent produces findings,
 * server renders them into a human-readable MR/PR comment for students.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Inline-first: every finding with a file location becomes a diff note</li>
 *   <li>MR summary only has non-inlinable findings (e.g. MR description quality, commit discipline)
 *       plus a compact overview of what was posted inline</li>
 *   <li>Natural, conversational tone — reads like a human code reviewer</li>
 *   <li>No bracket severity labels — emoji conveys urgency</li>
 * </ul>
 */
class DeliveryComposer {

    /** Min confidence to name a positive practice in the opening line. */
    static final float POSITIVE_CONFIDENCE_FLOOR = 0.90f;

    /** Max positives to name in the opening (more than 2 creates a run-on). */
    static final int MAX_NAMED_POSITIVES = 2;

    /** Practices that are inherently non-inlinable (no file-level location). */
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of("mr-description-quality", "commit-discipline");

    /** Paths that are internal workspace artifacts, not student code. */
    private static final List<String> INTERNAL_PATH_PREFIXES = List.of(
        ".context/",
        ".practices/",
        ".analysis/",
        ".claude/"
    );

    /**
     * Compose delivery content from validated findings.
     *
     * @param findings validated findings (may include POSITIVE, NEGATIVE, and NOT_APPLICABLE)
     * @return delivery content with mrNote and diffNotes, or null if findings list is empty
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }

        List<ValidatedFinding> negatives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.NEGATIVE)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        // Only name high-confidence positives in the opening
        List<ValidatedFinding> positives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.POSITIVE && f.confidence() >= POSITIVE_CONFIDENCE_FLOOR)
            .toList();

        // All positive/not-applicable → post an approval comment
        if (negatives.isEmpty()) {
            return new DeliveryContent(composeAllPositiveNote(positives), List.of());
        }

        // Partition negatives: inlinable (has valid file location) vs non-inlinable
        List<ValidatedFinding> inlinable = new ArrayList<>();
        List<ValidatedFinding> nonInlinable = new ArrayList<>();
        for (ValidatedFinding f : negatives) {
            if (isNonInlinable(f)) {
                nonInlinable.add(f);
            } else {
                inlinable.add(f);
            }
        }

        // MR summary note: opening + non-inlinable findings expanded + brief inline overview
        String mrNote = composeMrNote(positives, negatives, nonInlinable, inlinable);

        // Diff notes: ALL inlinable negatives get inline comments
        List<DiffNote> diffNotes = collectDiffNotes(inlinable);

        return new DeliveryContent(mrNote, diffNotes);
    }

    /**
     * Compose an approval note when all findings are positive. Uses the agent's actual
     * per-finding reasoning to build a specific, contextual summary rather than a generic template.
     */
    private static String composeAllPositiveNote(List<ValidatedFinding> positives) {
        var sb = new StringBuilder(1024);
        sb.append("\u2705 "); // ✅

        if (positives.isEmpty()) {
            sb.append("No issues found in this review.\n");
            return sb.toString();
        }

        // Collect findings that have actual reasoning to build a natural summary
        List<ValidatedFinding> withReasoning = positives
            .stream()
            .filter(f -> f.reasoning() != null && !f.reasoning().isBlank())
            .toList();

        if (withReasoning.isEmpty()) {
            // Fallback: no reasoning available, name the practices
            List<String> namedPositives = positives
                .stream()
                .limit(MAX_NAMED_POSITIVES)
                .map(f -> humanizePracticeSlug(f.practiceSlug()))
                .toList();
            sb.append("No issues found — the ").append(joinNatural(namedPositives));
            sb.append(positives.size() == 1 ? " looks good.\n" : " look good.\n");
            return sb.toString();
        }

        // Build a contextual summary using the agent's own observations
        sb.append("**No issues found.** Here's what stood out:\n\n");
        int shown = 0;
        for (ValidatedFinding f : withReasoning) {
            if (shown >= 4) break; // Cap at 4 to avoid wall of text
            String label = humanizePracticeSlug(f.practiceSlug());
            String reasoning = f.reasoning().strip();
            // Truncate long reasoning to first sentence or 200 chars
            String summary = truncateToFirstSentence(reasoning, 200);
            sb.append("- **").append(capitalize(label)).append(":** ").append(summary).append("\n");
            shown++;
        }
        sb.append("\n");

        return sb.toString();
    }

    /** Truncate text to the first sentence or maxLen chars, whichever is shorter. */
    private static String truncateToFirstSentence(String text, int maxLen) {
        // Find first sentence-ending punctuation followed by space or end
        int end = -1;
        for (int i = 0; i < Math.min(text.length(), maxLen); i++) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 >= text.length() || text.charAt(i + 1) == ' ')) {
                end = i + 1;
                break;
            }
        }
        if (end > 0 && end <= maxLen) {
            return text.substring(0, end);
        }
        if (text.length() <= maxLen) {
            return text;
        }
        // Truncate at word boundary
        int space = text.lastIndexOf(' ', maxLen);
        if (space > maxLen / 2) {
            return text.substring(0, space) + "...";
        }
        return text.substring(0, maxLen) + "...";
    }

    /** Capitalize the first character of a string. */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Check whether a finding is non-inlinable (belongs in MR summary, not a diff note).
     * Non-inlinable if: practice is inherently non-inlinable, OR finding has no valid file location.
     */
    private static boolean isNonInlinable(ValidatedFinding f) {
        if (NON_INLINABLE_PRACTICES.contains(f.practiceSlug())) {
            return true;
        }
        // Check if there's a valid file location in evidence
        String location = extractPrimaryLocation(f);
        return location == null || isInternalPath(location);
    }

    /**
     * Compose the MR note. Structure:
     * 1. Opening praise line + issue counts
     * 2. Non-inlinable findings (full detail, with separators)
     * 3. Brief overview of inline findings (just title + severity, no full content)
     */
    static String composeMrNote(
        List<ValidatedFinding> positives,
        List<ValidatedFinding> allNegatives,
        List<ValidatedFinding> nonInlinable,
        List<ValidatedFinding> inlinable
    ) {
        var sb = new StringBuilder(4096);

        // Opening: praise + issue summary
        composeOpening(sb, positives, allNegatives);

        // Non-inlinable findings (full detail) — these only exist in the summary
        for (int i = 0; i < nonInlinable.size(); i++) {
            composeFinding(sb, nonInlinable.get(i));
            if (i < nonInlinable.size() - 1 || !inlinable.isEmpty()) {
                sb.append("---\n\n");
            }
        }

        // Inline findings — compact list (title + location only, detail is on the diff)
        if (!inlinable.isEmpty()) {
            if (!nonInlinable.isEmpty()) {
                sb.append("**Inline comments on the diff:**\n\n");
            }
            for (ValidatedFinding f : inlinable) {
                String emoji = severityEmoji(f.severity());
                sb.append(emoji).append(" **").append(f.title()).append("**");
                String location = extractPrimaryLocation(f);
                if (location != null && !isInternalPath(location)) {
                    sb.append(" · `").append(location).append("`");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void composeOpening(
        StringBuilder sb,
        List<ValidatedFinding> positives,
        List<ValidatedFinding> negatives
    ) {
        // Name up to MAX_NAMED_POSITIVES specific positive practices
        if (!positives.isEmpty()) {
            List<String> namedPositives = positives
                .stream()
                .limit(MAX_NAMED_POSITIVES)
                .map(f -> humanizePracticeSlug(f.practiceSlug()))
                .toList();
            sb.append("Nice work on the ").append(joinNatural(namedPositives)).append(". ");
        }

        // Split blocking vs improvement counts
        long blockingCount = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        long improvementCount = negatives.size() - blockingCount;

        if (blockingCount > 0 && improvementCount > 0) {
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(" to fix before merging, plus ")
                .append(improvementCount)
                .append(improvementCount == 1 ? " suggestion" : " suggestions")
                .append(" for improvement:\n\n");
        } else if (blockingCount > 0) {
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(" to fix before merging:\n\n");
        } else {
            sb
                .append(improvementCount)
                .append(improvementCount == 1 ? " suggestion" : " suggestions")
                .append(" for improvement:\n\n");
        }
    }

    private static void composeFinding(StringBuilder sb, ValidatedFinding f) {
        String emoji = severityEmoji(f.severity());

        // Title with location (no [SEVERITY] bracket — emoji is enough)
        sb.append("**").append(emoji).append(" ").append(f.title()).append("**");
        String location = extractPrimaryLocation(f);
        if (location != null && !isInternalPath(location)) {
            sb.append(" · `").append(location).append("`");
        }
        sb.append("\n\n");

        String lang = detectLanguage(f);

        // For CRITICAL/MAJOR: "You wrote:" → reasoning → "Instead:" with fix
        if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
            String snippet = extractPrimarySnippet(f);
            if (snippet != null) {
                sb.append("You wrote:\n");
                sb.append("```").append(lang).append("\n").append(snippet).append("\n```\n\n");
            }

            if (f.reasoning() != null && !f.reasoning().isBlank()) {
                sb.append(f.reasoning()).append("\n\n");
            }

            if (f.guidance() != null && !f.guidance().isBlank()) {
                sb.append(f.guidance()).append("\n\n");
            }
        } else {
            // MINOR/INFO: combine reasoning + guidance naturally
            if (f.reasoning() != null && !f.reasoning().isBlank()) {
                sb.append(f.reasoning()).append("\n\n");
            }
            if (f.guidance() != null && !f.guidance().isBlank()) {
                sb.append(f.guidance()).append("\n\n");
            }
        }
    }

    /** Check if a path is an internal workspace artifact (not student code). */
    private static boolean isInternalPath(String location) {
        // Strip line number suffix for comparison
        String path = location.contains(":") ? location.substring(0, location.lastIndexOf(':')) : location;
        return INTERNAL_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    // ── Helpers ──

    private static final Map<String, String> EXT_TO_LANG = Map.ofEntries(
        Map.entry("swift", "swift"),
        Map.entry("kt", "kotlin"),
        Map.entry("kts", "kotlin"),
        Map.entry("java", "java"),
        Map.entry("py", "python"),
        Map.entry("js", "javascript"),
        Map.entry("ts", "typescript"),
        Map.entry("tsx", "tsx"),
        Map.entry("jsx", "jsx"),
        Map.entry("rb", "ruby"),
        Map.entry("go", "go"),
        Map.entry("rs", "rust"),
        Map.entry("c", "c"),
        Map.entry("cpp", "cpp"),
        Map.entry("h", "c"),
        Map.entry("hpp", "cpp"),
        Map.entry("cs", "csharp"),
        Map.entry("xml", "xml"),
        Map.entry("json", "json"),
        Map.entry("yaml", "yaml"),
        Map.entry("yml", "yaml"),
        Map.entry("md", "markdown"),
        Map.entry("sh", "bash"),
        Map.entry("html", "html"),
        Map.entry("css", "css")
    );

    /** Detect code language from the primary file extension in evidence. */
    private static String detectLanguage(ValidatedFinding f) {
        String location = extractPrimaryLocation(f);
        if (location == null) return "";
        String path = location.contains(":") ? location.substring(0, location.lastIndexOf(':')) : location;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        String ext = path.substring(dot + 1).toLowerCase();
        return EXT_TO_LANG.getOrDefault(ext, "");
    }

    private static String severityEmoji(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "\uD83D\uDD34"; // 🔴
            case MAJOR -> "\uD83D\uDFE0"; // 🟠
            case MINOR -> "\uD83D\uDFE1"; // 🟡
            case INFO -> "\u2139\uFE0F"; // ℹ️
        };
    }

    @Nullable
    private static String extractPrimaryLocation(ValidatedFinding f) {
        JsonNode evidence = f.evidence();
        if (evidence == null || evidence.isNull()) return null;
        JsonNode locations = evidence.get("locations");
        if (locations == null || !locations.isArray() || locations.isEmpty()) return null;
        JsonNode first = locations.get(0);
        if (!first.isObject()) return null;
        JsonNode pathNode = first.get("path");
        if (pathNode == null || !pathNode.isTextual()) return null;
        String path = pathNode.asText();
        JsonNode startLineNode = first.get("startLine");
        if (startLineNode != null && startLineNode.isNumber()) {
            return path + ":" + startLineNode.asInt();
        }
        return path;
    }

    @Nullable
    private static String extractPrimarySnippet(ValidatedFinding f) {
        JsonNode evidence = f.evidence();
        if (evidence == null || evidence.isNull()) return null;
        JsonNode snippets = evidence.get("snippets");
        if (snippets == null || !snippets.isArray() || snippets.isEmpty()) return null;
        String snippet = snippets.get(0).asText();
        return (snippet != null && !snippet.isBlank()) ? snippet.strip() : null;
    }

    /**
     * Maps practice slugs to natural positive labels for the opening line.
     */
    private static final Map<String, String> SLUG_TO_POSITIVE_LABEL = Map.ofEntries(
        Map.entry("hardcoded-secrets", "credential hygiene"),
        Map.entry("fatal-error-crash", "crash avoidance"),
        Map.entry("silent-failure-patterns", "error propagation"),
        Map.entry("error-state-handling", "error state handling"),
        Map.entry("view-decomposition", "view decomposition"),
        Map.entry("view-logic-separation", "separation of concerns"),
        Map.entry("state-ownership-misuse", "state ownership"),
        Map.entry("meaningful-naming", "naming clarity"),
        Map.entry("code-hygiene", "code hygiene"),
        Map.entry("preview-quality", "preview coverage"),
        Map.entry("accessibility-support", "accessibility support"),
        Map.entry("mr-description-quality", "MR documentation"),
        Map.entry("commit-discipline", "commit discipline")
    );

    /** Map a practice slug to a human-friendly positive label. */
    private static String humanizePracticeSlug(String slug) {
        return SLUG_TO_POSITIVE_LABEL.getOrDefault(slug, slug.replace('-', ' '));
    }

    /** Join ["a", "b", "c"] as "a, b, and c". */
    private static String joinNatural(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);
        return (
            items.subList(0, items.size() - 1).stream().collect(Collectors.joining(", ")) +
            ", and " +
            items.get(items.size() - 1)
        );
    }

    /**
     * Collect inline diff notes from NEGATIVE findings.
     * Each finding gets one diff note at its primary location.
     * Body includes severity emoji + title header + guidance (the fix).
     */
    private static List<DiffNote> collectDiffNotes(List<ValidatedFinding> negatives) {
        List<DiffNote> notes = new ArrayList<>();

        for (ValidatedFinding f : negatives) {
            if (notes.size() >= PracticeDetectionResultParser.MAX_DIFF_NOTES) break;

            JsonNode evidence = f.evidence();
            if (evidence == null || evidence.isNull()) continue;
            JsonNode locations = evidence.get("locations");
            if (locations == null || !locations.isArray() || locations.isEmpty()) continue;

            JsonNode loc = locations.get(0);
            if (!loc.isObject()) continue;
            JsonNode pathNode = loc.get("path");
            JsonNode startLineNode = loc.get("startLine");
            if (pathNode == null || !pathNode.isTextual()) continue;
            if (startLineNode == null || !startLineNode.isNumber()) continue;
            int startLine = startLineNode.asInt();
            if (startLine <= 0) continue;

            Integer endLine = null;
            JsonNode endLineNode = loc.get("endLine");
            if (endLineNode != null && endLineNode.isNumber() && endLineNode.asInt() >= startLine) {
                endLine = endLineNode.asInt();
            }

            // Diff note body: emoji title + reasoning + guidance
            String body = composeDiffNoteBody(f);
            if (body != null && !body.isBlank()) {
                notes.add(new DiffNote(pathNode.asText(), startLine, endLine, body));
            }
        }

        return notes;
    }

    /**
     * Compose a diff note body — the full finding content placed inline on the diff.
     * Since the MR summary only has a compact list, the diff note carries the full detail.
     */
    @Nullable
    private static String composeDiffNoteBody(ValidatedFinding f) {
        var sb = new StringBuilder();
        sb.append("**").append(severityEmoji(f.severity())).append(" ").append(f.title()).append("**\n\n");

        if (f.reasoning() != null && !f.reasoning().isBlank()) {
            sb.append(f.reasoning()).append("\n\n");
        }
        if (f.guidance() != null && !f.guidance().isBlank()) {
            sb.append(f.guidance()).append("\n\n");
        }

        String body = sb.toString().strip();
        if (body.length() > PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH) {
            body = body.substring(0, PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH - 3) + "...";
        }
        return body.isBlank() ? null : body;
    }
}
