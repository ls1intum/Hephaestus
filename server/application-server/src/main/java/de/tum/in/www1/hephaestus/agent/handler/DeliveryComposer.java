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
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;

/**
 * Composes delivery content (mrNote + diffNotes) from structured findings.
 *
 * <p>Server-side "step 2" of the two-step architecture: agent produces findings,
 * server renders them into a human-readable MR/PR comment.
 *
 * <p>Design principles (from PE analysis):
 * <ul>
 *   <li>Show defective code before the fix ("You wrote: ... → Fix: ...")</li>
 *   <li>Cap MR note at 5 findings (rest go to inline diff notes only)</li>
 *   <li>Name specific positive practices in the opening, not just a count</li>
 *   <li>CRITICAL/MAJOR = merge-blocking language; MINOR/INFO = improvement suggestions</li>
 *   <li>No collapsible sections — every shown finding is worth reading</li>
 * </ul>
 */
class DeliveryComposer {

    /** Max findings shown in the MR summary note. More than 5 causes cognitive overload. */
    static final int MAX_MR_NOTE_FINDINGS = 5;

    /** Min confidence to name a positive practice in the opening line. */
    static final float POSITIVE_CONFIDENCE_FLOOR = 0.90f;

    /**
     * Compose delivery content from validated findings.
     *
     * @param findings validated findings (may include POSITIVE and NEGATIVE)
     * @return delivery content with mrNote and diffNotes, or null if all findings are POSITIVE
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }

        List<ValidatedFinding> negatives = findings.stream()
            .filter(f -> f.verdict() == Verdict.NEGATIVE)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        // All positive → silence = approval, no comment posted
        if (negatives.isEmpty()) {
            return null;
        }

        // Only name high-confidence positives in the opening (low-confidence = not sure enough)
        List<ValidatedFinding> positives = findings.stream()
            .filter(f -> f.verdict() == Verdict.POSITIVE && f.confidence() >= POSITIVE_CONFIDENCE_FLOOR)
            .toList();

        // MR note: top N findings
        List<ValidatedFinding> noteFindings = negatives.size() > MAX_MR_NOTE_FINDINGS
            ? negatives.subList(0, MAX_MR_NOTE_FINDINGS)
            : negatives;

        String mrNote = composeMrNote(positives, negatives, noteFindings);

        // Diff notes: ALL negatives get inline comments (not capped to 5)
        List<DiffNote> diffNotes = collectDiffNotes(negatives);

        return new DeliveryContent(mrNote, diffNotes);
    }

    /**
     * Compose the MR note. Structure:
     * <pre>
     * [Named positive observation]. N issues to address [before merge / for improvement].
     *
     * **🔴 [CRITICAL] Title** · `File:line`
     * You wrote:
     * ```{lang}
     * defective code
     * ```
     * reasoning...
     * guidance (fix)...
     *
     * **🟠 [MAJOR] Title** · `File:line`
     * ...
     * </pre>
     */
    static String composeMrNote(
        List<ValidatedFinding> positives,
        List<ValidatedFinding> allNegatives,
        List<ValidatedFinding> shownNegatives
    ) {
        var sb = new StringBuilder(4096);

        // Opening: name specific positive practices
        composeOpening(sb, positives, allNegatives);

        // Shown findings (severity-ordered, capped)
        for (ValidatedFinding f : shownNegatives) {
            composeFinding(sb, f);
        }

        // If we capped, mention the overflow
        if (allNegatives.size() > shownNegatives.size()) {
            int overflow = allNegatives.size() - shownNegatives.size();
            sb.append("*Plus ").append(overflow)
                .append(" more issue").append(overflow > 1 ? "s" : "")
                .append(" noted as inline comments on the diff.*\n\n");
        }

        // Provenance footer
        sb.append("---\n<sub>Hephaestus \u2014 automated practice review</sub>\n");

        return sb.toString();
    }

