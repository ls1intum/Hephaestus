package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout.REPO_MOUNT_RELATIVE;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Composes delivery content (mrNote + diffNotes) from structured findings — server-side "step 2": the
 * agent produces findings, this renders them into a human-readable MR/PR comment for students.
 *
 * <p><b>Authored-text contract.</b> The words are always somebody else's; this class only sanitises and
 * lays them out. They come from one of two places, and the order of preference is the whole point of the
 * two-phase split:
 *
 * <ol>
 *   <li>the composition stage's in-context message for the practice ({@link ComposedNotes}) — an
 *       intervention written after the measurements, by a pass that could read everything ever measured
 *       about this person and everything already said to them;</li>
 *   <li>failing that, the finding's own {@code reasoning} and {@code guidance}, written by the detector at
 *       the moment of measurement.</li>
 * </ol>
 *
 * <p>The fallback is not a degraded stub, it is the rendering that shipped before composition existed, so
 * a run that composes nothing produces exactly today's comment. That is why this lane needs no synthesised
 * content of its own and no source other than {@code AGENT}: there is nothing here that the model did not
 * write. Either author's text must: acknowledge an adjacent good signal rather than structurally censor all
 * praise (this renderer never strips such an opening clause); stay thread-aware and state-neutral rather
 * than prescribe an already-satisfied action; and never fabricate criteria, tools, or deliverables not
 * named in the artifact.
 */
class DeliveryComposer {

    /** Non-blocking (MINOR/INFO) suggestions surfaced in full before the rest collapse into an overflow line; blocking findings are never capped. */
    static final int MAX_IMPROVEMENT_SUGGESTIONS = 3;

    /**
     * Author-side process practices with no meaningful diff anchor (they critique the PR as a whole, not a
     * single changed line) — must be delivered in the summary, never as an inline note.
     */
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of(
        "describe-what-and-why",
        "commits-are-atomic-and-cohesive",
        "commit-subjects-explain-each-change"
    );

    /**
     * The "is this single issue well-formed?" near-duplicate pair: scoped-to-one-concern and
     * has-a-checkable-outcome critique the SAME framing, so when both fire as a gap we keep only the
     * highest-severity one. {@code breaks-large-work-into-trackable-subtasks} is excluded: "decompose this
     * epic" is a distinct, independently-actionable lesson.
     */
    private static final Set<String> EPIC_STRUCTURE_PRACTICES = Set.of(
        "issue-scoped-to-single-concern",
        "issue-has-checkable-outcome"
    );

    /**
     * Practice pairs that, when both fire as a gap, deliver the SAME underlying fact and must collapse to
     * ONE finding. {@code redundant-slug → preferred-slug}: when both are present the redundant one is
     * dropped, keeping the more-actionable, change-anchored one. E.g. a DoD checkbox claiming "all tests
     * pass" is the same fact {@code ships-tests-with-the-change} already owns more actionably.
     */
    private static final Map<String, String> CO_OCCURRENCE_REDUNDANT_TO_PREFERRED = Map.ofEntries(
        Map.entry("ready-and-traceable-handoff", "ships-tests-with-the-change")
    );

