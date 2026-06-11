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
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
        return compose(findings, WorkArtifact.PULL_REQUEST);
    }

    /**
     * Compose feedback for a specific artifact. The blocking call-to-action is artifact-aware: a PR
     * reads "to fix before merging", an ISSUE simply "to fix" (issues are not merged).
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings, WorkArtifact artifact) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }

        List<ValidatedFinding> negatives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.NEGATIVE)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        // No negatives → an observation note over the POSITIVE findings (see composeNoIssuesNote).
        if (negatives.isEmpty()) {
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

        // POSITIVE findings the same job produced — surfaced as a brief strengths line before the
        // critiques so the note acknowledges effort (task-level, not person-level praise).
        List<ValidatedFinding> positives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.POSITIVE)
            .toList();

        // MR summary note: opening + non-inlinable findings expanded + brief inline overview
        String mrNote = composeMrNote(positives, negatives, nonInlinable, inlinable, artifact);

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
            String summary = truncateToFirstSentence(sanitizeStudentText(f.reasoning()).strip(), 200);
            sb.append("- **").append(label).append(":** ").append(summary).append("\n");
            shown++;
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Short, task-level strength phrases keyed by practice slug — used to acknowledge what the work
     * already does well before listing improvements. Task/process-level by design (never person-level
     * praise). Unknown slugs (custom practices) fall back to a humanised practice name.
     */
    private static final Map<String, String> STRENGTH_PHRASES = Map.ofEntries(
        Map.entry("scope-one-reviewable-change", "keeping the change focused and reviewable"),
        Map.entry("describe-what-and-why", "explaining what changed"),
        Map.entry("ready-and-traceable-handoff", "linking the change to its issue"),
        Map.entry("engaging-with-inline-review-comments", "engaging with the review feedback"),
        Map.entry("issue-states-an-actionable-problem", "stating the problem clearly"),
        Map.entry("issue-scoped-to-single-concern", "keeping the issue scoped to one concern"),
        Map.entry("issue-has-checkable-outcome", "defining a clear, checkable outcome")
    );

    /**
     * Builds a one-sentence strengths acknowledgement from up to two POSITIVE findings, e.g.
     * "Nice work keeping the change focused and reviewable and linking the change to its issue — a
     * couple of things to tighten:". Returns "" when there are no positives. Strictly task-level: it
     * names what the work does, never grades the author.
     */
    static String composeAcknowledgement(List<ValidatedFinding> positives) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        List<String> phrases = positives
            .stream()
            .map(f -> STRENGTH_PHRASES.getOrDefault(f.practiceSlug(), humanisePracticeSlug(f.practiceSlug())))
            .filter(p -> p != null && !p.isBlank())
            .distinct()
            .limit(2)
            .toList();
        if (phrases.isEmpty()) {
            return "";
        }
        String strengths = phrases.size() == 1 ? phrases.get(0) : phrases.get(0) + " and " + phrases.get(1);
        String tail = positives.size() > 1 ? " — a couple of things to tighten:" : " — one thing to tighten:";
        return "Nice work " + strengths + tail;
    }

    /** Fallback strength phrase for a practice not in {@link #STRENGTH_PHRASES}: the slug with dashes as spaces. */
    @Nullable
    private static String humanisePracticeSlug(@Nullable String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return slug.replace('-', ' ');
    }

    /**
     * Marks a sentence as pure grading-mechanics meta — if any of these appears, the whole sentence is
     * the grader explaining the rubric to itself, not feedback to the student, so it is dropped wholesale.
     * Catches the phrasings observed leaking from gpt-oss-120b: "the practice requires…", "for a POSITIVE
     * verdict", "MINOR severity level/band", "acceptable upper band", "according to/violating the
     * practice", "…line threshold".
     */
    private static final Pattern GRADING_SENTENCE = Pattern.compile(
        "(?i)(" +
            "\\bthe\\s+practice\\s+(?:requires|defines|expects|mandates|deems|treats|considers|states)\\b|" +
            "\\b(?:according to|per|under|following|violat\\w+|satisf\\w+|fail\\w*)\\s+the\\s+practice\\b|" +
            "\\b(?:POSITIVE|NEGATIVE|NOT[_ ]APPLICABLE)\\s+(?:verdict|finding|result|rating)\\b|" +
            "\\b(?:for|to|a|an|the)\\s+(?:POSITIVE|NEGATIVE|NOT[_ ]APPLICABLE)\\s+(?:verdict|finding)\\b|" +
            "\\b(?:MINOR|MAJOR|INFO|CRITICAL)\\s+(?:severity|band|bucket|tier)\\b|" +
            "\\bseverity\\s+(?:level|band|bucket|rating)\\b|" +
            "\\b(?:upper|lower|acceptable)\\s+band\\b|" +
            "\\b[≤<=>]*\\s*\\d+[\\s-]*(?:line|file)s?\\s+threshold\\b|" +
            "\\bthreshold\\s+for\\s+a\\s+\\w+\\s+(?:verdict|finding)\\b" +
            ")"
    );

    /**
     * Strips internal grading vocabulary from text headed to a student: split into sentences, drop any
     * that is pure rubric meta ({@link #GRADING_SENTENCE}), then tidy the whitespace the drops leave
     * behind. Idempotent and locale-safe (no naked case folding).
     */
    static String sanitizeStudentText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        // Split on sentence-ending punctuation followed by whitespace so a mid-paragraph rubric sentence
        // is removed wholesale.
        StringBuilder kept = new StringBuilder(text.length());
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (!GRADING_SENTENCE.matcher(sentence).find()) {
                if (kept.length() > 0) {
                    kept.append(' ');
                }
                kept.append(sentence);
            }
        }
        String out = kept.toString();
        // Tidy the gaps the drops leave: doubled spaces, space-before-punctuation, blank lines.
        out = out.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s+([.,;])", "$1").replaceAll("\\n{3,}", "\n\n");
        return out.strip();
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
        List<ValidatedFinding> positives,
        List<ValidatedFinding> allNegatives,
        List<ValidatedFinding> nonInlinable,
        List<ValidatedFinding> inlinable,
        WorkArtifact artifact
    ) {
        var sb = new StringBuilder(4096);

        // Strengths first: name 1-2 things the work already does well (task-level acknowledgement)
        // before the critiques, so a suggestions-only note is never deficit-only when the job also
        // found strengths. Suppressed when there is a blocking (CRITICAL/MAJOR) issue: front-loading
        // praise ahead of a serious problem reads as a hollow "feedback sandwich" and dilutes the
        // message. Task/process-level only (Hattie & Timperley) — never person-level praise.
        boolean hasBlocking = allNegatives
            .stream()
            .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR);
        if (!hasBlocking) {
            String acknowledgement = composeAcknowledgement(positives);
            if (!acknowledgement.isEmpty()) {
                sb.append(acknowledgement).append("\n\n");
            }
        }

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

    private static void composeOpening(StringBuilder sb, List<ValidatedFinding> negatives, WorkArtifact artifact) {
        // PRs are merged → "to fix before merging"; issues are not → "to fix".
        String blockingCta = artifact == WorkArtifact.PULL_REQUEST ? " to fix before merging" : " to fix";
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
                boolean hasCodeLocation = location != null && !isInternalPath(location);
                if (hasCodeLocation) {
                    // Real code reference → fenced code block.
                    sb.append("You wrote:\n");
                    sb.append("```").append(lang).append("\n").append(snippet).append("\n```\n\n");
                } else {
                    // Metadata field (e.g. a title/body span) → plain inline quote, never a ```json fence.
                    sb.append("You wrote: “").append(metadataSnippetText(snippet)).append("”\n\n");
                }
            }

            appendStudentText(sb, f.reasoning());
            appendStudentText(sb, f.guidance());
        } else {
            // MINOR/INFO: combine reasoning + guidance naturally
            appendStudentText(sb, f.reasoning());
            appendStudentText(sb, f.guidance());
        }
    }

    /** Appends sanitised student-facing text (reasoning/guidance) if non-blank after the scrub. */
    private static void appendStudentText(StringBuilder sb, @Nullable String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String clean = sanitizeStudentText(text);
        if (!clean.isBlank()) {
            sb.append(clean).append("\n\n");
        }
    }

    /**
     * Renders a metadata snippet as plain inline text: unwraps a {@code "field" : "value"} JSON span to
     * just the value, collapses whitespace, and caps the length so the quote stays readable.
     */
    private static String metadataSnippetText(String snippet) {
        String s = snippet.strip();
        var m = Pattern.compile("\"[^\"]+\"\\s*:\\s*\"(.*)\"\\s*$", Pattern.DOTALL).matcher(s);
        if (m.find()) {
            s = m.group(1);
        }
        s = s.replace("\\n", " ").replace("\\\"", "\"").replaceAll("\\s+", " ").strip();
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
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

        appendStudentText(sb, f.reasoning());
        appendStudentText(sb, f.guidance());

        String body = sb.toString().strip();
        if (body.length() > PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH) {
            body = body.substring(0, PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH - 3) + "...";
        }
        return body.isBlank() ? null : body;
    }
}
