package de.tum.cit.aet.hephaestus.agent.handler;

import static de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout.REPO_MOUNT_RELATIVE;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DeliveryContent;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedObservation;
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

/** Renders admitted observations and composed in-context feedback for artifact delivery. */
class DeliveryComposer {

    static final int MAX_DIFF_NOTE_BODY_LENGTH = 2_000;

    static final int MAX_IMPROVEMENT_SUGGESTIONS = 3;
    static final Set<String> NON_INLINABLE_PRACTICES = Set.of(
        "describe-what-and-why",
        "commits-are-atomic-and-cohesive",
        "commit-subjects-explain-each-change"
    );
    private static final Set<String> EPIC_STRUCTURE_PRACTICES = Set.of(
        "issue-scoped-to-single-concern",
        "issue-has-checkable-outcome"
    );
    private static final Map<String, String> CO_OCCURRENCE_REDUNDANT_TO_PREFERRED = Map.ofEntries(
        Map.entry("ready-and-traceable-handoff", "ships-tests-with-the-change")
    );
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

    private static String repoRelative(String path) {
        return path.startsWith(REPO_MOUNT_RELATIVE) ? path.substring(REPO_MOUNT_RELATIVE.length()) : path;
    }

    private static boolean isProblem(ValidatedObservation f) {
        return f.assessment() == Assessment.BAD;
    }

    private static boolean isStrength(ValidatedObservation f) {
        return f.assessment() == Assessment.GOOD;
    }

    @Nullable
    static DeliveryContent compose(@Nullable List<ValidatedObservation> observations) {
        return compose(observations, ArtifactKinds.PULL_REQUEST);
    }

    @Nullable
    static DeliveryContent compose(@Nullable List<ValidatedObservation> observations, ArtifactKind artifact) {
        return compose(observations, artifact, Map.of());
    }

    @Nullable
    static DeliveryContent compose(
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug
    ) {
        // Pre-delivery: no observation is known-delivered yet, so every inlinable observation keeps its full line.
        return compose(observations, artifact, whyBySlug, Set.of(), GroundingContext.none(), List.of());
    }

    @Nullable
    static DeliveryContent compose(
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        @Nullable String unifiedDiff
    ) {
        return compose(observations, artifact, whyBySlug, unifiedDiff, List.of());
    }

    @Nullable
    static DeliveryContent compose(
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        @Nullable String unifiedDiff,
        List<ComposedFeedbackUnit> composed
    ) {
        return compose(
            observations,
            artifact,
            whyBySlug,
            Set.of(),
            GroundingContext.fromDiff(artifact, unifiedDiff),
            composed
        );
    }

    @Nullable
    static String recomposeMrNote(
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys
    ) {
        return recomposeMrNote(observations, artifact, whyBySlug, deliveredKeys, List.of());
    }

