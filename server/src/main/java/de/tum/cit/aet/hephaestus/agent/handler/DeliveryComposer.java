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
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedObservation;
import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 *
 * <p><b>Authored-text contract (the upstream detector owns these, this renderer prints them verbatim).</b>
 * The {@code reasoning} and {@code guidance} on a finding are written by the detector, not by this class;
 * this renderer only sanitises and lays them out. Three quality invariants the authored text must satisfy,
 * documented here so the rendering layout never works against them:
 * <ul>
 *   <li><b>M1 — acknowledge the co-occurring good signal.</b> A single BAD finding fires on one defect, but
 *       the change often did something right on the same move (a correct {@code Closes #36} link beside a
 *       thin definition-of-done). The authored {@code reasoning} may open with a one-clause acknowledgement
 *       of that adjacent good signal before the corrective — the single-finding / max-severity contract is
 *       NOT licence to structurally censor all praise. This renderer never strips such an opening clause.</li>
 *   <li><b>M2 — thread-aware, state-neutral guidance.</b> When the disposition comment, rationale, or
 *       ready-state already exists, the authored {@code guidance} must acknowledge it rather than prescribe
 *       the already-satisfied action, and avoid gate-like phrasing ("before marking the PR as ready") in
 *       favour of state-neutral feed-forward.</li>
 *   <li><b>M3 — no fabricated specifics.</b> The authored text must not invent criteria, tools, roles, or
 *       deliverables not named in the artifact (use bare {@code <criterion 1>} placeholders or quote only
 *       phrases that appear in the work), and must not attach generic future-tense advice to a
 *       PRESENT/GOOD strength.</li>
 * </ul>
 */
class DeliveryComposer {

    /**
     * Provenance stamp for the {@code Feedback} ledger (ADR 0021 C6): which renderer produced a delivered
     * body. Bump explicitly when the composition changes so a delivered unit is reproducible from its row.
     */
    static final String COMPOSER_VERSION = "v4-inline-first";

    /** Non-blocking (MINOR/INFO) suggestions surfaced in full before the rest collapse into an overflow line; blocking findings are never capped. */
    static final int MAX_IMPROVEMENT_SUGGESTIONS = 3;

    /**
     * Author-side process practices whose finding critiques the PR as a whole (its description, its commit
     * series) rather than any single changed line — they have no meaningful diff anchor and must be delivered
     * in the summary, never as an inline note.
     */
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of(
        "describe-what-and-why",
        "commits-are-atomic-and-cohesive",
        "commit-subjects-explain-each-change"
    );

    /**
     * The "is this single issue well-formed?" near-duplicate pair — scoped-to-one-concern and
     * has-a-checkable-outcome critique the SAME framing, so when both fire as a gap (ABSENT, BAD) we keep only the
     * highest-severity one. {@code breaks-large-work-into-trackable-subtasks} is excluded on purpose:
     * "decompose this epic" is a distinct, independently-actionable lesson that must survive on its own.
     */
    private static final Set<String> EPIC_STRUCTURE_PRACTICES = Set.of(
        "issue-scoped-to-single-concern",
        "issue-has-checkable-outcome"
    );

    /**
     * Co-occurrence pairs (W4): two practices that, when both fire as a gap (BAD), deliver the SAME
     * underlying fact and so must collapse to ONE finding rather than pile on as two separate blocking
     * items. Each entry is {@code redundant-slug → preferred-slug}: when BOTH are present, the redundant
     * one is dropped and the preferred (more-actionable, anchored-on-the-change) one is kept. The set is
     * deliberately small and explicit so distinct lessons are never merged.
     *
     * <ul>
     *   <li>{@code ready-and-traceable-handoff → ships-tests-with-the-change}: a DoD checkbox claiming "all
     *       tests pass" when no tests changed is the no-tests fact, which ships-tests owns more actionably
     *       (it anchors on the missing test, not on a checkbox).</li>
     * </ul>
     */
    private static final Map<String, String> CO_OCCURRENCE_REDUNDANT_TO_PREFERRED = Map.ofEntries(
        Map.entry("ready-and-traceable-handoff", "ships-tests-with-the-change")
    );

    /**
     * Curated short, task-level strength phrases keyed by practice slug. A GOOD finding whose slug has an
     * entry here renders as a concrete "Worth keeping: you're <gerund>." line; a GOOD finding without one
     * falls back to a generic, grammatical acknowledgement (see {@link #composeSubordinatePositive}). Every
     * phrase names what the WORK does, never grades the author — the no-self-praise / process-not-person
     * rules still govern the subordinate line.
     */
    private static final Map<String, String> SUBORDINATE_STRENGTH_PHRASES = Map.ofEntries(
        Map.entry("engaging-with-inline-review-comments", "engaging with the review feedback"),
        Map.entry("acting-on-review-feedback", "acting on the review feedback"),
        Map.entry("intent-revealing-comments", "leaving intent-revealing comments"),
        Map.entry("leaves-the-code-clean-with-intent-revealing-comments", "leaving intent-revealing comments"),
        Map.entry("commit-subjects-explain-each-change", "writing commit subjects that explain each change"),
        Map.entry("commits-are-atomic-and-cohesive", "keeping each commit atomic and cohesive"),
        Map.entry("excludes-generated-and-build-artifacts", "keeping generated and build artifacts out of the diff"),
        Map.entry("ready-and-traceable-handoff", "linking the change to its issue"),
        Map.entry("describe-what-and-why", "explaining what changed and why"),
        Map.entry("scope-one-reviewable-change", "keeping the change focused and reviewable"),
        Map.entry("triages-the-issue-with-labels-and-ownership", "triaging the issue with a clear type label"),
        Map.entry("breaks-large-work-into-trackable-subtasks", "breaking the work into trackable subtasks")
    );

    /**
     * Strips the leading repo-mount prefix so a student-facing location stays repo-relative. The repo
     * mounts at the integration-namespaced {@code inputs/sources/scm/repo/} (ADR 0020).
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

    /** A finding is a problem when its detector-resolved assessment is {@link Assessment#BAD} (ADR 0022). */
    private static boolean isProblem(ValidatedObservation f) {
        return f.assessment() == Assessment.BAD;
    }

    /** A finding is a strength when its detector-resolved assessment is {@link Assessment#GOOD} (ADR 0022). */
    private static boolean isStrength(ValidatedObservation f) {
        return f.assessment() == Assessment.GOOD;
    }

    /** Compose for a pull request (the default artifact; CTA reads "to fix before merging"). */
    @Nullable
    static DeliveryContent compose(List<ValidatedObservation> findings) {
        return compose(findings, WorkArtifact.PULL_REQUEST);
    }

    /**
     * Compose feedback for a specific artifact. The blocking call-to-action is artifact-aware: a PR
     * reads "to fix before merging", an ISSUE simply "to fix" (issues are not merged). "Is this finding a
     * problem vs a strength?" is decided per finding by its {@code assessment} (ADR 0022).
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedObservation> findings, WorkArtifact artifact) {
        return compose(findings, artifact, Map.of());
    }

    /**
     * Compose with the catalogue-authored transferable principle ({@code whyBySlug}, keyed by practice
     * slug from {@code Practice.whyItMatters}) surfaced on substantive critiques. The principle is the
     * feed-forward layer the model is deliberately told NOT to write itself (so it cannot fabricate or
     * drift it); the server supplies it verbatim here. An empty map omits the principle line, leaving the
     * rest of the delivery unchanged.
     */
    @Nullable
    static DeliveryContent compose(
        List<ValidatedObservation> findings,
        WorkArtifact artifact,
        Map<String, String> whyBySlug
    ) {
        // First-pass compose: inline notes have not been posted yet, so NO finding is known-delivered.
        // An empty delivered-key set makes every inlinable finding render its full summary line — the
        // safe pre-delivery state, and the permanent fallback for any finding whose inline note never lands.
        return compose(findings, artifact, whyBySlug, Set.of());
    }

    /**
     * Compose with a server-side GROUNDING GUARD (M1): the last line of defence before a hallucinated locus
     * lands on a student as a confidently-anchored inline note. {@code unifiedDiff} is the raw two-ref diff
     * of the change under review; any inline anchor whose file is not in that diff's changed-file set, AND
     * whose evidence snippet is not substring-present in that file's hunk, has its inline anchor DROPPED —
     * the finding still delivers in full via the summary, only the ungrounded file:line is withheld. Passing
     * a blank diff (or using an overload without one) disables the guard — a strict no-op, preserving the
     * existing delivery layout for callers that cannot supply the diff.
     */
    @Nullable
    static DeliveryContent compose(
        List<ValidatedObservation> findings,
        WorkArtifact artifact,
        Map<String, String> whyBySlug,
        @Nullable String unifiedDiff
    ) {
        return compose(findings, artifact, whyBySlug, Set.of(), GroundingContext.fromDiff(artifact, unifiedDiff));
    }

    /**
     * Recomposes ONLY the MR summary body after inline notes have been posted, demoting every inlinable
     * finding whose inline comment actually landed (its {@code findingFingerprint} is in {@code deliveredKeys})
     * to a one-line "see inline comments" pointer, while a finding whose inline note FAILED keeps its full
     * summary line as the fallback. Re-runs the identical partition pipeline as {@link #compose} so the
     * recomposed summary cannot drift from the first pass — only the inline section reacts to the signals.
     * Returns {@code null} when there is nothing to summarise (mirrors {@link #compose}).
     */
    @Nullable
    static String recomposeMrNote(
        List<ValidatedObservation> findings,
        WorkArtifact artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys
    ) {
        DeliveryContent recomposed = compose(findings, artifact, whyBySlug, deliveredKeys);
        return recomposed == null ? null : recomposed.mrNote();
    }

    @Nullable
    private static DeliveryContent compose(
        List<ValidatedObservation> findings,
        WorkArtifact artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys
    ) {
        return compose(findings, artifact, whyBySlug, deliveredKeys, GroundingContext.none());
    }

    @Nullable
    private static DeliveryContent compose(
        List<ValidatedObservation> findings,
        WorkArtifact artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys,
        GroundingContext grounding
    ) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }
        // One transferable principle per practice per delivery — shared across the summary and the inline
        // diff notes so a slug's "Why this matters" lands exactly once, wherever that finding renders in full.
        Set<String> emittedWhy = new HashSet<>();

        List<ValidatedObservation> negatives = findings
            .stream()
            .filter(DeliveryComposer::isProblem)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        // On an ISSUE the two "is this single issue well-formed?" detectors (scoped + checkable) say the
        // same thing about the same framing, so collapse them to the single highest-severity one — one clear
        // lesson, not two stacked near-duplicate bullets. breaks-large-work is NOT in the set (it is a
        // distinct "decompose this epic" lesson that must always survive — see EPIC_STRUCTURE_PRACTICES).
        // Severity-sorted above (CRITICAL ordinal 0 first), so the first epic-structure finding seen is
        // the lead we keep; later ones are the redundant siblings we drop. Conservative: ISSUE-only,
        // and only within EPIC_STRUCTURE_PRACTICES, so distinct lessons are never merged.
        if (artifact == WorkArtifact.ISSUE) {
            negatives = dedupEpicStructure(negatives);
        }

        // Co-occurrence dedup (W4): two findings sometimes deliver the SAME underlying fact as separate
        // blocking items (most often the no-tests fact: ready-and-traceable-handoff flags a DoD checkbox that
        // claims "all tests pass" while ships-tests-with-the-change flags the absent tests). A student
        // shouldn't read one root cause as two stacked MAJORs, so the pair collapses to ONE — the more
        // actionable member (the one anchored on the change itself). Defined conservatively as an explicit,
        // small pair set so distinct lessons (e.g. breaks-large-work vs scope) are never merged.
        negatives = dedupCoOccurringNegatives(negatives);

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

        // No problems → an observation note over the strength findings (see composeNoIssuesNote).
        if (negatives.isEmpty()) {
            List<ValidatedObservation> observed = findings.stream().filter(DeliveryComposer::isStrength).toList();
            if (observed.isEmpty()) {
                // Every finding abstained (all NOT_APPLICABLE): the artifact could not be assessed against
                // any active practice, so deliver nothing rather than a misleading "nothing to change here"
                // all-clear on work that was never actually evaluated.
                return null;
            }
            return new DeliveryContent(composeNoIssuesNote(observed, whyBySlug, emittedWhy), List.of());
        }

        // Partition negatives: inlinable (a diff note) vs non-inlinable (expanded in the summary).
        // Issues carry no diff, so a positional note can never be posted on them — every issue finding
        // must be expanded in full in the issue note itself rather than demoted to a diff note that
        // silently vanishes, leaving the student a bare title with no reasoning or guidance.
        boolean inlineSupported = artifact == WorkArtifact.PULL_REQUEST;
        List<ValidatedObservation> inlinable = new ArrayList<>();
        List<ValidatedObservation> nonInlinable = new ArrayList<>();
        for (ValidatedObservation f : negatives) {
            if (inlineSupported && !isNonInlinable(f)) {
                inlinable.add(f);
            } else {
                nonInlinable.add(f);
            }
        }

        // Strength findings the same job produced — surfaced as a brief strengths line before the
        // critiques so the note acknowledges effort (task-level, not person-level praise).
        List<ValidatedObservation> positives = findings.stream().filter(DeliveryComposer::isStrength).toList();

        // MR summary note: opening + non-inlinable findings expanded + brief inline overview. The inline
        // overview is signal-driven (deliveredKeys): a finding whose inline comment landed collapses to a
        // pointer, one whose note failed keeps its full line as the summary fallback.
        String mrNote = composeMrNote(
            positives,
            negatives,
            nonInlinable,
            inlinable,
            improvementOverflow,
            deliveredKeys,
            whyBySlug,
            emittedWhy
        );

        // Diff notes: ALL inlinable negatives get inline comments (grounding guard drops ungrounded anchors)
        List<DiffNote> diffNotes = collectDiffNotes(inlinable, whyBySlug, emittedWhy, grounding);

        return new DeliveryContent(mrNote, diffNotes);
    }

    /**
     * Collapses overlapping epic issue-structure gap (BAD) findings. Keeps the FIRST
     * {@link #EPIC_STRUCTURE_PRACTICES} finding encountered (the list is severity-sorted, so that is the
     * highest-severity lead) and drops the rest; every non-epic-structure finding passes through
     * untouched and in order. No-op when fewer than two epic-structure findings are present.
     */
    private static List<ValidatedObservation> dedupEpicStructure(List<ValidatedObservation> negatives) {
        long epicCount = negatives
            .stream()
            .filter(f -> EPIC_STRUCTURE_PRACTICES.contains(f.practiceSlug()))
            .count();
        if (epicCount < 2) {
            return negatives;
        }
        List<ValidatedObservation> kept = new ArrayList<>(negatives.size());
        boolean epicKept = false;
        for (ValidatedObservation f : negatives) {
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
     * Collapses {@link #CO_OCCURRENCE_REDUNDANT_TO_PREFERRED} pairs whose two members deliver the SAME
     * underlying fact (W4). For each entry, when BOTH the redundant and the preferred slug are present as
     * gap (BAD) findings, the redundant one is dropped so the student sees the lesson once via the more
     * actionable preferred finding. Every other finding passes through untouched and in order; a pair with
     * only one member present is left alone (no over-merge). Order-preserving over the incoming
     * severity-sorted list.
     */
    private static List<ValidatedObservation> dedupCoOccurringNegatives(List<ValidatedObservation> negatives) {
        Set<String> present = negatives.stream().map(ValidatedObservation::practiceSlug).collect(Collectors.toSet());
        // Drop a redundant slug only when its preferred partner is also present in THIS delivery.
        Set<String> toDrop = CO_OCCURRENCE_REDUNDANT_TO_PREFERRED.entrySet()
            .stream()
            .filter(e -> present.contains(e.getKey()) && present.contains(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        if (toDrop.isEmpty()) {
            return negatives;
        }
        return negatives
            .stream()
            .filter(f -> !toDrop.contains(f.practiceSlug()))
            .toList();
    }

    /**
     * Caps the non-blocking (MINOR/INFO) improvement tail to {@link #MAX_IMPROVEMENT_SUGGESTIONS}. EVERY
     * blocking (CRITICAL/MAJOR) finding is kept — blocking is never capped. Among the non-blocking
     * findings the kept ones are the highest-severity, then highest-confidence (most certain nudge wins a
     * tie); the rest are collapsed into the overflow count the caller renders. The returned list preserves
     * the incoming severity ordering so the existing lead-with-blocking layout is untouched. Caller only
     * invokes this when the non-blocking count actually exceeds the cap.
     */
    private static List<ValidatedObservation> capImprovementTail(List<ValidatedObservation> negatives) {
        List<ValidatedObservation> blocking = new ArrayList<>();
        List<ValidatedObservation> improvements = new ArrayList<>();
        for (ValidatedObservation f : negatives) {
            if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
                blocking.add(f);
            } else {
                improvements.add(f);
            }
        }
        // Pick the few highest-leverage improvements: severity (MINOR before INFO) then confidence desc.
        // Collect by reference IDENTITY (not value-equality): ValidatedObservation is a record, so two findings
        // with identical content are equal — a value-set would collapse them into one slot, letting the
        // order-preserving re-emit below match BOTH and overshoot the cap. Identity keeps exactly the
        // limit()-selected instances.
        Set<ValidatedObservation> keptImprovements = improvements
            .stream()
            .sorted(
                Comparator.comparingInt((ValidatedObservation f) -> f.severity().ordinal()).thenComparing(
                    Comparator.comparingDouble(ValidatedObservation::confidence).reversed()
                )
            )
            .limit(MAX_IMPROVEMENT_SUGGESTIONS)
            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

        // Re-emit in the original (severity-sorted) order, dropping the improvements that did not survive.
        List<ValidatedObservation> kept = new ArrayList<>(blocking.size() + keptImprovements.size());
        for (ValidatedObservation f : negatives) {
            if (blocking.contains(f) || keptImprovements.contains(f)) {
                kept.add(f);
            }
        }
        return kept;
    }

    /** Positives a learner can act on at once — kept to 1-3 (deliberate practice). */
    private static final int MAX_STRENGTH_REINFORCEMENTS = 3;

    /** Whole-sentence budget for a positive observation/forward-prompt — generous enough not to clip an enumeration. */
    private static final int STRENGTH_BUDGET = 280;

    /**
     * Compose the note posted when no issues were found — reports what was reviewed and, where the agent
     * recorded reasoning, what it observed against each practice. Carries NO self-level praise: feedback stays
     * at the task/process level, never person-directed.
     *
     * <p>W7 — the catalogue-authored transferable principle ({@code whyBySlug}) is surfaced on the all-GOOD
     * path too, on the lead strength bullet, so an above-bar student hears the standard affirmed rather than
     * silence. It is the same verbatim "Why this matters" line the critique path uses (the feed-up layer),
     * and it lands at most once per delivery via the shared {@code emittedWhy} ledger.
     */
    private static String composeNoIssuesNote(
        List<ValidatedObservation> observed,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        // Findings whose reasoning lets us cite a concrete observation, ranked most-certain first so the
        // highest-confidence reinforcements survive the cap.
        List<ValidatedObservation> withReasoning = observed
            .stream()
            .filter(f -> f.reasoning() != null && !f.reasoning().isBlank())
            .sorted(Comparator.comparingDouble((ValidatedObservation f) -> f.confidence()).reversed())
            .toList();

        if (withReasoning.isEmpty()) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }

        var bullets = new StringBuilder(1024);
        int shown = 0;
        boolean principleShown = false;
        for (ValidatedObservation f : withReasoning) {
            if (shown >= MAX_STRENGTH_REINFORCEMENTS) break;
            // Whole-sentence budget clamp: never clip a multi-clause observation mid-enumeration.
            String summary = clampToSentenceBudget(sanitizeStudentText(f.reasoning()).strip(), STRENGTH_BUDGET);
            if (summary.isBlank()) {
                // The reasoning was entirely grading-meta and scrubbed to nothing — skip it rather than
                // emit a bare "- **Practice:** " bullet with no observation behind it.
                continue;
            }
            String label = capitalize(f.practiceSlug().replace('-', ' '));
            bullets.append("- **").append(label).append(":** ").append(summary);
            // Feed-forward: append the grounded guidance (transferable principle + one forward prompt). Bare,
            // empty, or "No change needed." guidance degrades gracefully to just the observation.
            String forward = clampToSentenceBudget(
                sanitizeStudentText(f.guidance() == null ? "" : f.guidance()).strip(),
                STRENGTH_BUDGET
            );
            if (!forward.isBlank() && !forward.replace(".", "").equalsIgnoreCase("No change needed")) {
                bullets.append(' ').append(forward);
            }
            bullets.append("\n");
            // W7: feed-up \u2014 append the catalogue "Why this matters" on the lead bullet that has one, once.
            if (!principleShown) {
                String why = strengthPrincipleText(f, whyBySlug, emittedWhy);
                if (!why.isBlank()) {
                    bullets.append("  ").append(why).append("\n");
                    principleShown = true;
                }
            }
            shown++;
        }
        if (shown == 0) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }
        // Build-on framing (mentoring, not audit) \u2014 task/process level, never person-level praise.
        return "What's working well here, and how to keep building on it:\n\n" + bullets + "\n";
    }

    /**
     * The catalogue "Why this matters" line for a STRENGTH finding on the all-GOOD path (W7), or {@code ""}
     * when there is none to surface (no authored principle, or one already emitted this delivery). Unlike
     * {@link #principleText}, it does NOT skip on INFO severity \u2014 a strength finding carries INFO by
     * construction, yet the affirmed standard is exactly what an above-bar student should hear. Still deduped
     * once-per-delivery via the shared {@code emittedWhy} ledger so the same slug never repeats its principle.
     */
    private static String strengthPrincipleText(
        ValidatedObservation f,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        String why = whyBySlug.get(f.practiceSlug());
        if (why == null || why.isBlank()) {
            return "";
        }
        if (!emittedWhy.add(f.practiceSlug())) {
            return ""; // this practice's principle already surfaced earlier in the same delivery
        }
        return "_Why this matters:_ " + sanitizeStudentText(why).strip();
    }

    /**
     * Clamps {@code text} to whole sentences within {@code maxLen}: appends sentences (split on
     * {@link #SENTENCE_SEPARATOR}) until the next would exceed the budget, stopping at the last whole one.
     * Only when even the first sentence overruns does it fall back to {@link #truncateToFirstSentence}'s
     * word-boundary cut \u2014 so a multi-clause enumeration is never clipped mid-thought.
     */
    static String clampToSentenceBudget(String text, int maxLen) {
        if (text == null || text.isBlank() || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        StringBuilder out = new StringBuilder(maxLen);
        Matcher sep = SENTENCE_SEPARATOR.matcher(text);
        int pos = 0;
        while (sep.find()) {
            String sentence = text.substring(pos, sep.end());
            if (out.length() + sentence.length() > maxLen) {
                break;
            }
            out.append(sentence);
            pos = sep.end();
        }
        if (pos < text.length()) {
            String tail = text.substring(pos);
            if (out.length() + tail.length() <= maxLen) {
                out.append(tail);
            }
        }
        if (out.length() == 0) {
            // Even the first sentence overruns \u2014 fall back to the word-boundary cut.
            return truncateToFirstSentence(text, maxLen);
        }
        return out.toString().strip();
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
        Map.entry("issue-has-checkable-outcome", "defining a clear, checkable outcome"),
        Map.entry("triages-the-issue-with-labels-and-ownership", "triaging the issue with a clear type label")
    );

    /**
     * Builds a one-sentence strengths acknowledgement from up to two GOOD (strength) findings, e.g.
     * "Nice work keeping the change focused and reviewable and linking the change to its issue — a
     * couple of things to tighten:". Returns "" when there are no positives. Strictly task-level: it
     * names what the work does, never grades the author.
     */
    static String composeAcknowledgement(List<ValidatedObservation> positives, int improvementCount) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        // Curated gerund phrases ONLY — a non-curated slug humanised to raw text ("triages the issue …")
        // breaks the "Nice work keeping X and [Ying]" grammar, so an un-phrased strength is simply not
        // named in the opener rather than dumped verbatim.
        List<String> phrases = positives
            .stream()
            .map(f -> STRENGTH_PHRASES.get(f.practiceSlug()))
            .filter(p -> p != null && !p.isBlank())
            .distinct()
            .limit(2)
            .toList();
        // The lead-in counts the IMPROVEMENTS that follow, not the strengths named — otherwise a single
        // strength in front of two suggestions reads "one thing to tighten:" above a list of two.
        String tail = improvementCount > 1 ? " — a couple of things to tighten:" : " — one thing to tighten:";
        if (phrases.isEmpty()) {
            // C2: a real GOOD strength exists but none has a curated gerund phrase. Acknowledge it
            // GENERICALLY rather than (a) silently dropping the whole opener — a real positive then vanishes —
            // or (b) dumping an ungrammatical raw slug into the "Nice work <gerund>" frame. "Nice work here"
            // is grammatical and never drops the acknowledgement.
            return "Nice work here" + tail;
        }
        String strengths = phrases.size() == 1 ? phrases.get(0) : phrases.get(0) + " and " + phrases.get(1);
        return "Nice work " + strengths + tail;
    }

    /**
     * Builds the single earned strength line allowed alongside blocking issues (W1). Formative feedback
     * REQUIRES naming what to keep doing, so even under a blocking finding the note opens its body with ONE
     * brief, genuine acknowledgement of the run's HIGHEST-CONFIDENCE GOOD finding — any practice, not only
     * process ones — before the corrective lands. This is NOT a feedback sandwich that buries the critique:
     * it is a single subordinate line ("Worth keeping: …") rendered after the issue count, and the
     * no-self-praise / process-not-person rules still govern it (it names what the WORK does).
     *
     * <p>A GOOD finding whose slug has a curated phrase renders it concretely; a GOOD finding without one
     * is acknowledged generically ("Worth keeping: there's solid work here to build on.") rather than (a)
     * dropped — a real strength then vanishes — or (b) dumped as a raw ungrammatical slug. Returns "" only
     * when there is genuinely no GOOD finding to surface.
     */
    static String composeSubordinatePositive(List<ValidatedObservation> positives) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        // Most-certain GOOD finding first, so the single line we are allowed lands on the strongest signal.
        return positives
            .stream()
            .filter(DeliveryComposer::isStrength)
            .max(Comparator.comparingDouble(ValidatedObservation::confidence))
            .map(DeliveryComposer::subordinateStrengthLine)
            .orElse("");
    }

    /** Renders one GOOD finding as the subordinate "Worth keeping: …" line (curated phrase or generic fallback). */
    private static String subordinateStrengthLine(ValidatedObservation f) {
        String phrase = SUBORDINATE_STRENGTH_PHRASES.get(f.practiceSlug());
        if (phrase != null && !phrase.isBlank()) {
            return "Worth keeping: you're " + phrase + ".";
        }
        // C2 (subordinate): a real GOOD strength with no curated gerund — acknowledge generically and
        // grammatically rather than drop it or dump the raw slug into the "you're <gerund>" frame.
        return "Worth keeping: there's solid work here to build on.";
    }

    /**
     * Matches the whitespace run that separates two sentences (a sentence-ending [.!?] then whitespace).
     * Used to tokenise student text while preserving the original separator, so Markdown lists and
     * headings (whose items end in '.') keep their newlines instead of being folded onto one line.
     */
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Strips internal grading vocabulary from student-facing text. Delegates to the shared
     * {@link StudentTextSanitizer} so the SCM composer, the reflective dashboard, and the mentor context files
     * all run the SAME scrub (audit firewall gap #1). Kept as a package-visible seam for the composer tests.
     */
    static String sanitizeStudentText(@Nullable String text) {
        return StudentTextSanitizer.sanitize(text);
    }

    /** JSON-envelope corruption repair — delegated to {@link StudentTextSanitizer#stripEnvelopeCorruption}. */
    static String stripEnvelopeCorruption(String text) {
        return StudentTextSanitizer.stripEnvelopeCorruption(text);
    }

    /** Truncate text to the first sentence or maxLen chars, whichever is shorter. */
    private static String truncateToFirstSentence(String text, int maxLen) {
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
        int space = text.lastIndexOf(' ', maxLen);
        if (space > maxLen / 2) {
            return text.substring(0, space) + "...";
        }
        return text.substring(0, maxLen) + "...";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Check whether a finding is non-inlinable (belongs in MR summary, not a diff note).
     * Non-inlinable if: practice is inherently non-inlinable, OR finding has neither a
     * usable evidence location nor an agent-supplied {@code suggestedDiffNote}.
     */
    private static boolean isNonInlinable(ValidatedObservation f) {
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
     * 3. Brief overview of inline findings — signal-driven by {@code deliveredKeys}
     *
     * <p>Pure: the inline overview reacts only to the injected {@code deliveredKeys} set (no I/O). An
     * inlinable finding whose inline comment actually landed (its correlation key is in the set) collapses
     * to a single "see inline comments" pointer — the detail already lives on the diff. A finding whose
     * inline note did NOT land keeps its full summary line, so a delivery failure still reaches the student
     * somewhere. An empty set means "nothing delivered yet" → every inlinable finding keeps its full line.
     */
    static String composeMrNote(
        List<ValidatedObservation> positives,
        List<ValidatedObservation> allNegatives,
        List<ValidatedObservation> nonInlinable,
        List<ValidatedObservation> inlinable,
        int improvementOverflow,
        Set<String> deliveredKeys,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        var sb = new StringBuilder(4096);

        // Strengths first: name 1-2 things the work already does well (task-level acknowledgement)
        // before the critiques, so a suggestions-only note is never deficit-only when the job also
        // found strengths. Suppressed when there is a blocking (CRITICAL/MAJOR) issue: front-loading
        // praise ahead of a serious problem reads as a hollow "feedback sandwich" and dilutes the
        // message. Task/process-level only — never person-level praise.
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
        composeOpening(sb, allNegatives, improvementOverflow);

        // When blocking issues exist the cheerful multi-strength opener is suppressed (anti-feedback-sandwich),
        // but a single EARNED acknowledgement must still land (W1): formative feedback requires naming what to
        // keep doing, and the run already detected it. Surface AT MOST ONE, subordinate — a short single line
        // AFTER the issue count, never a sandwich opener — picking the highest-confidence GOOD finding of the
        // run (any practice). This is bounded to one line and stays task-level, so the corrective is never
        // buried and no self-praise leaks in.
        if (hasBlocking) {
            String reinforcement = composeSubordinatePositive(positives);
            if (!reinforcement.isEmpty()) {
                sb.append(reinforcement).append("\n\n");
            }
        }

        // Non-inlinable findings (full detail) — these only exist in the summary
        for (int i = 0; i < nonInlinable.size(); i++) {
            composeFinding(sb, nonInlinable.get(i), whyBySlug, emittedWhy);
            if (i < nonInlinable.size() - 1 || !inlinable.isEmpty()) {
                sb.append("---\n\n");
            }
        }

        // Inline findings — signal-driven. The label is emitted whenever the list is non-empty (gating it on
        // nonInlinable would leave a clean PR with only inline findings showing an UNLABELED wall of duplicated
        // headers right after the count opener). A finding whose inline comment LANDED (its correlation key
        // is in deliveredKeys) is not re-listed here — its full detail already lives on the diff, so the
        // summary only points at it. A finding whose inline note did NOT land keeps its full header line so
        // the lesson still reaches the student in the summary (the delivery-failure fallback). With an empty
        // deliveredKeys set (pre-delivery / no signals) every inlinable finding keeps its full line.
        if (!inlinable.isEmpty()) {
            // A null/blank correlation key can never match a delivered key (and Set.of().contains(null)
            // throws), so a keyless finding is always treated as undelivered → keeps its full summary line.
            List<ValidatedObservation> undelivered = inlinable
                .stream()
                .filter(f -> f.recurrenceKey() == null || !deliveredKeys.contains(f.recurrenceKey()))
                .toList();
            long deliveredCount = inlinable.size() - undelivered.size();
            sb.append("**Inline comments on the diff:**");
            if (deliveredCount > 0) {
                sb
                    .append(" see the ")
                    .append(deliveredCount)
                    .append(deliveredCount == 1 ? " inline comment" : " inline comments")
                    .append(" below.");
            }
            sb.append("\n\n");
            for (ValidatedObservation f : undelivered) {
                appendFindingHeader(sb, f, true);
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void composeOpening(
        StringBuilder sb,
        List<ValidatedObservation> negatives,
        int improvementOverflow
    ) {
        // Hephaestus is a NON-BLOCKING, feedback-first mentor — it never gates a merge. "to fix before
        // merging" is gatekeeping language that is wrong on every PR (and absurd on an already-merged one),
        // so the call-to-action is state-neutral feed-forward: name what is worth tightening, not a gate to
        // clear. Issues are not merged either, so they share the same "to tighten" framing. Merge-state is
        // not plumbed into the composer; the non-blocking reframe is correct regardless of state, so no
        // brittle state dependency is added just for this line.
        String blockingCta = " to tighten";
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
            // Blocking-only opener: overflow can't reach this branch — it always co-occurs with surviving
            // improvements (the branch above), since the cap keeps MAX_IMPROVEMENT_SUGGESTIONS when it collapses any.
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

    /**
     * Renders the canonical finding header — emoji inside the bold, optional {@code · `location`} — used by
     * every surface (MR summary list, full finding, diff note) so the format cannot drift between them.
     */
    private static void appendFindingHeader(StringBuilder sb, ValidatedObservation f, boolean withLocation) {
        sb.append("**").append(severityEmoji(f.severity())).append(" ").append(f.title()).append("**");
        if (withLocation) {
            String location = extractPrimaryLocation(f);
            if (location != null && !isInternalPath(location)) {
                sb.append(" · `").append(location).append("`");
            }
        }
    }

    private static void composeFinding(
        StringBuilder sb,
        ValidatedObservation f,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        appendFindingHeader(sb, f, true);
        sb.append("\n\n");

        String location = extractPrimaryLocation(f);
        String lang = detectLanguage(f);

        // For CRITICAL/MAJOR: "You wrote:" → reasoning → "Instead:" with fix
        if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
            String snippet = extractPrimarySnippet(f);
            // A "You wrote:" quote is meant to echo the STUDENT's artifact. When the agent instead drops its
            // own pipeline plumbing / rubric mechanics into the evidence field ("diff_stat.txt lists 28 …
            // material disagreement; trusting the diff"), the verbatim quote would leak it past the
            // reasoning sanitizer — so suppress the quote entirely when it carries grader mechanics.
            if (snippet != null && !containsGraderMechanics(snippet)) {
                boolean hasCodeLocation = location != null && !isInternalPath(location);
                if (hasCodeLocation) {
                    // Real code reference → fenced code block. This echo is high-value (shows the offending line).
                    sb.append("You wrote:\n");
                    sb.append("```").append(lang).append("\n").append(snippet).append("\n```\n\n");
                }
                // Metadata-field findings (title/body spans, draft/WIP flags) intentionally do NOT echo a
                // "You wrote: …" quote. The agent's metadata span is frequently a truncated heading, a single
                // token, a title==body echo, or a serialized boolean ("[Feat", "false", "Analysis Object Model
                // (AOM)", a mid-sentence body cut) that reads as broken output and leaks raw fields to the
                // student; it added no value the reasoning + guidance don't already carry.
            }

            appendStudentText(sb, f.reasoning());
            appendPrinciple(sb, f, whyBySlug, emittedWhy);
            appendStudentText(sb, f.guidance());
        } else {
            // MINOR/INFO: combine reasoning + guidance naturally
            appendStudentText(sb, f.reasoning());
            appendPrinciple(sb, f, whyBySlug, emittedWhy);
            appendStudentText(sb, f.guidance());
        }
    }

    /**
     * Surfaces the catalogue-authored transferable principle ({@code Practice.whyItMatters}) as a single
     * "Why this matters" line between the grounded observation and the forward step — completing Hattie's
     * formative loop (feed-back → principle → feed-forward) that a task-level critique otherwise lacks.
     *
     * <p>Pulled VERBATIM from the catalogue (never model-generated), so it cannot fabricate or drift and
     * carries no rubric vocabulary. Emitted at most once per practice slug per delivery (shared
     * {@code emittedWhy}) so a multi-finding note never repeats it, and never on an INFO nudge.
     *
     * <p>Cognitive-load budget: a BLOCKING (CRITICAL/MAJOR) critique each keeps its principle — the stakes
     * justify the why and the developer cannot simply ignore it. But ADVISORY (MINOR) critiques get at most
     * ONE principle line across the whole delivery: a craft-heavy note (several suggestions) lands a single
     * teaching moment rather than a wall of rationale that reads as preachy and dilutes the lesson (Shute
     * 2008 "as simple as possible"; reactance to repeated unsolicited justification). An absent/blank entry
     * (e.g. the empty default map) is a no-op, so a slug without an authored principle changes nothing.
     */
    private static void appendPrinciple(
        StringBuilder sb,
        ValidatedObservation f,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        sb.append(principleText(f, whyBySlug, emittedWhy));
    }

    /**
     * Sentinel marker tracked in {@code emittedWhy} once a non-blocking (advisory) principle line has been
     * surfaced this delivery, so only the lead advisory critique carries one. Not a valid practice slug
     * (slugs match {@code WorkspaceAbi.PRACTICE_SLUG}), so it can never collide with a real entry.
     */
    private static final String ADVISORY_PRINCIPLE_SHOWN = " advisory-principle-shown";

    /**
     * The "Why this matters" line for {@code f}, or {@code ""} when it should not be surfaced (INFO nudge,
     * no authored principle, already emitted this delivery, or a second advisory principle). Mutates
     * {@code emittedWhy} on success. See {@link #appendPrinciple}.
     */
    private static String principleText(ValidatedObservation f, Map<String, String> whyBySlug, Set<String> emittedWhy) {
        if (f.severity() == Severity.INFO) {
            return "";
        }
        String why = whyBySlug.get(f.practiceSlug());
        if (why == null || why.isBlank()) {
            return "";
        }
        boolean blocking = f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR;
        if (!blocking && emittedWhy.contains(ADVISORY_PRINCIPLE_SHOWN)) {
            return ""; // an advisory teaching moment already landed this delivery — don't stack another
        }
        if (!emittedWhy.add(f.practiceSlug())) {
            return ""; // this practice's principle already surfaced earlier in the same delivery
        }
        if (!blocking) {
            emittedWhy.add(ADVISORY_PRINCIPLE_SHOWN);
        }
        return "_Why this matters:_ " + sanitizeStudentText(why).strip() + "\n\n";
    }

    /** True when {@code text} carries any internal grading-mechanics / pipeline-plumbing token. */
    private static boolean containsGraderMechanics(@Nullable String text) {
        return StudentTextSanitizer.isGradingMeta(text);
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
    private static String detectLanguage(ValidatedObservation f) {
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
    private static String extractPrimaryLocation(ValidatedObservation f) {
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
    private static String extractPrimarySnippet(ValidatedObservation f) {
        JsonNode evidence = f.evidence();
        if (evidence == null || evidence.isNull()) return null;
        JsonNode snippets = evidence.get("snippets");
        if (snippets == null || !snippets.isArray() || snippets.isEmpty()) return null;
        JsonNode first = snippets.get(0);
        // Jackson 3 asString() throws on a container node; an off-contract object/array snippet must not abort
        // the whole job's delivery. Only a textual scalar yields a snippet.
        if (first == null || !first.isString()) return null;
        String snippet = first.asString();
        return (snippet != null && !snippet.isBlank()) ? snippet.strip() : null;
    }

    /**
     * Collect inline diff notes from BAD (problem) findings.
     *
     * <p>Prefer the agent's per-finding {@code suggestedDiffNotes} (richer, explicit lines/body).
     * Fall back to a synthesized note from the first evidence location + composed body when the
     * agent did not supply one.
     */
    private static List<DiffNote> collectDiffNotes(
        List<ValidatedObservation> negatives,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy,
        GroundingContext grounding
    ) {
        List<DiffNote> notes = new ArrayList<>();

        for (ValidatedObservation f : negatives) {
            if (notes.size() >= PracticeDetectionResultParser.MAX_DELIVERY_DIFF_NOTES) break;

            // Prefer the agent's suggestedDiffNotes — but at most ONE per finding (its primary anchor). A
            // single lesson split across several near-identical inline notes reads as nagging; the summary
            // already lists the finding once, so one inline note carries the detail without the pile-on.
            if (!f.suggestedDiffNotes().isEmpty()) {
                // Prefer the agent's note, but run its body through the same student-text sanitizer the
                // synthesized branch uses: the agent body is raw model output and can echo grading-meta that
                // the student must never see (the synthesized path scrubs via appendStudentText, this one did
                // not). Carry the finding's correlation key so the inline channel can match the delivered
                // placement back to its persisted finding (ADR 0021 C2).
                DiffNote suggested = f.suggestedDiffNotes().get(0);
                // GROUNDING GUARD (M1): drop the inline ANCHOR if it is ungrounded — a path absent from the
                // diff's changed-file set whose finding-evidence snippet is not substring-present in that
                // file's hunk. The finding is not lost: with no inline note it keeps its full summary line.
                if (!grounding.anchorIsGrounded(suggested.filePath(), extractPrimarySnippet(f))) {
                    continue;
                }
                String clean = sanitizeStudentText(suggested.body());
                if (clean.isBlank()) continue;
                // Append the transferable principle so an inline note (the primary surface for an inlinable
                // finding — its summary line collapses to a pointer once delivered) still completes the
                // formative loop rather than landing as a bare terse fix.
                String principle = principleText(f, whyBySlug, emittedWhy);
                String body = principle.isEmpty() ? clean : clean + "\n\n" + principle.strip();
                notes.add(
                    new DiffNote(
                        // Repo-relativise the anchor path so the inline note targets the same file path the
                        // summary line shows. The agent's suggested path can carry the raw repo-mount prefix
                        // (inputs/sources/scm/repo/…); the downstream poster anchors on a repo-relative diff
                        // path, so a raw-prefixed anchor mis-anchors or is dropped while the summary still
                        // names the finding.
                        repoRelative(suggested.filePath()),
                        suggested.startLine(),
                        suggested.endLine(),
                        body,
                        f.recurrenceKey()
                    )
                );
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

            // GROUNDING GUARD (M1): same drop on the synthesized branch — an ungrounded evidence.location
            // never becomes an inline anchor. The finding keeps its full summary line.
            if (!grounding.anchorIsGrounded(pathNode.asString(), extractPrimarySnippet(f))) {
                continue;
            }

            Integer endLine = null;
            JsonNode endLineNode = loc.get("endLine");
            if (endLineNode != null && endLineNode.isNumber() && endLineNode.asInt() >= startLine) {
                endLine = endLineNode.asInt();
            }

            String body = composeDiffNoteBody(f, whyBySlug, emittedWhy);
            if (body != null && !body.isBlank()) {
                // Synthesized note inherits the finding's correlation key, same as the suggested-note branch.
                // Repo-relativise the anchor path (mirrors extractPrimaryLocation and the grounding guard) so
                // the inline note targets the same repo-relative path the summary shows.
                notes.add(new DiffNote(repoRelative(pathNode.asString()), startLine, endLine, body, f.recurrenceKey()));
            }
        }

        return notes;
    }

    /**
     * Compose a diff note body — the full finding content placed inline on the diff.
     * Since the MR summary only has a compact list, the diff note carries the full detail.
     */
    @Nullable
    private static String composeDiffNoteBody(
        ValidatedObservation f,
        Map<String, String> whyBySlug,
        Set<String> emittedWhy
    ) {
        var sb = new StringBuilder();
        appendFindingHeader(sb, f, false);
        sb.append("\n\n");

        appendStudentText(sb, f.reasoning());
        appendPrinciple(sb, f, whyBySlug, emittedWhy);
        appendStudentText(sb, f.guidance());

        String body = sb.toString().strip();
        if (body.length() > PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH) {
            body = body.substring(0, PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH - 3) + "...";
        }
        return body.isBlank() ? null : body;
    }

    /**
     * Server-side grounding context for the inline-anchor guard (M1). Built from the raw two-ref diff of the
     * change under review, it answers one question: is a finding's proposed inline anchor real, or a
     * hallucinated locus that would land a confident file:line note on a student about code that isn't there?
     *
     * <p>An anchor is GROUNDED when its file is in the diff's changed-file set AND the finding's evidence
     * snippet is substring-present in that file's hunk text. A {@link #FORCE_NO_LOCUS} context (issues, which
     * have no file path at all) treats every anchor as ungrounded. An INACTIVE context (no diff supplied)
     * is a strict no-op: every anchor passes, so the existing PR delivery layout is unchanged for callers
     * that cannot produce the diff.
     *
     * @param active        whether the guard runs at all (false ⇒ no-op pass-through)
     * @param forceNoLocus  whether to reject every anchor regardless of the diff (issues have no file locus)
     * @param hunkByFile    changed file path → concatenated added/context hunk text (new-side), for the
     *                      snippet substring check
     */
    record GroundingContext(boolean active, boolean forceNoLocus, Map<String, String> hunkByFile) {
        /** The no-op context: the guard does not run and every anchor is admitted unchanged. */
        static GroundingContext none() {
            return new GroundingContext(false, false, Map.of());
        }

        /**
         * Build the guard from an artifact + its raw unified diff. ISSUE ⇒ force-no-locus (issue findings
         * carry no file anchor). PR with a non-blank diff ⇒ an active snippet/changed-file guard. PR with no
         * diff ⇒ inactive (no-op): without the diff we cannot tell grounded from hallucinated, and silently
         * dropping every anchor would be worse than the status quo, so we fall back to the pre-existing
         * downstream {@code DiffHunkValidator} line check.
         */
        static GroundingContext fromDiff(WorkArtifact artifact, @Nullable String unifiedDiff) {
            if (artifact == WorkArtifact.ISSUE) {
                return new GroundingContext(true, true, Map.of());
            }
            if (unifiedDiff == null || unifiedDiff.isBlank()) {
                return none();
            }
            return new GroundingContext(true, false, parseHunksByFile(unifiedDiff));
        }

        /**
         * Is {@code path}'s inline anchor grounded? A no-op context admits everything; a force-no-locus
         * context (issue) rejects everything. Otherwise the path must be a changed file, and — when a
         * non-blank {@code snippet} is given — that snippet (whitespace-normalised) must appear in the file's
         * hunk text. A blank/absent snippet falls back to changed-file membership alone (we have nothing to
         * substring-match, so the path being in the diff is the strongest signal available).
         */
        boolean anchorIsGrounded(@Nullable String path, @Nullable String snippet) {
            if (!active) return true;
            if (forceNoLocus) return false;
            if (path == null || path.isBlank()) return false;
            String key = repoRelative(path);
            String hunk = hunkByFile.get(key);
            if (hunk == null) {
                // Path is not in the diff's changed-file set ⇒ hallucinated locus.
                return false;
            }
            if (snippet == null || snippet.isBlank()) {
                // No snippet to verify against — the changed-file membership is the grounding we have.
                return true;
            }
            return hunk.contains(normalizeForMatch(snippet));
        }

        /**
         * Parse a unified diff into {@code newPath → concatenated new-side hunk text} (added + context
         * lines), whitespace-normalised for a tolerant substring check. Mirrors
         * {@link DiffHunkValidator#parseValidLines}'s header handling: tolerates the {@code [L<n>]} annotated
         * form and resolves the file from the {@code diff --git a/… b/<path>} header.
         */
        private static Map<String, String> parseHunksByFile(String diff) {
            Map<String, StringBuilder> acc = new HashMap<>();
            String currentFile = null;
            for (String raw : diff.split("\n", -1)) {
                String line = raw;
                if (line.startsWith("[L") && line.contains("] ")) {
                    line = line.substring(line.indexOf("] ") + 2);
                }
                if (line.startsWith("diff --git")) {
                    int bIdx = line.lastIndexOf(" b/");
                    currentFile = bIdx > 0 ? line.substring(bIdx + 3) : null;
                    if (currentFile != null) acc.putIfAbsent(currentFile, new StringBuilder());
                    continue;
                }
                if (currentFile == null) continue;
                // Collect new-side content: added (+) and context ( ) lines. Skip hunk headers, ---/+++ file
                // markers, and deletions (not present in the new file the student is reading).
                if (line.startsWith("@@") || line.startsWith("+++") || line.startsWith("---")) continue;
                if (line.startsWith("+") || line.startsWith(" ")) {
                    acc.get(currentFile).append(normalizeForMatch(line.substring(1))).append('\n');
                }
            }
            Map<String, String> out = new HashMap<>(acc.size());
            for (Map.Entry<String, StringBuilder> e : acc.entrySet()) {
                out.put(e.getKey(), e.getValue().toString());
            }
            return out;
        }

        /** Collapse all runs of whitespace to a single space and strip, so a snippet's indentation/EOL
         * quirks don't defeat the substring match. */
        private static String normalizeForMatch(String s) {
            return s.replaceAll("\\s+", " ").strip();
        }
    }
}