    /**
     * Curated short, task-level strength phrases keyed by practice slug, rendered as "Worth keeping: you're
     * <gerund>."; a slug without one falls back to a generic acknowledgement (see
     * {@link #composeSubordinatePositive}). Names what the WORK does, never the author.
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

    private static boolean isProblem(ValidatedFinding f) {
        return f.assessment() == Assessment.BAD;
    }

    private static boolean isStrength(ValidatedFinding f) {
        return f.assessment() == Assessment.GOOD;
    }

    /** Compose for a pull request (the default artifact; CTA reads "to fix before merging"). */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings) {
        return compose(findings, ArtifactKinds.PULL_REQUEST);
    }

    /**
     * Compose feedback for a specific artifact. The blocking call-to-action is artifact-aware: a PR
     * reads "to fix before merging", an ISSUE simply "to fix".
     */
    @Nullable
    static DeliveryContent compose(List<ValidatedFinding> findings, ArtifactKind artifact) {
        return compose(findings, artifact, Map.of());
    }

    /**
     * Compose with the catalogue-authored transferable principle ({@code whyBySlug}, from
     * {@code Practice.whyItMatters}) surfaced on substantive critiques — supplied by the server verbatim
     * because the model is deliberately told not to write it itself. An empty map omits the principle line.
     */
    @Nullable
    static DeliveryContent compose(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug
    ) {
        // Pre-delivery: no finding is known-delivered yet, so every inlinable finding keeps its full line.
        return compose(findings, artifact, whyBySlug, Set.of(), GroundingContext.none(), List.of());
    }

    /**
     * Compose with a server-side grounding guard: the last line of defence before a hallucinated locus
     * lands on a student as a confidently-anchored inline note. {@code unifiedDiff} is the raw diff of the
     * change under review; an inline anchor whose file is not in the diff's changed-file set, or whose
     * evidence snippet is not present in that file's hunk, is dropped — the finding still delivers in full
     * via the summary. A blank diff disables the guard (a strict no-op).
     */
    @Nullable
    static DeliveryContent compose(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        @Nullable String unifiedDiff
    ) {
        return compose(findings, artifact, whyBySlug, unifiedDiff, List.of());
    }

    /**
     * Compose preferring the composition stage's own in-context messages ({@code composed}) over the
     * measurement-time rendering, per practice. Units for the other lanes, and units this renderer cannot
     * use, are ignored rather than refused — the practice simply keeps the words the detector wrote, which
     * is the output this file produced before the stage existed.
     *
     * <p>The stage proposes and this class renders; neither admits. A unit whose practice was dropped by
     * the diff-scope filter, the loudness tier or the reaction filter arrives here with no finding left to
     * attach to and is never rendered at all.
     */
    @Nullable
    static DeliveryContent compose(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        @Nullable String unifiedDiff,
        List<ComposedFeedbackUnit> composed
    ) {
        return compose(
            findings,
            artifact,
            whyBySlug,
            Set.of(),
            GroundingContext.fromDiff(artifact, unifiedDiff),
            composed
        );
    }

    /**
     * Recomposes ONLY the MR summary body after inline notes have been posted, demoting every inlinable
     * finding whose inline comment landed (its key is in {@code deliveredKeys}) to a one-line pointer, while
     * a finding whose inline note failed keeps its full summary line. Re-runs the identical pipeline as
     * {@link #compose} so the recomposed summary cannot drift from the first pass.
     */
    @Nullable
    static String recomposeMrNote(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys
    ) {
        return recomposeMrNote(findings, artifact, whyBySlug, deliveredKeys, List.of());
    }

    /**
     * Recompose with the same composed messages the first pass was given. Passing them again is what keeps
     * the two passes identical: the units are claimed by practice in the same deterministic order both
     * times, so a summary line and the inline note it points at can never end up naming different things.
     */
    @Nullable
    static String recomposeMrNote(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys,
        List<ComposedFeedbackUnit> composed
    ) {
        DeliveryContent recomposed = compose(
            findings,
            artifact,
            whyBySlug,
            deliveredKeys,
            GroundingContext.none(),
            composed
        );
        return recomposed == null ? null : recomposed.mrNote();
    }

    @Nullable
    private static DeliveryContent compose(
        List<ValidatedFinding> findings,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys,
        GroundingContext grounding,
        List<ComposedFeedbackUnit> composed
    ) {
        if (findings == null || findings.isEmpty()) {
            return null;
        }
        // Shared across the summary and inline notes so a slug's "Why this matters" lands exactly once.
        Set<String> emittedWhy = new HashSet<>();

        // Reported on the DeliveryContent so the ledger marks these SUPPRESSED, not DELIVERED.
        List<ValidatedFinding> dedupDropped = new ArrayList<>();
        List<ValidatedFinding> capDropped = new ArrayList<>();

        List<ValidatedFinding> negatives = findings
            .stream()
            .filter(DeliveryComposer::isProblem)
            .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
            .toList();

        if (ArtifactKinds.ISSUE.equals(artifact)) {
            List<ValidatedFinding> before = negatives;
            negatives = dedupEpicStructure(negatives);
            dedupDropped.addAll(identityDiff(before, negatives));
        }

        {
            List<ValidatedFinding> before = negatives;
            negatives = dedupCoOccurringNegatives(negatives);
            dedupDropped.addAll(identityDiff(before, negatives));
        }

        // Every blocking (CRITICAL/MAJOR) finding is kept; only the non-blocking tail is capped (see
        // capImprovementTail). The capped list, not the raw one, flows into the partition and diff notes
        // below, so a dropped nudge leaves no inline comment either.
        int improvementOverflow = 0;
        long blockingTotal = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        long improvementTotal = negatives.size() - blockingTotal;
        if (improvementTotal > MAX_IMPROVEMENT_SUGGESTIONS) {
            List<ValidatedFinding> before = negatives;
            negatives = capImprovementTail(negatives);
            capDropped.addAll(identityDiff(before, negatives));
            improvementOverflow = (int) (improvementTotal - MAX_IMPROVEMENT_SUGGESTIONS);
        }

        if (negatives.isEmpty()) {
            // Ranked most-certain first so the highest-confidence reinforcements survive the cap, and so a
            // practice's single composed message is claimed by its strongest signal.
            List<ValidatedFinding> observed = findings
                .stream()
                .filter(DeliveryComposer::isStrength)
                .sorted(Comparator.comparingDouble((ValidatedFinding f) -> f.confidence()).reversed())
                .toList();
            if (observed.isEmpty()) {
                // Every finding NOT_APPLICABLE or INCONCLUSIVE: nothing was actually assessed, so deliver
                // nothing rather than a misleading "nothing to change here" all-clear.
                return null;
            }
            Rendering rendering = new Rendering(whyBySlug, emittedWhy, ComposedNotes.claim(observed, composed));
            return new DeliveryContent(composeNoIssuesNote(observed, rendering), List.of(), List.of());
        }

        // Issues carry no diff, so every issue finding must expand in full in the note itself rather than
        // demote to a diff note that silently vanishes.
        boolean inlineSupported = ArtifactKinds.hasInlineLane(artifact);
        List<ValidatedFinding> inlinable = new ArrayList<>();
        List<ValidatedFinding> nonInlinable = new ArrayList<>();
        for (ValidatedFinding f : negatives) {
            if (inlineSupported && !isNonInlinable(f)) {
                inlinable.add(f);
            } else {
                nonInlinable.add(f);
            }
        }

        List<ValidatedFinding> positives = findings.stream().filter(DeliveryComposer::isStrength).toList();

        // Claimed over the severity-sorted negatives, BEFORE either surface renders, so the summary and
        // the inline notes agree on which locus carries a practice's composed message.
        Rendering rendering = new Rendering(whyBySlug, emittedWhy, ComposedNotes.claim(negatives, composed));

        String mrNote = composeMrNote(
            positives,
            negatives,
            nonInlinable,
            inlinable,
            improvementOverflow,
            deliveredKeys,
            rendering
        );

        List<DiffNote> diffNotes = collectDiffNotes(inlinable, rendering, grounding);

        return new DeliveryContent(mrNote, diffNotes, withheldFindings(dedupDropped, capDropped));
    }

    /**
     * Set difference by reference IDENTITY, not {@code equals}: ValidatedFinding is a record, so two
     * value-equal findings must not collapse into one dropped slot.
     */
    private static List<ValidatedFinding> identityDiff(List<ValidatedFinding> before, List<ValidatedFinding> after) {
        if (before.size() == after.size()) {
            return List.of();
        }
        Set<ValidatedFinding> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(after);
        List<ValidatedFinding> dropped = new ArrayList<>(before.size() - after.size());
        for (ValidatedFinding f : before) {
            if (!kept.contains(f)) {
                dropped.add(f);
            }
        }
        return dropped;
    }

    /**
     * The dropped findings as ledger-reportable {@link WithheldFinding}s. Addressed by {@code occurrenceKey}
     * (a single observation) rather than {@code recurrenceKey} (a locus several observations share), so
     * withholding one finding can never mark a delivered sibling at the same locus as suppressed.
     */
    private static List<PracticeDetectionResultParser.WithheldFinding> withheldFindings(
        List<ValidatedFinding> dedupDropped,
        List<ValidatedFinding> capDropped
    ) {
        return Stream.concat(
            dedupDropped.stream().map(f -> withheld(f, FeedbackSuppressionReason.COMPOSER_DEDUPED)),
            capDropped.stream().map(f -> withheld(f, FeedbackSuppressionReason.VOLUME_CAPPED))
        )
            .filter(Objects::nonNull)
            .toList();
    }

    private static PracticeDetectionResultParser.@Nullable WithheldFinding withheld(
        ValidatedFinding f,
        FeedbackSuppressionReason reason
    ) {
        String key = f.occurrenceKey();
        return key == null ? null : new PracticeDetectionResultParser.WithheldFinding(key, reason);
    }

    /**
     * Keeps the FIRST {@link #EPIC_STRUCTURE_PRACTICES} finding (the list is severity-sorted, so that is
     * the highest-severity lead) and drops the rest. No-op when fewer than two are present.
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
                    continue;
                }
                epicKept = true;
            }
            kept.add(f);
        }
        return kept;
    }

    /**
     * Collapses {@link #CO_OCCURRENCE_REDUNDANT_TO_PREFERRED} pairs: when both members are present as gap
     * findings, the redundant one is dropped. A pair with only one member present is left alone.
     */
    private static List<ValidatedFinding> dedupCoOccurringNegatives(List<ValidatedFinding> negatives) {
        Set<String> present = negatives.stream().map(ValidatedFinding::practiceSlug).collect(Collectors.toSet());
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
     * Caps the non-blocking (MINOR/INFO) improvement tail to {@link #MAX_IMPROVEMENT_SUGGESTIONS}, keeping
     * the highest-severity then highest-confidence ones; every blocking finding survives uncapped. Preserves
     * incoming severity ordering.
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
        // Identity, not value-equality: ValidatedFinding is a record, so a value-set would collapse two
        // equal findings into one slot and the re-emit below would match both, overshooting the cap.
        Set<ValidatedFinding> keptImprovements = improvements
            .stream()
            .sorted(
                Comparator.comparingInt((ValidatedFinding f) -> f.severity().ordinal()).thenComparing(
                    Comparator.comparingDouble(ValidatedFinding::confidence).reversed()
                )
            )
            .limit(MAX_IMPROVEMENT_SUGGESTIONS)
            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

        List<ValidatedFinding> kept = new ArrayList<>(blocking.size() + keptImprovements.size());
        for (ValidatedFinding f : negatives) {
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
     * recorded reasoning, what it observed against each practice. Carries no self-level praise: task/process
     * level only. Also surfaces the catalogue-authored principle ({@code whyBySlug}) on the lead bullet, so
     * an above-bar student hears the standard affirmed rather than silence.
     */
    private static String composeNoIssuesNote(List<ValidatedFinding> observed, Rendering rendering) {
        // Already ranked most-certain first by the caller. A strength earns a bullet when there is
        // something to say about it — the composed message where the stage wrote one, and the
        // measurement's own reasoning where it did not.
        List<ValidatedFinding> withSomethingToSay = observed
            .stream()
            .filter(f -> rendering.noteFor(f) != null || (f.reasoning() != null && !f.reasoning().isBlank()))
            .toList();

        if (withSomethingToSay.isEmpty()) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }

        var bullets = new StringBuilder(1024);
        int shown = 0;
        boolean principleShown = false;
        for (ValidatedFinding f : withSomethingToSay) {
            if (shown >= MAX_STRENGTH_REINFORCEMENTS) break;
            ComposedNote note = rendering.noteFor(f);
            String summary = clampToSentenceBudget(
                note == null ? sanitizeStudentText(f.reasoning()).strip() : note.body(),
                STRENGTH_BUDGET
            );
            if (summary.isBlank()) {
                // Reasoning was entirely grading-meta and scrubbed to nothing — skip rather than emit a
                // bare bullet with no observation behind it.
                continue;
            }
            String label = capitalize(f.practiceSlug().replace('-', ' '));
            bullets.append("- **").append(label).append(":** ").append(summary);
            // Bare, empty, or "No change needed." forward text degrades gracefully to just the observation.
            String forward = clampToSentenceBudget(
                note == null ? sanitizeStudentText(f.guidance() == null ? "" : f.guidance()).strip() : note.nextStep(),
                STRENGTH_BUDGET
            );
            if (!forward.isBlank() && !forward.replace(".", "").equalsIgnoreCase("No change needed")) {
                bullets.append(' ').append(forward);
            }
            bullets.append("\n");
            if (!principleShown) {
                String why = strengthPrincipleText(f, rendering);
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
        return "What's working well here, and how to keep building on it:\n\n" + bullets + "\n";
    }

    /**
     * The catalogue "Why this matters" line for a STRENGTH finding, or {@code ""} when there is none to
     * surface. Unlike {@link #principleText}, does not skip on INFO severity \u2014 a strength finding carries
     * INFO by construction. Deduped once-per-delivery via the shared {@code emittedWhy} ledger.
     */
    private static String strengthPrincipleText(ValidatedFinding f, Rendering rendering) {
        String why = rendering.whyBySlug().get(f.practiceSlug());
        if (why == null || why.isBlank()) {
            return "";
        }
        if (!rendering.emittedWhy().add(f.practiceSlug())) {
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

    /** Short, task-level strength phrases keyed by practice slug, used to acknowledge what the work already
     * does well before listing improvements. */
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
     * Builds a one-sentence strengths acknowledgement from up to two GOOD findings, e.g. "Nice work keeping
     * the change focused and reviewable and linking the change to its issue — a couple of things to
     * tighten:". Returns "" when there are no positives.
     */
    static String composeAcknowledgement(List<ValidatedFinding> positives, int improvementCount) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        // Curated gerund phrases only — a raw slug ("triages the issue …") breaks the "Nice work keeping X
        // and [Ying]" grammar, so an un-phrased strength is simply not named in the opener.
        List<String> phrases = positives
            .stream()
            .map(f -> STRENGTH_PHRASES.get(f.practiceSlug()))
            .filter(p -> p != null && !p.isBlank())
            .distinct()
            .limit(2)
            .toList();
        // Counts the IMPROVEMENTS that follow, not the strengths named — a single strength in front of two
        // suggestions must not read "one thing to tighten:" above a list of two.
        String tail = improvementCount > 1 ? " — a couple of things to tighten:" : " — one thing to tighten:";
        if (phrases.isEmpty()) {
            // A real GOOD strength exists but none has a curated phrase — acknowledge generically rather
            // than drop the opener or dump the raw slug into the "Nice work <gerund>" frame.
            return "Nice work here" + tail;
        }
        String strengths = phrases.size() == 1 ? phrases.get(0) : phrases.get(0) + " and " + phrases.get(1);
        return "Nice work " + strengths + tail;
    }

    /**
     * The single earned strength line allowed alongside blocking issues: one brief acknowledgement of the
     * run's highest-confidence GOOD finding, rendered after the issue count — not a feedback sandwich that
     * buries the critique. A slug without a curated phrase falls back to a generic line rather than
     * dropping the acknowledgement or dumping a raw slug.
     */
    static String composeSubordinatePositive(List<ValidatedFinding> positives) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        // Most-certain GOOD finding first, so the single line we are allowed lands on the strongest signal.
        return positives
            .stream()
            .filter(DeliveryComposer::isStrength)
            .max(Comparator.comparingDouble(ValidatedFinding::confidence))
            .map(DeliveryComposer::subordinateStrengthLine)
            .orElse("");
    }

    /** Renders one GOOD finding as the subordinate "Worth keeping: …" line (curated phrase or generic fallback). */
    private static String subordinateStrengthLine(ValidatedFinding f) {
        String phrase = SUBORDINATE_STRENGTH_PHRASES.get(f.practiceSlug());
        if (phrase != null && !phrase.isBlank()) {
            return "Worth keeping: you're " + phrase + ".";
        }
        return "Worth keeping: there's solid work here to build on.";
    }

    /**
     * Matches the whitespace run that separates two sentences. Used to tokenise student text while
     * preserving the original separator, so Markdown lists and headings keep their newlines instead of
     * being folded onto one line.
     */
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Strips internal grading vocabulary from student-facing text. Delegates to the shared
     * {@link StudentTextSanitizer} so every composer runs the same scrub. Package-visible for the tests.
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

    /** Non-inlinable if the practice is inherently so, or the finding has neither a usable evidence
     * location nor an agent-supplied {@code suggestedDiffNote}. */
    private static boolean isNonInlinable(ValidatedFinding f) {
        if (NON_INLINABLE_PRACTICES.contains(f.practiceSlug())) {
            return true;
        }
        if (!f.suggestedDiffNotes().isEmpty()) {
            return false;
        }
        String location = extractPrimaryLocation(f);
        return location == null;
    }

    /**
     * Compose the MR note: opening counts, non-inlinable findings in full, then a brief overview of inline
     * findings. Pure — the inline overview reacts only to the injected {@code deliveredKeys} set. A finding
     * whose inline comment landed collapses to a "see inline comments" pointer; one whose note did not land
     * keeps its full summary line, so a delivery failure still reaches the student somewhere.
     */
    static String composeMrNote(
        List<ValidatedFinding> positives,
        List<ValidatedFinding> allNegatives,
        List<ValidatedFinding> nonInlinable,
        List<ValidatedFinding> inlinable,
        int improvementOverflow,
        Set<String> deliveredKeys,
        Rendering rendering
    ) {
        var sb = new StringBuilder(4096);

        // Suppressed when there is a blocking issue: front-loading praise ahead of a serious problem
        // reads as a hollow "feedback sandwich".
        boolean hasBlocking = allNegatives
            .stream()
            .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR);
        if (!hasBlocking) {
            String acknowledgement = composeAcknowledgement(positives, allNegatives.size());
            if (!acknowledgement.isEmpty()) {
                sb.append(acknowledgement).append("\n\n");
            }
        }

        composeOpening(sb, allNegatives, improvementOverflow);

        // The multi-strength opener above is suppressed when blocking, but one earned acknowledgement
        // still lands, subordinate, after the issue count rather than as a sandwich opener.
        if (hasBlocking) {
            String reinforcement = composeSubordinatePositive(positives);
            if (!reinforcement.isEmpty()) {
                sb.append(reinforcement).append("\n\n");
            }
        }

        for (int i = 0; i < nonInlinable.size(); i++) {
            composeFinding(sb, nonInlinable.get(i), rendering);
            if (i < nonInlinable.size() - 1 || !inlinable.isEmpty()) {
                sb.append("---\n\n");
            }
        }

        // The label is emitted whenever the list is non-empty, not gated on nonInlinable, so a PR with
        // only inline findings doesn't show an unlabeled wall of headers.
        if (!inlinable.isEmpty()) {
            // A null/blank correlation key can never match a delivered key (Set.of().contains(null) also
            // throws), so a keyless finding is always treated as undelivered.
            List<ValidatedFinding> undelivered = inlinable
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
            for (ValidatedFinding f : undelivered) {
                appendFindingHeader(sb, f, true, rendering);
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static void composeOpening(StringBuilder sb, List<ValidatedFinding> negatives, int improvementOverflow) {
        // Hephaestus never gates a merge, so the call-to-action is state-neutral feed-forward ("to tighten"),
        // not "to fix before merging"; merge-state is not plumbed into the composer.
        String blockingCta = " to tighten";
        long blockingCount = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        // negatives is the CAPPED list; the collapsed remainder is disclosed via improvementOverflow.
        long improvementCount = negatives.size() - blockingCount;
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
     *
     * <p>The severity emoji is always the measurement's: the composer carries no verdict and may not
     * restate one. The title is the composed message's where there is one, because naming the issue is part
     * of what was composed, and a summary line that named the issue differently from the note it points at
     * would read as two problems.
     */
    private static void appendFindingHeader(
        StringBuilder sb,
        ValidatedFinding f,
        boolean withLocation,
        Rendering rendering
    ) {
        ComposedNote note = rendering.noteFor(f);
        String title = note == null || note.title() == null ? f.title() : note.title();
        sb.append("**").append(severityEmoji(f.severity())).append(" ").append(title).append("**");
        if (withLocation) {
            String location = extractPrimaryLocation(f);
            if (location != null) {
                sb.append(" · `").append(location).append("`");
            }
        }
    }

    private static void composeFinding(StringBuilder sb, ValidatedFinding f, Rendering rendering) {
        appendFindingHeader(sb, f, true, rendering);
        sb.append("\n\n");

        if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
            String snippet = extractPrimarySnippet(f);
            // Suppress the "You wrote:" quote when it carries grader mechanics instead of the student's
            // own artifact (the agent sometimes drops pipeline plumbing into the evidence field). And
            // metadata-field findings (title/body spans, flags) do not echo a quote at all: the agent's
            // metadata span is frequently a truncated heading or serialized boolean that reads as broken
            // output, so the quote is gated on a real code location too.
            if (snippet != null && !containsGraderMechanics(snippet) && extractPrimaryLocation(f) != null) {
                sb.append("You wrote:\n");
                sb.append("```").append(detectLanguage(f)).append("\n").append(snippet).append("\n```\n\n");
            }
        }

        appendBody(sb, f, rendering);
    }

    /**
     * The words of one finding: the composition stage's in-context message for its practice where there is
     * one, and the measurement-time {@code reasoning} + {@code guidance} where there is not. The catalogue's
     * transferable principle sits between the two halves either way — it is authored by the catalogue and
     * spliced by the server, and neither author is asked to write it.
     */
    private static void appendBody(StringBuilder sb, ValidatedFinding f, Rendering rendering) {
        ComposedNote note = rendering.noteFor(f);
        appendStudentText(sb, note == null ? f.reasoning() : note.body());
        appendPrinciple(sb, f, rendering);
        appendStudentText(sb, note == null ? f.guidance() : note.nextStep());
    }

    /**
     * Surfaces the catalogue-authored transferable principle ({@code Practice.whyItMatters}) as a "Why this
     * matters" line between the observation and the forward step. Pulled verbatim from the catalogue, never
     * model-generated, so it cannot fabricate or drift. Emitted at most once per practice slug per delivery,
     * and never on an INFO nudge. A blocking (CRITICAL/MAJOR) critique keeps its principle every time;
     * advisory (MINOR) critiques get at most one across the whole delivery, so a craft-heavy note lands a
     * single teaching moment rather than a wall of rationale.
     */
    private static void appendPrinciple(StringBuilder sb, ValidatedFinding f, Rendering rendering) {
        sb.append(principleText(f, rendering));
    }

    /**
     * Sentinel marker tracked in {@code emittedWhy} once a non-blocking (advisory) principle line has been
     * surfaced this delivery, so only the lead advisory critique carries one. Not a valid practice slug
     * (slugs match {@code SandboxLayout.PRACTICE_SLUG}), so it can never collide with a real entry.
     */
    private static final String ADVISORY_PRINCIPLE_SHOWN = " advisory-principle-shown";

    /**
     * The "Why this matters" line for {@code f}, or {@code ""} when it should not be surfaced (INFO nudge,
     * no authored principle, already emitted this delivery, or a second advisory principle). Mutates
     * {@code emittedWhy} on success. See {@link #appendPrinciple}.
     */
    private static String principleText(ValidatedFinding f, Rendering rendering) {
        if (f.severity() == Severity.INFO) {
            return "";
        }
        String why = rendering.whyBySlug().get(f.practiceSlug());
        if (why == null || why.isBlank()) {
            return "";
        }
        Set<String> emittedWhy = rendering.emittedWhy();
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
        JsonNode citations = evidence.get("citations");
        if (citations == null || !citations.isArray() || citations.isEmpty()) return null;
        JsonNode first = citations.get(0);
        if (!first.isObject()) return null;
        String sourceKind = first.path("sourceKind").asString();
        if (!sourceKind.equals("scm.pull-request.diff") && !sourceKind.equals("scm.repository.tree")) return null;
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
        JsonNode citations = evidence.get("citations");
        if (citations == null || !citations.isArray() || citations.isEmpty()) return null;
        JsonNode quote = citations.get(0).get("quote");
        if (quote == null || !quote.isString()) return null;
        String snippet = quote.asString();
        return (snippet != null && !snippet.isBlank()) ? snippet.strip() : null;
    }

    /**
     * Collect inline diff notes from BAD findings. Prefers the agent's {@code suggestedDiffNotes}, falling
     * back to a synthesized note from the first evidence location when the agent did not supply one.
     */
    private static List<DiffNote> collectDiffNotes(
        List<ValidatedFinding> negatives,
        Rendering rendering,
        GroundingContext grounding
    ) {
        List<DiffNote> notes = new ArrayList<>();

        for (ValidatedFinding f : negatives) {
            if (notes.size() >= PracticeDetectionResultParser.MAX_DELIVERY_DIFF_NOTES) break;

            // At most ONE inline note per finding (its primary anchor) — several near-identical notes for
            // the same lesson reads as nagging.
            if (!f.suggestedDiffNotes().isEmpty()) {
                DiffNote suggested = f.suggestedDiffNotes().get(0);
                if (!grounding.anchorIsGrounded(suggested.filePath(), extractPrimarySnippet(f))) {
                    continue;
                }
                // A composed message supersedes the agent's measurement-time note whole — its words, and
                // the next step it ends on — while the placement stays the one the finding proposed.
                // Without one, sanitize the agent's own note: its body is raw model output just the same
                // and can echo grading-meta.
                String body;
                if (rendering.noteFor(f) != null) {
                    body = composeDiffNoteBody(f, rendering);
                } else {
                    String clean = sanitizeStudentText(suggested.body());
                    if (clean.isBlank()) continue;
                    String principle = principleText(f, rendering);
                    body = principle.isEmpty() ? clean : clean + "\n\n" + principle.strip();
                }
                if (body == null || body.isBlank()) continue;
                notes.add(
                    new DiffNote(
                        // The agent's suggested path can carry the raw repo-mount prefix; the downstream
                        // poster anchors on a repo-relative path, so a raw-prefixed anchor mis-anchors.
                        repoRelative(suggested.filePath()),
                        suggested.startLine(),
                        suggested.endLine(),
                        body,
                        f.recurrenceKey()
                    )
                );
                continue;
            }

            JsonNode evidence = f.evidence();
            if (evidence == null || evidence.isNull()) continue;
            JsonNode citations = evidence.get("citations");
            if (citations == null || !citations.isArray() || citations.isEmpty()) continue;

            JsonNode citation = citations.get(0);
            if (!citation.isObject()) continue;
            JsonNode pathNode = citation.get("path");
            JsonNode startLineNode = citation.get("startLine");
            if (pathNode == null || !pathNode.isString()) continue;
            if (startLineNode == null || !startLineNode.isNumber()) continue;
            int startLine = startLineNode.asInt();
            if (startLine <= 0) continue;

            if (!grounding.anchorIsGrounded(pathNode.asString(), extractPrimarySnippet(f))) {
                continue;
            }

            Integer endLine = null;
            JsonNode endLineNode = citation.get("endLine");
            if (endLineNode != null && endLineNode.isNumber() && endLineNode.asInt() >= startLine) {
                endLine = endLineNode.asInt();
            }

            String body = composeDiffNoteBody(f, rendering);
            if (body != null && !body.isBlank()) {
                notes.add(new DiffNote(repoRelative(pathNode.asString()), startLine, endLine, body, f.recurrenceKey()));
            }
        }

        return notes;
    }

    /** Compose a diff note body — the full finding content placed inline on the diff. */
    @Nullable
    private static String composeDiffNoteBody(ValidatedFinding f, Rendering rendering) {
        var sb = new StringBuilder();
        appendFindingHeader(sb, f, false, rendering);
        sb.append("\n\n");

        appendBody(sb, f, rendering);

        String body = sb.toString().strip();
        if (body.length() > PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH) {
            body = body.substring(0, PracticeDetectionResultParser.MAX_DIFF_NOTE_BODY_LENGTH - 3) + "...";
        }
        return body.isBlank() ? null : body;
    }

    /**
     * The per-delivery rendering inputs, carried together because all three are shared by the summary and
     * the inline notes and must agree between them: the catalogue's transferable principles, the running
     * ledger of which ones have already landed this delivery, and the composition stage's own messages.
     */
    record Rendering(Map<String, String> whyBySlug, Set<String> emittedWhy, ComposedNotes notes) {
        /** The composed message this finding renders, or null to fall back to its own reasoning + guidance. */
        @Nullable
        ComposedNote noteFor(ValidatedFinding f) {
            return notes.byFinding().get(f);
        }
    }

    /**
     * The composition stage's in-context messages, each already assigned to the one finding that renders it.
     *
     * <p><b>One practice, one message, one locus.</b> The stage writes at most one in-context unit per
     * practice: where several loci of the same practice fired, it writes the note for the most consequential
     * and names the rest in prose. Assigning that unit up front — to the first finding of its practice in
     * the severity-sorted order both passes walk — is what stops the same words appearing at two anchors,
     * and what keeps the summary line and the inline note it points at from naming different things.
     */
    record ComposedNotes(Map<ValidatedFinding, ComposedNote> byFinding) {
        /** No composition ran, or it wrote nothing for this lane: every finding renders its own text. */
        static ComposedNotes none() {
            return new ComposedNotes(Map.of());
        }

        /**
         * Assign the usable units to findings, keyed by IDENTITY: {@link ValidatedFinding} is a record, so
         * two value-equal findings at two loci must not share one slot.
         *
         * <p>A unit is usable only if it is an in-context message with words in it. {@code WITHHOLD}
         * deliberately is not: silencing an in-context note is the server's decision, and it owes the ledger
         * a {@link FeedbackSuppressionReason} for it that the composer's own reasons do not map onto —
         * honouring one here would drop the note and write no row to explain it. Everything unusable simply
         * leaves its practice on the measurement-time rendering.
         */
        static ComposedNotes claim(List<ValidatedFinding> ordered, List<ComposedFeedbackUnit> units) {
            if (units.isEmpty()) {
                return none();
            }
            Map<String, ComposedNote> unclaimed = new HashMap<>();
            for (ComposedFeedbackUnit unit : units) {
                if (
                    unit.channel() != FeedbackChannel.IN_CONTEXT ||
                    unit.action() == ComposedFeedbackUnit.Action.WITHHOLD
                ) {
                    continue;
                }
                ComposedNote note = ComposedNote.of(unit);
                if (note != null) {
                    unclaimed.putIfAbsent(unit.practiceSlug(), note);
                }
            }
            Map<ValidatedFinding, ComposedNote> byFinding = new IdentityHashMap<>();
            for (ValidatedFinding f : ordered) {
                ComposedNote note = unclaimed.remove(f.practiceSlug());
                if (note != null) {
                    byFinding.put(f, note);
                }
            }
            return new ComposedNotes(byFinding);
        }
    }

    /**
     * One composed in-context message, scrubbed and clamped, laid out exactly where the measurement-time
     * text would otherwise go: {@code title} in the header, {@code body} where {@code reasoning} sits, and
     * {@code nextStep} where {@code guidance} sits.
     *
     * @param title null when the scrub emptied it, in which case the finding's own title stands
     * @param nextStep may be blank — the message still stands on its body alone
     */
    record ComposedNote(@Nullable String title, String body, String nextStep) {
        /**
         * The unit as this renderer will lay it out, or null when the scrub left nothing to say.
         *
         * <p>Scrubbed and clamped here even though the parser already did both. This is raw model output
         * exactly as {@code guidance} is, {@link DeliveryComposer#compose} is reachable with units from any
         * source, and a composed body that reached a student on a path that never ran the scrub would be
         * the one hole in it. A body the scrub empties yields null rather than an empty note, so the
         * practice falls back and the developer is still told.
         */
        @Nullable
        static ComposedNote of(ComposedFeedbackUnit unit) {
            String body = clamp(sanitizeStudentText(unit.body()), ComposedFeedbackUnit.MAX_BODY_LENGTH);
            if (body.isBlank()) {
                return null;
            }
            String title = clamp(sanitizeStudentText(unit.title()), ComposedFeedbackUnit.MAX_TITLE_LENGTH);
            String nextStep = clamp(sanitizeStudentText(unit.nextStep()), ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH);
            return new ComposedNote(title.isBlank() ? null : title, body, nextStep);
        }
    }

    /** Hard character clamp on the ledger's own column widths, re-applied to text this class did not parse. */
    private static String clamp(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength).strip();
    }

    /**
     * Server-side grounding context for the inline-anchor guard: is a finding's proposed inline anchor
     * real, or a hallucinated locus that would land a confident file:line note about code that isn't there?
     * An anchor is grounded when its file is in the diff's changed-file set and the finding's evidence
     * snippet is present in that file's hunk text.
     *
     * @param active       whether the guard runs at all (false ⇒ no-op pass-through, no diff was supplied)
     * @param forceNoLocus reject every anchor regardless of the diff (issues have no file locus)
     * @param hunkByFile   changed file path → concatenated added/context hunk text (new-side)
     */
    record GroundingContext(boolean active, boolean forceNoLocus, Map<String, String> hunkByFile) {
        /** The no-op context: the guard does not run and every anchor is admitted unchanged. */
        static GroundingContext none() {
            return new GroundingContext(false, false, Map.of());
        }

        /**
         * ISSUE ⇒ force-no-locus. PR with a diff ⇒ active guard. PR with no diff ⇒ inactive: without the
         * diff we cannot tell grounded from hallucinated, so we fall back to the downstream
         * {@code DiffHunkValidator} line check rather than silently drop every anchor.
         */
        static GroundingContext fromDiff(ArtifactKind artifact, @Nullable String unifiedDiff) {
            if (ArtifactKinds.ISSUE.equals(artifact)) {
                return new GroundingContext(true, true, Map.of());
            }
            if (unifiedDiff == null || unifiedDiff.isBlank()) {
                return none();
            }
            return new GroundingContext(true, false, parseHunksByFile(unifiedDiff));
        }

        /**
         * A no-op context admits everything; force-no-locus (issue) rejects everything. Otherwise the path
         * must be a changed file, and a non-blank snippet must appear (whitespace-normalised) in that
         * file's hunk text; a blank snippet falls back to changed-file membership alone.
         */
        boolean anchorIsGrounded(@Nullable String path, @Nullable String snippet) {
            if (!active) return true;
            if (forceNoLocus) return false;
            if (path == null || path.isBlank()) return false;
            String key = repoRelative(path);
            String hunk = hunkByFile.get(key);
            if (hunk == null) {
                return false;
            }
            if (snippet == null || snippet.isBlank()) {
                return true;
            }
            return hunk.contains(normalizeForMatch(snippet));
        }

        /**
         * Parse a unified diff into {@code newPath → concatenated new-side hunk text}. Mirrors
         * {@link DiffHunkValidator#parseValidLines}'s header handling: tolerates the {@code [L<n>]}
         * annotated form and resolves the file from the {@code diff --git a/… b/<path>} header.
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
                // New-side only: skip hunk headers, file markers, and deletions.
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