    @Nullable
    static String recomposeMrNote(
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys,
        List<ComposedFeedbackUnit> composed
    ) {
        DeliveryContent recomposed = compose(
            observations,
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
        @Nullable List<ValidatedObservation> observations,
        ArtifactKind artifact,
        Map<String, String> whyBySlug,
        Set<String> deliveredKeys,
        GroundingContext grounding,
        List<ComposedFeedbackUnit> composed
    ) {
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        // Shared across the summary and inline notes so a slug's "Why this matters" lands exactly once.
        Set<String> emittedWhy = new HashSet<>();

        // Reported on the DeliveryContent so the ledger marks these SUPPRESSED, not DELIVERED.
        List<ValidatedObservation> dedupDropped = new ArrayList<>();
        List<ValidatedObservation> capDropped = new ArrayList<>();

        List<ValidatedObservation> negatives = observations
            .stream()
            .filter(DeliveryComposer::isProblem)
            .sorted(Comparator.comparingInt(f -> severity(f).ordinal()))
            .toList();

        if (ArtifactKinds.ISSUE.equals(artifact)) {
            List<ValidatedObservation> before = negatives;
            negatives = dedupEpicStructure(negatives);
            dedupDropped.addAll(identityDiff(before, negatives));
        }

        {
            List<ValidatedObservation> before = negatives;
            negatives = dedupCoOccurringNegatives(negatives);
            dedupDropped.addAll(identityDiff(before, negatives));
        }

        // Every blocking (CRITICAL/MAJOR) observation is kept; only the non-blocking tail is capped (see
        // capImprovementTail). The capped list, not the raw one, flows into the partition and diff notes
        // below, so a dropped nudge leaves no inline comment either.
        int improvementOverflow = 0;
        long blockingTotal = negatives
            .stream()
            .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR)
            .count();
        long improvementTotal = negatives.size() - blockingTotal;
        if (improvementTotal > MAX_IMPROVEMENT_SUGGESTIONS) {
            List<ValidatedObservation> before = negatives;
            negatives = capImprovementTail(negatives);
            capDropped.addAll(identityDiff(before, negatives));
            improvementOverflow = (int) (improvementTotal - MAX_IMPROVEMENT_SUGGESTIONS);
        }

        if (negatives.isEmpty()) {
            // Ranked best-attested first, so the strengths that survive the cap are the ones we saw in the
            // most of the work, and so a practice's single composed message is claimed by its widest
            // signal. Strengths carry no severity, so breadth is the only ranking dimension there is here.
            List<ValidatedObservation> observed = observations
                .stream()
                .filter(DeliveryComposer::isStrength)
                .sorted(ObservationOrder.bestAttestedFirst())
                .toList();
            if (observed.isEmpty()) {
                // Every observation NOT_APPLICABLE or INCONCLUSIVE: nothing was actually assessed, so deliver
                // nothing rather than a misleading "nothing to change here" all-clear.
                return null;
            }
            Rendering rendering = new Rendering(whyBySlug, emittedWhy, ComposedNotes.claim(observed, composed));
            return new DeliveryContent(composeNoIssuesNote(observed, rendering), List.of(), List.of());
        }

        // Issues carry no diff, so every issue observation must expand in full in the note itself rather than
        // demote to a diff note that silently vanishes.
        boolean inlineSupported = ArtifactKinds.hasInlineLane(artifact);
        List<ValidatedObservation> inlinable = new ArrayList<>();
        List<ValidatedObservation> nonInlinable = new ArrayList<>();
        for (ValidatedObservation f : negatives) {
            if (inlineSupported && !isNonInlinable(f) && !hasArtifactPlacement(f.practiceSlug(), composed)) {
                inlinable.add(f);
            } else {
                nonInlinable.add(f);
            }
        }

        List<ValidatedObservation> positives = observations.stream().filter(DeliveryComposer::isStrength).toList();

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

        return new DeliveryContent(mrNote, diffNotes, withheldObservations(dedupDropped, capDropped));
    }

    private static boolean hasArtifactPlacement(String practiceSlug, List<ComposedFeedbackUnit> composed) {
        return composed
            .stream()
            .anyMatch(
                unit ->
                    unit.channel() == FeedbackChannel.IN_CONTEXT &&
                    unit.action() != ComposedFeedbackUnit.Action.WITHHOLD &&
                    unit.practiceSlug().equals(practiceSlug) &&
                    unit.placement() != null &&
                    unit.placement().kind() == ComposedFeedbackUnit.InContextPlacement.PlacementKind.ARTIFACT
            );
    }

    private static List<ValidatedObservation> identityDiff(
        List<ValidatedObservation> before,
        List<ValidatedObservation> after
    ) {
        if (before.size() == after.size()) {
            return List.of();
        }
        Set<ValidatedObservation> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(after);
        List<ValidatedObservation> dropped = new ArrayList<>(before.size() - after.size());
        for (ValidatedObservation f : before) {
            if (!kept.contains(f)) {
                dropped.add(f);
            }
        }
        return dropped;
    }

    private static List<PracticeDetectionResultParser.WithheldObservation> withheldObservations(
        List<ValidatedObservation> dedupDropped,
        List<ValidatedObservation> capDropped
    ) {
        return Stream.concat(
            dedupDropped.stream().map(f -> withheld(f, FeedbackSuppressionReason.COMPOSER_DEDUPED)),
            capDropped.stream().map(f -> withheld(f, FeedbackSuppressionReason.VOLUME_CAPPED))
        )
            .filter(Objects::nonNull)
            .toList();
    }

