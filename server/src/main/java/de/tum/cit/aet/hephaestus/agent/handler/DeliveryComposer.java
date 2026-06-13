package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.ANALYSIS_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.CONTEXT_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PI_AGENT_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRACTICES_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_OUT_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.PRECOMPUTE_PREFIX;
import static de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi.REPO_MOUNT_RELATIVE;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    /**
     * How many non-blocking (MINOR/INFO) improvement suggestions we surface in full before collapsing
     * the rest into an honest overflow line. The mentor usefulness bar is ~1-3 highest-leverage lessons
     * per artifact; every blocking (CRITICAL/MAJOR) issue is ALWAYS kept on top of this, so a PR with two
     * blockers plus this cap still lands the few things that actually change the next MR rather than a
     * 10-item pile-on. Tuned so blocking + cap stays within the small-handful bar. NEVER caps blocking.
     */
    static final int MAX_IMPROVEMENT_SUGGESTIONS = 3;

    /** Practices that are inherently non-inlinable (no file-level location). */
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of("mr-description-quality", "commit-discipline");

    /**
     * Issue-structure practices that all express the same underlying lesson on an epic — "give this
     * large issue trackable structure". When two or more fire NEGATIVE on the SAME issue they stack
     * near-duplicate "this epic needs structure" bullets, so on an ISSUE artifact we keep only the
     * highest-severity one (F4). Scoped narrowly to this set so distinct lessons are never collapsed.
     */
    private static final Set<String> EPIC_STRUCTURE_PRACTICES = Set.of(
        "issue-scoped-to-single-concern",
        "issue-has-checkable-outcome",
        "breaks-large-work-into-trackable-subtasks"
    );

    /**
     * Process-level practices whose POSITIVE is a named good ACT (engaging with review, revealing
     * intent) rather than a code-correctness claim. Only these may surface as the single subordinate
     * reinforcement line when blocking issues exist (F5) — so a correctness positive can never leak
     * into a blocking note.
     */
    private static final Set<String> PROCESS_POSITIVE_PRACTICES = Set.of(
        "engaging-with-inline-review-comments",
        "acting-on-review-feedback",
        "intent-revealing-comments"
    );

    /**
     * Strips the leading repo-mount prefix so a student-facing location stays repo-relative (F3). The
     * repo mounts at the integration-namespaced {@code inputs/worktrees/scm/repo/} (ADR 0020).
     */
    private static String repoRelative(String path) {
        return path.startsWith(REPO_MOUNT_RELATIVE) ? path.substring(REPO_MOUNT_RELATIVE.length()) : path;
    }

    /** Paths that are internal workspace artifacts, not student code. */
    private static final List<String> INTERNAL_PATH_PREFIXES = List.of(
        CONTEXT_PREFIX,
        PRACTICES_PREFIX,
        ANALYSIS_PREFIX,
        PI_AGENT_PREFIX,
        PRECOMPUTE_PREFIX,
        PRECOMPUTE_OUT_PREFIX
    );

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

        // F4: on an epic ISSUE the issue-structure detectors (scoped/checkable/subtasks) all say the
        // same thing — "this epic needs trackable structure". Collapse them to the single highest-
        // severity one so the student gets one clear lesson, not 3-4 stacked near-duplicate bullets.
        // Severity-sorted above (CRITICAL ordinal 0 first), so the first epic-structure finding seen is
        // the lead we keep; later ones are the redundant siblings we drop. Conservative: ISSUE-only,
        // and only within EPIC_STRUCTURE_PRACTICES, so distinct lessons are never merged.
        if (artifact == WorkArtifact.ISSUE) {
            negatives = dedupEpicStructure(negatives);
        }

        // PRIORITISE + CAP THE LONG TAIL. Detection legitimately fires many low-value MINOR/INFO nudges;
        // surfacing all of them buries the 1-3 highest-leverage lessons under a pile-on. Keep EVERY
        // blocking (CRITICAL/MAJOR) finding — those must never be silently dropped — then keep only the
        // top MAX_IMPROVEMENT_SUGGESTIONS non-blocking ones (already severity-sorted; ties broken by
        // confidence so the most-certain nudge wins), and remember how many we collapsed so the opening
        // can own it honestly with a "+N more minor suggestions" line. The capped list — not the raw one —
        // is what flows into the inline/summary partition and the diff notes below, so a dropped nudge
        // leaves no inline comment either.
        int improvementOverflow = 0;
        long blockingTotal = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        long improvementTotal = negatives.size() - blockingTotal;
        if (improvementTotal > MAX_IMPROVEMENT_SUGGESTIONS) {
            negatives = capImprovementTail(negatives);
            improvementOverflow = (int) (improvementTotal - MAX_IMPROVEMENT_SUGGESTIONS);
        }

        // No negatives → an observation note over the POSITIVE findings (see composeNoIssuesNote).
        if (negatives.isEmpty()) {
            List<ValidatedFinding> observed = findings
                .stream()
                .filter(f -> f.verdict() == Verdict.POSITIVE)
                .toList();
            if (observed.isEmpty()) {
                // Every finding abstained (all NOT_APPLICABLE): the artifact could not be assessed against
                // any active practice, so deliver nothing rather than a misleading "nothing to change here"
                // all-clear on work that was never actually evaluated.
                return null;
            }
            return new DeliveryContent(composeNoIssuesNote(observed), List.of());
        }

        // Partition negatives: inlinable (a diff note) vs non-inlinable (expanded in the summary).
        // Issues carry no diff, so a positional note can never be posted on them — every issue finding
        // must be expanded in full in the issue note itself rather than demoted to a diff note that
        // silently vanishes, leaving the student a bare title with no reasoning or guidance.
        boolean inlineSupported = artifact == WorkArtifact.PULL_REQUEST;
        List<ValidatedFinding> inlinable = new ArrayList<>();
        List<ValidatedFinding> nonInlinable = new ArrayList<>();
        for (ValidatedFinding f : negatives) {
            if (inlineSupported && !isNonInlinable(f)) {
                inlinable.add(f);
            } else {
                nonInlinable.add(f);
            }
        }

        // POSITIVE findings the same job produced — surfaced as a brief strengths line before the
        // critiques so the note acknowledges effort (task-level, not person-level praise).
        List<ValidatedFinding> positives = findings
            .stream()
            .filter(f -> f.verdict() == Verdict.POSITIVE)
            .toList();

        // MR summary note: opening + non-inlinable findings expanded + brief inline overview
        String mrNote = composeMrNote(positives, negatives, nonInlinable, inlinable, artifact, improvementOverflow);

        // Diff notes: ALL inlinable negatives get inline comments
        List<DiffNote> diffNotes = collectDiffNotes(inlinable);

        return new DeliveryContent(mrNote, diffNotes);
    }

    /**
     * Collapses overlapping epic issue-structure NEGATIVE findings (F4). Keeps the FIRST
     * {@link #EPIC_STRUCTURE_PRACTICES} finding encountered (the list is severity-sorted, so that is the
     * highest-severity lead) and drops the rest; every non-epic-structure finding passes through
     * untouched and in order. No-op when fewer than two epic-structure findings are present.
     */
    private static List<ValidatedFinding> dedupEpicStructure(List<ValidatedFinding> negatives) {
        long epicCount = negatives
            .stream()
            .filter(f -> EPIC_STRUCTURE_PRACTICES.contains(f.practiceSlug()))
            .count();
        if (epicCount < 2) {
            return negatives;
        }
        List<ValidatedFinding> kept = new ArrayList<>(negatives.size());
        boolean epicKept = false;
        for (ValidatedFinding f : negatives) {
            if (EPIC_STRUCTURE_PRACTICES.contains(f.practiceSlug())) {
                if (epicKept) {
                    continue; // redundant sibling — same epic-structure lesson as the lead already kept
                }
                epicKept = true;
            }
            kept.add(f);
        }
        return kept;
    }

    /**
     * Caps the non-blocking (MINOR/INFO) improvement tail to {@link #MAX_IMPROVEMENT_SUGGESTIONS}. EVERY
     * blocking (CRITICAL/MAJOR) finding is kept — blocking is never capped. Among the non-blocking
     * findings the kept ones are the highest-severity, then highest-confidence (most certain nudge wins a
     * tie); the rest are collapsed into the overflow count the caller renders. The returned list preserves
     * the incoming severity ordering so the existing lead-with-blocking layout is untouched. Caller only
     * invokes this when the non-blocking count actually exceeds the cap.
     */
    private static List<ValidatedFinding> capImprovementTail(List<ValidatedFinding> negatives) {
        List<ValidatedFinding> blocking = new ArrayList<>();
        List<ValidatedFinding> improvements = new ArrayList<>();
        for (ValidatedFinding f : negatives) {
            if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
                blocking.add(f);
            } else {
                improvements.add(f);
            }
        }
        // Pick the few highest-leverage improvements: severity (MINOR before INFO) then confidence desc.
        // Collect by reference IDENTITY (not value-equality): ValidatedFinding is a record, so two findings
        // with identical content are equal — a value-set would collapse them into one slot, letting the
        // order-preserving re-emit below match BOTH and overshoot the cap. Identity keeps exactly the
        // limit()-selected instances.
        Set<ValidatedFinding> keptImprovements = improvements
            .stream()
            .sorted(
                Comparator.comparingInt((ValidatedFinding f) -> f.severity().ordinal()).thenComparing(
                    Comparator.comparingDouble(ValidatedFinding::confidence).reversed()
                )
            )
            .limit(MAX_IMPROVEMENT_SUGGESTIONS)
            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

        // Re-emit in the original (severity-sorted) order, dropping the improvements that did not survive.
        List<ValidatedFinding> kept = new ArrayList<>(blocking.size() + keptImprovements.size());
        for (ValidatedFinding f : negatives) {
            if (blocking.contains(f) || keptImprovements.contains(f)) {
                kept.add(f);
            }
        }
        return kept;
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

        var bullets = new StringBuilder(1024);
        int shown = 0;
        for (ValidatedFinding f : withReasoning) {
            if (shown >= 4) break; // Cap at 4 to avoid a wall of text
            String summary = truncateToFirstSentence(sanitizeStudentText(f.reasoning()).strip(), 200);
            if (summary.isBlank()) {
                // The reasoning was entirely grading-meta and scrubbed to nothing — skip it rather than
                // emit a bare "- **Practice:** " bullet with no observation behind it.
                continue;
            }
            String label = capitalize(f.practiceSlug().replace('-', ' '));
            bullets.append("- **").append(label).append(":** ").append(summary).append("\n");
            shown++;
        }
        if (shown == 0) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }
        return "Reviewed against the active practices. What I observed:\n\n" + bullets + "\n";
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
    static String composeAcknowledgement(List<ValidatedFinding> positives, int improvementCount) {
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
        // The lead-in counts the IMPROVEMENTS that follow, not the strengths named — otherwise a single
        // strength in front of two suggestions reads "one thing to tighten:" above a list of two.
        String tail = improvementCount > 1 ? " — a couple of things to tighten:" : " — one thing to tighten:";
        return "Nice work " + strengths + tail;
    }

    /**
     * Builds the single subordinate process-positive line allowed alongside blocking issues (F5).
     * Picks the first POSITIVE whose practice is in {@link #PROCESS_POSITIVE_PRACTICES} (a named good
     * process act, never code-correctness) and renders it as one short subordinate line. Returns "" when
     * no eligible process positive exists — keeping the blocking note free of any hollow reinforcement.
     */
    static String composeSubordinateProcessPositive(List<ValidatedFinding> positives) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        return positives
            .stream()
            .filter(f -> PROCESS_POSITIVE_PRACTICES.contains(f.practiceSlug()))
            .map(f -> STRENGTH_PHRASES.getOrDefault(f.practiceSlug(), humanisePracticeSlug(f.practiceSlug())))
            .filter(p -> p != null && !p.isBlank())
            .findFirst()
            .map(p -> "Worth keeping: you're " + p + ".")
            .orElse("");
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
            "\\bthreshold\\s+for\\s+a\\s+\\w+\\s+(?:verdict|finding)\\b|" +
            // Rubric-mechanics / criteria-computation leaks observed reaching students (deepseek echoes the
            // criteria's internal bucket maths and preamble tags into the reasoning). Drop the whole sentence.
            "\\braw\\s+bucket\\b|" +
            "->\\s*(?:MAJOR|MINOR|INFO|CRITICAL|POSITIVE|NEGATIVE|NOT[_ ]APPLICABLE)\\b|" +
            "\\b(?:DEFECT-DETECTOR|POSITIVE\\s+DISCIPLINE|GROUNDING\\s+GATE|EPIC\\s+EXCEPTION|EPIC/CORE-REQUIREMENT)\\b|" +
            "\\benriched\\s*[=:]|" +
            "\\b[AUDFNST]\\s*[+]?\\s*[AUDFNST]?\\s*==?\\s*\\d|" + // A=4094, A+D=4420, U == 0, F=28, N/T counts
            "\\b(?:additions?|deletions?|changed[_ ]files?)\\s*[=:]\\s*\\d|" +
            "\\bgenerated/vendored\\s+(?:check|exclusion|dominance)\\b|" +
            "\\bpartition\\s+after\\b|" +
            "\\bnoiseFraction\\b|" +
            "\\b[A-Z]\\s*=\\s*(?:true|false)\\b" + // P=true, etc.
            ")"
    );

    /**
     * Matches the whitespace run that separates two sentences (a sentence-ending [.!?] then whitespace).
     * Used to tokenise student text while preserving the original separator, so Markdown lists and
     * headings (whose items end in '.') keep their newlines instead of being folded onto one line.
     */
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Strips internal grading vocabulary from text headed to a student: split into sentences, drop any
     * that is pure rubric meta ({@link #GRADING_SENTENCE}), then tidy the whitespace the drops leave
     * behind. Idempotent and locale-safe (no naked case folding).
     */
    static String sanitizeStudentText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        // Unescape literal \n / \t the agent sometimes emits in reasoning/guidance (a JSON escape that
        // should render as a real line break, e.g. "e.g.:\n- ..."), so it reads as Markdown not raw text.
        text = text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "    ");
        // Walk sentence-ending punctuation followed by whitespace so a mid-paragraph rubric sentence is
        // removed wholesale. Preserve the ORIGINAL inter-sentence separator (notably newlines that form
        // Markdown lists/headings) for every sentence we keep — rejoining with a blanket space would
        // collapse a bulleted acceptance-criteria block into one run-on line.
        StringBuilder kept = new StringBuilder(text.length());
        Matcher sep = SENTENCE_SEPARATOR.matcher(text);
        int pos = 0;
        while (sep.find()) {
            String sentence = text.substring(pos, sep.start());
            if (!GRADING_SENTENCE.matcher(sentence).find()) {
                kept.append(sentence).append(text, sep.start(), sep.end());
            }
            pos = sep.end();
        }
        String tail = text.substring(pos);
        if (!GRADING_SENTENCE.matcher(tail).find()) {
            kept.append(tail);
        }
        String out = kept.toString();
        // Tidy the gaps the drops leave: doubled spaces, space-before-punctuation, blank lines. Use
        // [ \t] (not \s) so list/heading newlines are never folded away.
        out = out.replaceAll("[ \\t]{2,}", " ").replaceAll("[ \\t]+([.,;])", "$1").replaceAll("\\n{3,}", "\n\n");
        return stripEnvelopeCorruption(out.strip());
    }

    /**
     * A closing brace/bracket immediately followed by ≥1 quote/backslash at the very end of the text:
     * the serialized-object boundary the agent occasionally leaks INTO a guidance value (observed as a
     * {@code '"}"} tail on deepseek output). A legitimate inline JSON example ends AT the brace
     * ({@code {"k":"v"}}) with nothing after it — the trailing outer quote is the discriminator, so this
     * never fires on a real code/JSON snippet that simply closes with a brace.
     */
    private static final Pattern ENVELOPE_TAIL = Pattern.compile("[\"'\\\\]*[}\\]][\"'\\\\]+\\s*$");

    /**
     * Peels a leading {@code "<key>":} (optionally-quoted value) off a metadata-envelope span the agent
     * sometimes quotes raw. Hoisted to a static field — like the other patterns here — so it is compiled
     * once rather than per {@link #metadataSnippetText} call.
     */
    private static final Pattern METADATA_FIELD = Pattern.compile("\"[A-Za-z_][A-Za-z0-9_]*\"\\s*:\\s*\"?");

    /**
     * Repairs the JSON-envelope corruption that occasionally reaches student text: the model terminates a
     * guidance string with a leaked object boundary ({@code …quality'"}"}), often after echoing the final
     * clause ({@code …quality'"ws to adjust…quality}). Only runs when the unmistakable {@link #ENVELOPE_TAIL}
     * signature is present, so well-formed guidance — even guidance that legitimately repeats a phrase or
     * ends in a brace — is never touched. On match: drop the artifact, undo the duplicated trailing run, and
     * trim the dangling quote/space the cut leaves. Idempotent.
     */
    static String stripEnvelopeCorruption(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = ENVELOPE_TAIL.matcher(text);
        if (!m.find()) {
            return text;
        }
        String head = dropDuplicatedTail(text.substring(0, m.start()), 12);
        // Trim the dangling quote/backslash/space the splice leaves (e.g. a now-unbalanced opening quote).
        return head.replaceAll("[\"'\\\\\\s]+$", "").stripTrailing();
    }

    /**
     * If {@code s} ends with a run of ≥{@code minLen} chars that also occurs earlier, cut back to the end of
     * that earlier occurrence — removing the duplicate and any splice junk between the two copies. Scoped to
     * the corruption path ({@link #stripEnvelopeCorruption}) only, so a legitimately repeated phrase in
     * normal guidance is never trimmed.
     */
    private static String dropDuplicatedTail(String s, int minLen) {
        int n = s.length();
        for (int len = n / 2; len >= minLen; len--) {
            String suffix = s.substring(n - len);
            int earlier = s.lastIndexOf(suffix, n - len - 1);
            if (earlier >= 0) {
                return s.substring(0, earlier + len);
            }
        }
        return s;
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
        WorkArtifact artifact,
        int improvementOverflow
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
            String acknowledgement = composeAcknowledgement(positives, allNegatives.size());
            if (!acknowledgement.isEmpty()) {
                sb.append(acknowledgement).append("\n\n");
            }
        }

        // Opening: evidence-anchored issue summary (no self-level praise). The overflow count is owned
        // here so the student is told honestly that lower-value nudges were collapsed, not hidden.
        composeOpening(sb, allNegatives, artifact, improvementOverflow);

        // F5: when blocking issues exist the cheerful opener is suppressed (anti-feedback-sandwich), but
        // a WARRANTED, specific PROCESS-level positive (a named good act — engaging with review, revealing
        // intent) should still land. Surface AT MOST ONE, subordinate: a short single line AFTER the issue
        // count, never a sandwich opener, and only from PROCESS_POSITIVE_PRACTICES so a code-correctness
        // positive can never leak into a blocking note.
        if (hasBlocking) {
            String reinforcement = composeSubordinateProcessPositive(positives);
            if (!reinforcement.isEmpty()) {
                sb.append(reinforcement).append("\n\n");
            }
        }

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
        List<ValidatedFinding> negatives,
        WorkArtifact artifact,
        int improvementOverflow
    ) {
        // PRs are merged → "to fix before merging"; issues are not → "to fix".
        String blockingCta = artifact == WorkArtifact.PULL_REQUEST ? " to fix before merging" : " to fix";
        long blockingCount = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        // negatives is the CAPPED list, so this is the count we actually expand below. The collapsed
        // remainder is carried separately in improvementOverflow and disclosed via the overflow tail.
        long improvementCount = negatives.size() - blockingCount;
        // "+N more minor suggestions" — honest disclosure that lower-value nudges were folded away so the
        // student is never silently shorted, while the note still leads with the few that matter.
        String overflowTail =
            improvementOverflow > 0
                ? " (+" + improvementOverflow + " more minor suggestion" + (improvementOverflow == 1 ? "" : "s") + ")"
                : "";

        if (blockingCount > 0 && improvementCount > 0) {
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(blockingCta)
                .append(", plus ")
                .append(improvementCount)
                .append(improvementCount == 1 ? " suggestion" : " suggestions")
                .append(" for improvement")
                .append(overflowTail)
                .append(":\n\n");
        } else if (blockingCount > 0) {
            // No surviving improvements (improvementCount == 0). Overflow is impossible here: the cap keeps
            // MAX_IMPROVEMENT_SUGGESTIONS improvements whenever it collapses any, so overflow > 0 always
            // leaves improvementCount > 0 (the branch above). Plain blocking-only opener.
            sb
                .append(blockingCount)
                .append(blockingCount == 1 ? " issue" : " issues")
                .append(blockingCta)
                .append(":\n\n");
        } else {
            sb
                .append(improvementCount)
                .append(improvementCount == 1 ? " suggestion" : " suggestions")
                .append(" for improvement")
                .append(overflowTail)
                .append(":\n\n");
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
                    // Skipped entirely when the field cleans to a bare number / JSON punctuation.
                    String quoted = metadataSnippetText(snippet);
                    if (!quoted.isBlank()) {
                        sb.append("You wrote: “").append(quoted).append("”\n\n");
                    }
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
        // The agent sometimes quotes a raw span of the metadata.json envelope, dragging JSON field
        // syntax ("title": "...", "body": "...") into the quote. Peel it off: keep only the text after
        // the LAST `"<key>":` separator (the field the finding is actually about). This also handles a
        // cleanly terminated single field, which the prior trailing-anchored regex required.
        // The leading "<key>": may carry a quoted value ("body": "...") or a bare one ("additions": 2306,
        // "commits": [ {) — make the opening quote optional so numeric/array fields are peeled too.
        Matcher field = METADATA_FIELD.matcher(s);
        int valueStart = -1;
        while (field.find()) {
            valueStart = field.end();
        }
        if (valueStart >= 0) {
            s = s.substring(valueStart);
            // Drop whatever follows this value (the next "," field, a closing }/] brace) and a bare
            // trailing quote a fully-terminated value leaves behind.
            s = s.replaceAll("\"?\\s*[,}\\]].*$", "").replaceAll("\"\\s*$", "");
        }
        s = s.replace("\\n", " ").replace("\\t", " ").replace("\\\"", "\"").replaceAll("\\s+", " ").strip();
        // A metadata value that is just a number or JSON punctuation is not worth quoting back to the
        // developer ("You wrote: 2306" / "You wrote: [ {") — return blank so the caller drops the line.
        if (s.isBlank() || s.matches("[\\d\\s.,:{}\\[\\]\"]+")) {
            return "";
        }
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }

    /** Check if a path is an internal workspace artifact (not student code). */
    private static boolean isInternalPath(String location) {
        // Strip line number suffix for comparison
        String path = location.contains(":") ? location.substring(0, location.lastIndexOf(':')) : location;
        // The synthetic context envelope is cited both as the full inputs/context/metadata.json (caught
        // by the prefix) and, once the agent strips the prefix, as a bare "metadata.json". Neither is a
        // real repo file a student should see referenced — issue findings in particular only ever point
        // here, so this also clears the meaningless "metadata.json:2" location off issue notes.
        if (path.equals("metadata.json") || path.endsWith("/metadata.json")) {
            return true;
        }
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
        String path = repoRelative(pathNode.asString());
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
