package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.ANALYSIS_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.CONTEXT_TARGET_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PI_AGENT_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRACTICES_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_OUT_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_PREFIX;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.FocusArtifact;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

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

    /** Practices that are inherently non-inlinable (no file-level location). */
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of("mr-description-quality", "commit-discipline");

    /** Paths that are internal workspace artifacts, not student code. */
    private static final List<String> INTERNAL_PATH_PREFIXES = List.of(
        CONTEXT_TARGET_PREFIX,
        PRACTICES_PREFIX,
        ANALYSIS_PREFIX,
        PI_AGENT_PREFIX,
        PRECOMPUTE_PREFIX,
        PRECOMPUTE_OUT_PREFIX
    );

    /**
     * Compose delivery content from validated findings.
     *
     * @param findings validated findings (may include POSITIVE, NEGATIVE, and NOT_APPLICABLE)
     * @return delivery content with mrNote and diffNotes, or null if findings list is empty
     */
    /** Compose for a pull request (the default artifact; CTA reads "to fix before merging"). */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings) {
        return compose(findings, FocusArtifact.PULL_REQUEST);
    }

    /**
     * Compose feedback for a specific artifact. The blocking call-to-action is artifact-aware: a PR
     * reads "to fix before merging", an ISSUE simply "to fix" (issues are not merged).
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings, FocusArtifact artifact) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }

        List<ValidatedFinding> negatives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.NEGATIVE)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        // Nothing to change → an observation note (no self-level praise; see composeNoIssuesNote)
        if (negatives.isEmpty()) {
            // Defense-in-depth tripwire: a hardcoded-secrets finding that is neither POSITIVE nor
            // NOT_APPLICABLE is by definition a NEGATIVE and would already be in `negatives` — so it
            // can never legitimately reach the green path. If a future refactor ever routes a secret
            // finding here, fail loud rather than post a clean bill of health over a committed key.
            boolean leakedSecret = findings
                .stream()
                .anyMatch(
                    f ->
                        "hardcoded-secrets".equals(f.practiceSlug()) &&
                        f.verdict() != Verdict.POSITIVE &&
                        f.verdict() != Verdict.NOT_APPLICABLE
                );
            if (leakedSecret) {
                throw new IllegalStateException(
                    "Refusing to compose an all-clear comment: a non-positive hardcoded-secrets finding " +
                        "was present but did not register as NEGATIVE. This is a delivery-integrity bug."
                );
            }
            List<ValidatedFinding> observed = findings
                .stream()
                .filter(f -> f.verdict() == Verdict.POSITIVE)
                .toList();
            return new DeliveryContent(composeNoIssuesNote(observed), List.of());
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
        String mrNote = composeMrNote(negatives, nonInlinable, inlinable, artifact);

        // Diff notes: ALL inlinable negatives get inline comments
        List<DiffNote> diffNotes = collectDiffNotes(inlinable);

        return new DeliveryContent(mrNote, diffNotes);
    }

    /**
     * Compose the note posted when no issues were found. Reports what was reviewed and, where the
     * agent recorded reasoning, what it observed against each practice — evidence-anchored, on the
     * work. Deliberately carries NO self-level praise: person-directed praise is the least effective
     * feedback level (Hattie &amp; Timperley, The Power of Feedback), so the mentoring stance keeps
     * feedback at the task/process level.
     */
    private static String composeNoIssuesNote(List<ValidatedFinding> observed) {
        // Findings whose reasoning lets us cite a concrete observation rather than a bare pass.
        List<ValidatedFinding> withReasoning = observed
            .stream()
            .filter(f -> f.reasoning() != null && !f.reasoning().isBlank())
            .toList();

        if (withReasoning.isEmpty()) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }

        var sb = new StringBuilder(1024);
        sb.append("Reviewed against the active practices. What I observed:\n\n");
        int shown = 0;
        for (ValidatedFinding f : withReasoning) {
            if (shown >= 4) break; // Cap at 4 to avoid a wall of text
            String label = capitalize(f.practiceSlug().replace('-', ' '));
            String summary = truncateToFirstSentence(f.reasoning().strip(), 200);
            sb.append("- **").append(label).append(":** ").append(summary).append("\n");
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
     * Non-inlinable if: practice is inherently non-inlinable, OR finding has neither a
     * usable evidence location nor an agent-supplied {@code suggestedDiffNote}.
     */
    private static boolean isNonInlinable(ValidatedFinding f) {
        if (NON_INLINABLE_PRACTICES.contains(f.practiceSlug())) {
            return true;
        }
        if (!f.suggestedDiffNotes().isEmpty()) {
            return false;
        }
        String location = extractPrimaryLocation(f);
        return location == null || isInternalPath(location);
    }

    /**
     * Compose the MR note. Structure:
     * 1. Opening issue/suggestion counts (evidence-anchored, no praise)
     * 2. Non-inlinable findings (full detail, with separators)
     * 3. Brief overview of inline findings (just title + severity, no full content)
     */
    static String composeMrNote(
        List<ValidatedFinding> allNegatives,
        List<ValidatedFinding> nonInlinable,
        List<ValidatedFinding> inlinable,
        FocusArtifact artifact
    ) {
        var sb = new StringBuilder(4096);

        // Opening: evidence-anchored issue summary (no self-level praise)
        composeOpening(sb, allNegatives, artifact);

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

    private static void composeOpening(StringBuilder sb, List<ValidatedFinding> negatives, FocusArtifact artifact) {
        // PRs are merged → "to fix before merging"; issues are not → "to fix".
        String blockingCta = artifact == FocusArtifact.PULL_REQUEST ? " to fix before merging" : " to fix";
        long blockingCount = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        long improvementCount = negatives.size() - blockingCount;

        if (blockingCount > 0 && improvementCount > 0) {
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(blockingCta)
                .append(", plus ")
                .append(improvementCount)
                .append(improvementCount == 1 ? " suggestion" : " suggestions")
                .append(" for improvement:\n\n");
        } else if (blockingCount > 0) {
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(blockingCta)
                .append(":\n\n");
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

    // Helpers

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
        if (pathNode == null || !pathNode.isString()) return null;
        String path = pathNode.asString();
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
        String snippet = snippets.get(0).asString();
        return (snippet != null && !snippet.isBlank()) ? snippet.strip() : null;
    }

    /**
     * Collect inline diff notes from NEGATIVE findings.
     *
     * <p>Prefer the agent's per-finding {@code suggestedDiffNotes} (richer, explicit lines/body).
     * Fall back to a synthesized note from the first evidence location + composed body when the
     * agent did not supply one.
     */
    private static List<DiffNote> collectDiffNotes(List<ValidatedFinding> negatives) {
        List<DiffNote> notes = new ArrayList<>();

        for (ValidatedFinding f : negatives) {
            if (notes.size() >= PracticeDetectionResultParser.MAX_DELIVERY_DIFF_NOTES) break;

            // Prefer agent-supplied suggestedDiffNotes
            if (!f.suggestedDiffNotes().isEmpty()) {
                for (DiffNote note : f.suggestedDiffNotes()) {
                    if (notes.size() >= PracticeDetectionResultParser.MAX_DELIVERY_DIFF_NOTES) break;
                    notes.add(note);
                }
                continue;
            }

            // Fallback: synthesize from evidence.locations[0]
            JsonNode evidence = f.evidence();
            if (evidence == null || evidence.isNull()) continue;
            JsonNode locations = evidence.get("locations");
            if (locations == null || !locations.isArray() || locations.isEmpty()) continue;

            JsonNode loc = locations.get(0);
            if (!loc.isObject()) continue;
            JsonNode pathNode = loc.get("path");
            JsonNode startLineNode = loc.get("startLine");
            if (pathNode == null || !pathNode.isString()) continue;
            if (startLineNode == null || !startLineNode.isNumber()) continue;
            int startLine = startLineNode.asInt();
            if (startLine <= 0) continue;

            Integer endLine = null;
            JsonNode endLineNode = loc.get("endLine");
            if (endLineNode != null && endLineNode.isNumber() && endLineNode.asInt() >= startLine) {
                endLine = endLineNode.asInt();
            }

            String body = composeDiffNoteBody(f);
            if (body != null && !body.isBlank()) {
                notes.add(new DiffNote(pathNode.asString(), startLine, endLine, body));
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