    private static PracticeDetectionResultParser.@Nullable WithheldObservation withheld(
        ValidatedObservation f,
        FeedbackSuppressionReason reason
    ) {
        String key = f.occurrenceKey();
        return key == null ? null : new PracticeDetectionResultParser.WithheldObservation(key, reason);
    }

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
                    continue;
                }
                epicKept = true;
            }
            kept.add(f);
        }
        return kept;
    }

    private static List<ValidatedObservation> dedupCoOccurringNegatives(List<ValidatedObservation> negatives) {
        Set<String> present = negatives.stream().map(ValidatedObservation::practiceSlug).collect(Collectors.toSet());
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
        // Identity, not value-equality: ValidatedObservation is a record, so a value-set would collapse two
        // equal observations into one slot and the re-emit below would match both, overshooting the cap.
        Set<ValidatedObservation> keptImprovements = improvements
            .stream()
            .sorted(ObservationOrder.worstFirstUnstored())
            .limit(MAX_IMPROVEMENT_SUGGESTIONS)
            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

        List<ValidatedObservation> kept = new ArrayList<>(blocking.size() + keptImprovements.size());
        for (ValidatedObservation f : negatives) {
            if (blocking.contains(f) || keptImprovements.contains(f)) {
                kept.add(f);
            }
        }
        return kept;
    }

    private static final int MAX_STRENGTH_REINFORCEMENTS = 3;
    private static final int STRENGTH_BUDGET = 280;

    private static String composeNoIssuesNote(List<ValidatedObservation> observed, Rendering rendering) {
        // Already ranked most-certain first by the caller. A strength earns a bullet when there is
        // something to say about it — the composed message where the stage wrote one, and the
        // measurement's own reasoning where it did not.
        List<ValidatedObservation> withSomethingToSay = observed
            .stream()
            .filter(
                f -> rendering.noteFor(f) != null || (f.evidenceRationale() != null && !f.evidenceRationale().isBlank())
            )
            .toList();

        if (withSomethingToSay.isEmpty()) {
            return "Reviewed against the active practices \u2014 nothing to change here.\n";
        }

        var bullets = new StringBuilder(1024);
        int shown = 0;
        boolean principleShown = false;
        for (ValidatedObservation f : withSomethingToSay) {
            if (shown >= MAX_STRENGTH_REINFORCEMENTS) break;
            ComposedNote note = rendering.noteFor(f);
            String summary = clampToSentenceBudget(
                note == null ? sanitizeStudentText(f.evidenceRationale()).strip() : note.title(),
                STRENGTH_BUDGET
            );
            if (summary.isBlank()) {
                // Reasoning was entirely grading-meta and scrubbed to nothing — skip rather than emit a
                // bare bullet with no observation behind it.
                continue;
            }
            String label = capitalize(f.practiceSlug().replace('-', ' '));
            bullets.append("- **").append(label).append(":** ").append(summary);
            // Only a composed message carries a forward step; a measurement writes none, so an uncomposed
            // strength is the observation alone. Bare, empty, or "No change needed." text degrades to the
            // same thing.
            String forward = clampToSentenceBudget(note == null ? "" : note.nextStep(), STRENGTH_BUDGET);
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

    private static String strengthPrincipleText(ValidatedObservation f, Rendering rendering) {
        String why = rendering.whyBySlug().get(f.practiceSlug());
        if (why == null || why.isBlank()) {
            return "";
        }
        if (!rendering.emittedWhy().add(f.practiceSlug())) {
            return ""; // this practice's principle already surfaced earlier in the same delivery
        }
        return "_Why this matters:_ " + sanitizeStudentText(why).strip();
    }

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

    static String composeAcknowledgement(List<ValidatedObservation> positives, int improvementCount) {
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

    static String composeSubordinatePositive(List<ValidatedObservation> positives) {
        if (positives == null || positives.isEmpty()) {
            return "";
        }
        return positives
            .stream()
            .filter(DeliveryComposer::isStrength)
            .min(ObservationOrder.bestAttestedFirst())
            .map(DeliveryComposer::subordinateStrengthLine)
            .orElse("");
    }

    private static String subordinateStrengthLine(ValidatedObservation f) {
        String phrase = SUBORDINATE_STRENGTH_PHRASES.get(f.practiceSlug());
        if (phrase != null && !phrase.isBlank()) {
            return "Worth keeping: you're " + phrase + ".";
        }
        return "Worth keeping: there's solid work here to build on.";
    }

    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("(?<=[.!?])\\s+");

    static String sanitizeStudentText(@Nullable String text) {
        return StudentTextSanitizer.sanitize(text);
    }

    static String stripEnvelopeCorruption(String text) {
        return StudentTextSanitizer.stripEnvelopeCorruption(text);
    }

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

    private static boolean isNonInlinable(ValidatedObservation f) {
        if (NON_INLINABLE_PRACTICES.contains(f.practiceSlug())) {
            return true;
        }
        String location = extractPrimaryLocation(f);
        return location == null;
    }

    static String composeMrNote(
        List<ValidatedObservation> positives,
        List<ValidatedObservation> allNegatives,
        List<ValidatedObservation> nonInlinable,
        List<ValidatedObservation> inlinable,
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
            composeObservation(sb, nonInlinable.get(i), rendering);
            if (i < nonInlinable.size() - 1 || !inlinable.isEmpty()) {
                sb.append("---\n\n");
            }
        }

        // The label is emitted whenever the list is non-empty, not gated on nonInlinable, so a PR with
        // only inline observations doesn't show an unlabeled wall of headers.
        if (!inlinable.isEmpty()) {
            // A null/blank correlation key can never match a delivered key (Set.of().contains(null) also
            // throws), so a keyless observation is always treated as undelivered.
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
                appendObservationHeader(sb, f, true, rendering);
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

    private static void appendObservationHeader(
        StringBuilder sb,
        ValidatedObservation f,
        boolean withLocation,
        Rendering rendering
    ) {
        ComposedNote note = rendering.noteFor(f);
        String title = note == null || note.title() == null ? f.summary() : note.title();
        sb.append("**").append(severityEmoji(severity(f))).append(" ").append(title).append("**");
        if (withLocation) {
            String location = extractPrimaryLocation(f);
            if (location != null) {
                sb.append(" · `").append(location).append("`");
            }
        }
    }

    private static void composeObservation(StringBuilder sb, ValidatedObservation f, Rendering rendering) {
        appendObservationHeader(sb, f, true, rendering);
        sb.append("\n\n");

        if (f.severity() == Severity.CRITICAL || f.severity() == Severity.MAJOR) {
            String snippet = extractPrimarySnippet(f);
            // Suppress the "You wrote:" quote when it carries grader mechanics instead of the student's
            // own artifact (the agent sometimes drops pipeline plumbing into the evidence field). And
            // metadata-field observations (title/body spans, flags) do not echo a quote at all: the agent's
            // metadata span is frequently a truncated heading or serialized boolean that reads as broken
            // output, so the quote is gated on a real code location too.
            if (snippet != null && !containsGraderMechanics(snippet) && extractPrimaryLocation(f) != null) {
                sb.append("You wrote:\n");
                sb.append("```").append(detectLanguage(f)).append("\n").append(snippet).append("\n```\n\n");
            }
        }

        appendBody(sb, f, rendering);
    }

    private static void appendBody(StringBuilder sb, ValidatedObservation f, Rendering rendering) {
        ComposedNote note = rendering.noteFor(f);
        if (note == null) appendStudentText(sb, f.evidenceRationale());
        appendPrinciple(sb, f, rendering);
        if (note != null) {
            appendStudentText(sb, note.nextStep());
        }
    }

    private static void appendPrinciple(StringBuilder sb, ValidatedObservation f, Rendering rendering) {
        sb.append(principleText(f, rendering));
    }

    private static final String ADVISORY_PRINCIPLE_SHOWN = " advisory-principle-shown";

    private static String principleText(ValidatedObservation f, Rendering rendering) {
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

    private static boolean containsGraderMechanics(@Nullable String text) {
        return StudentTextSanitizer.isGradingMeta(text);
    }

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

    private static Severity severity(ValidatedObservation observation) {
        return Objects.requireNonNull(observation.severity(), "A problem observation must have a severity");
    }

    @Nullable
    private static String extractPrimaryLocation(ValidatedObservation f) {
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
    private static String extractPrimarySnippet(ValidatedObservation f) {
        JsonNode evidence = f.evidence();
        if (evidence == null || evidence.isNull()) return null;
        JsonNode citations = evidence.get("citations");
        if (citations == null || !citations.isArray() || citations.isEmpty()) return null;
        JsonNode quote = citations.get(0).get("quote");
        if (quote == null || !quote.isString()) return null;
        String snippet = quote.asString();
        return (snippet != null && !snippet.isBlank()) ? snippet.strip() : null;
    }

    private static List<DiffNote> collectDiffNotes(
        List<ValidatedObservation> negatives,
        Rendering rendering,
        GroundingContext grounding
    ) {
        List<DiffNote> notes = new ArrayList<>();

        for (ValidatedObservation f : negatives) {
            if (notes.size() >= PracticeDetectionResultParser.MAX_DELIVERY_DIFF_NOTES) break;

            JsonNode evidence = f.evidence();
            if (evidence == null || evidence.isNull()) continue;
            JsonNode citations = evidence.get("citations");
            if (citations == null || !citations.isArray() || citations.isEmpty()) continue;

            ComposedNote composed = rendering.noteFor(f);
            ComposedFeedbackUnit.ResolvedAnchor selected =
                composed == null ||
                composed.placement().kind() != ComposedFeedbackUnit.InContextPlacement.PlacementKind.DIFF
                    ? null
                    : composed.placement().diffAnchor();
            int citationIndex = selected == null ? 0 : selected.citationIndex();
            if (citationIndex < 0 || citationIndex >= citations.size()) continue;
            JsonNode citation = citations.get(citationIndex);
            if (!citation.isObject()) continue;
            String path = selected == null ? citation.path("path").asString(null) : selected.path();
            int startLine = selected == null ? citation.path("startLine").asInt(0) : selected.startLine();
            if (path == null) continue;
            if (startLine <= 0) continue;

            String snippet = citation.path("quote").asString(null);
            if (!grounding.anchorIsGrounded(path, snippet)) {
                continue;
            }

            Integer endLine =
                selected == null ? integerAtLeast(citation.get("endLine"), startLine) : selected.endLine();

            String body = composeDiffNoteBody(f, rendering);
            if (body != null && !body.isBlank()) {
                notes.add(new DiffNote(repoRelative(path), startLine, endLine, body, f.recurrenceKey()));
            }
        }

        return notes;
    }

    private static @Nullable Integer integerAtLeast(@Nullable JsonNode value, int minimum) {
        return value != null && value.isNumber() && value.asInt() >= minimum ? value.asInt() : null;
    }

    private static @Nullable String composeDiffNoteBody(ValidatedObservation f, Rendering rendering) {
        var words = new StringBuilder();
        appendBody(words, f, rendering);
        if (words.toString().isBlank()) {
            return null;
        }

        var sb = new StringBuilder();
        appendObservationHeader(sb, f, false, rendering);
        sb.append("\n\n").append(words);

        String body = sb.toString().strip();
        if (body.length() > MAX_DIFF_NOTE_BODY_LENGTH) {
            body = closeDanglingCodeFence(clampToSentenceBudget(body, MAX_DIFF_NOTE_BODY_LENGTH));
        }
        return body.isBlank() ? null : body;
    }

    /** Closes a fenced code block left open by the inline length limit. */
    static String closeDanglingCodeFence(String text) {
        long fences = text
            .lines()
            .filter(line -> line.stripLeading().startsWith("```"))
            .count();
        return fences % 2 == 0 ? text : text + "\n```";
    }

    record Rendering(Map<String, String> whyBySlug, Set<String> emittedWhy, ComposedNotes notes) {
        @Nullable
        ComposedNote noteFor(ValidatedObservation f) {
            return notes.byObservation().get(f);
        }
    }

    record ComposedNotes(Map<ValidatedObservation, ComposedNote> byObservation) {
        static ComposedNotes none() {
            return new ComposedNotes(Map.of());
        }

        static ComposedNotes claim(List<ValidatedObservation> ordered, List<ComposedFeedbackUnit> units) {
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
            Map<ValidatedObservation, ComposedNote> byObservation = new IdentityHashMap<>();
            for (ValidatedObservation f : ordered) {
                ComposedNote note = unclaimed.remove(f.practiceSlug());
                if (note != null) {
                    byObservation.put(f, note);
                }
            }
            return new ComposedNotes(byObservation);
        }
    }

    record ComposedNote(String title, String nextStep, ComposedFeedbackUnit.InContextPlacement placement) {
        @Nullable
        static ComposedNote of(ComposedFeedbackUnit unit) {
            String title = clamp(sanitizeStudentText(unit.title()), ComposedFeedbackUnit.MAX_TITLE_LENGTH);
            String nextStep = clamp(sanitizeStudentText(unit.nextStep()), ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH);
            return title.isBlank() || nextStep.isBlank() || unit.placement() == null
                ? null
                : new ComposedNote(title, nextStep, unit.placement());
        }
    }

    private static String clamp(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength).strip();
    }

    /** Server-derived diff content used to validate model-selected inline anchors. */
    record GroundingContext(boolean active, boolean forceNoLocus, Map<String, String> hunkByFile) {
        static GroundingContext none() {
            return new GroundingContext(false, false, Map.of());
        }

        static GroundingContext fromDiff(ArtifactKind artifact, @Nullable String unifiedDiff) {
            if (ArtifactKinds.ISSUE.equals(artifact)) {
                return new GroundingContext(true, true, Map.of());
            }
            if (unifiedDiff == null || unifiedDiff.isBlank()) {
                return none();
            }
            return new GroundingContext(true, false, parseHunksByFile(unifiedDiff));
        }

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
                    acc
                        .computeIfAbsent(currentFile, ignored -> new StringBuilder())
                        .append(normalizeForMatch(line.substring(1)))
                        .append('\n');
                }
            }
            Map<String, String> out = new HashMap<>(acc.size());
            for (Map.Entry<String, StringBuilder> e : acc.entrySet()) {
                out.put(e.getKey(), e.getValue().toString());
            }
            return out;
        }

        private static String normalizeForMatch(String s) {
            return s.replaceAll("\\s+", " ").strip();
        }
    }
}