    private static void composeOpening(
        StringBuilder sb,
        List<ValidatedFinding> positives,
        List<ValidatedFinding> negatives
    ) {
        // Name up to 3 specific positive practices
        if (!positives.isEmpty()) {
            List<String> namedPositives = positives.stream()
                .limit(3)
                .map(f -> humanizePracticeSlug(f.practiceSlug()))
                .toList();
            sb.append("Good ").append(joinNatural(namedPositives)).append(". ");
        }

        // Issue count with appropriate urgency
        boolean hasCriticalOrMajor = negatives.stream()
            .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR);

        sb.append("Here ").append(negatives.size() == 1 ? "is " : "are ")
            .append(negatives.size())
            .append(" issue").append(negatives.size() > 1 ? "s" : "")
            .append(hasCriticalOrMajor ? " to address before merge" : " to improve")
            .append(".\n\n");
    }

    private static void composeFinding(StringBuilder sb, ValidatedFinding f) {
        String emoji = severityEmoji(f.severity());
        String severityLabel = f.severity().name();

        // Title with location
        sb.append("**").append(emoji).append(" [").append(severityLabel).append("] ")
            .append(f.title()).append("**");
        String location = extractPrimaryLocation(f);
        if (location != null) {
            sb.append(" · `").append(location).append("`");
        }
        sb.append("\n\n");

        String lang = detectLanguage(f);

        // For CRITICAL/MAJOR: show defective code, then reasoning, then fix
        if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
            // Defective code snippet from evidence
            String snippet = extractPrimarySnippet(f);
            if (snippet != null) {
                sb.append("```").append(lang).append("\n").append(snippet).append("\n```\n\n");
            }

            // Reasoning (what's wrong and why it matters)
            if (f.reasoning() != null && !f.reasoning().isBlank()) {
                sb.append(f.reasoning()).append("\n\n");
            }

            // Guidance (the fix)
            if (f.guidance() != null && !f.guidance().isBlank()) {
                sb.append(f.guidance()).append("\n\n");
            }
        } else {
            // MINOR/INFO: reasoning + guidance (both matter for learning)
            if (f.reasoning() != null && !f.reasoning().isBlank()) {
                sb.append(f.reasoning()).append("\n\n");
            }
            if (f.guidance() != null && !f.guidance().isBlank()) {
                sb.append(f.guidance()).append("\n\n");
            }
        }
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
        // Strip line number suffix (e.g., "Foo.swift:42" → "Foo.swift")
        String path = location.contains(":") ? location.substring(0, location.lastIndexOf(':')) : location;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        String ext = path.substring(dot + 1).toLowerCase();
        return EXT_TO_LANG.getOrDefault(ext, "");
    }

    private static String severityEmoji(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "\uD83D\uDD34"; // 🔴
            case MAJOR -> "\uD83D\uDFE0";    // 🟠
            case MINOR -> "\uD83D\uDFE1";    // 🟡
            case INFO -> "\u2139\uFE0F";      // ℹ️
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
     * "Good crash avoidance" reads much better than "Good fatal error crash".
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
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);
        return items.subList(0, items.size() - 1).stream()
            .collect(Collectors.joining(", "))
            + ", and " + items.get(items.size() - 1);
    }

    /**
     * Collect inline diff notes from NEGATIVE findings.
     * Each finding gets one diff note at its primary location.
     * Body = guidance (the fix), not reasoning (the diagnosis).
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

            // Diff note body: title + guidance (compact)
            String body = composeDiffNoteBody(f);
            if (body != null && !body.isBlank()) {
                notes.add(new DiffNote(pathNode.asText(), startLine, endLine, body));
            }
        }

        return notes;
    }

    @Nullable
    private static String composeDiffNoteBody(ValidatedFinding f) {
        var sb = new StringBuilder();
        sb.append("**").append(severityEmoji(f.severity())).append(" ").append(f.title()).append("**\n\n");

        if (f.guidance() != null && !f.guidance().isBlank()) {
            sb.append(f.guidance());
        } else if (f.reasoning() != null && !f.reasoning().isBlank()) {
            sb.append(f.reasoning());
        }

        String body = sb.toString();
        if (body.length() > PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH) {
            body = body.substring(0, PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH - 3) + "...";
        }
        return body;
    }
}
