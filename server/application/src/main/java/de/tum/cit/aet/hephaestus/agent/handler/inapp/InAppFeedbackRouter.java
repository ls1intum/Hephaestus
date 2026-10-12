package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import de.tum.cit.aet.hephaestus.agent.handler.ResultOrder;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionInputs;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomyPolicy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.result.LatestRun;
import de.tum.cit.aet.hephaestus.practices.result.ReviewedWorkKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class InAppFeedbackRouter {

    /**
     * How long the same practice stays quiet on this surface after it was last shown. A way of working does not
     * change week to week; re-posting it every time a pull request lands turns a private surface into
     * nagging and teaches the developer to stop opening it.
     */
    public static final int RESURFACE_COOLDOWN_DAYS = 14;

    /**
     * How far back a pattern may reach for its evidence. Matches the reflective surface and the review
     * history staged into the sandbox, so what the composer read and what the router counts are the same
     * window.
     */
    public static final int PATTERN_WINDOW_DAYS = 90;

    private InAppFeedbackRouter() {}

    public static InAppRoutingDecision route(
            ComposedInAppMessage message,
            List<PracticeResult> evidence,
            @Nullable PracticeAutonomy autonomy,
            ActorRole subjectRole,
            @Nullable Instant lastSurfaced,
            Instant now) {
        if (!message.isComplete()) {
            return InAppRoutingDecision.INCOMPLETE;
        }
        return routeEvidence(evidence, autonomy, subjectRole, lastSurfaced, now);
    }

    /**
     * What {@link #route} decides from the evidence and the page's context alone. Composition reads it before a
     * message is written; preparation decides again through {@link #route}.
     */
    public static InAppRoutingDecision routeEvidence(
            List<PracticeResult> evidence,
            @Nullable PracticeAutonomy autonomy,
            ActorRole subjectRole,
            @Nullable Instant lastSurfaced,
            Instant now) {
        // Checked before anything else about the evidence: a message about a practice whose results cannot
        // be attributed to a named person must not be shown to that person, whatever else is true of it.
        // MERGER attribution is enforced by PracticeCatalogInjector.subjectNameable at admission.
        if (subjectRole != ActorRole.AUTHOR && subjectRole != ActorRole.MERGER && subjectRole != ActorRole.REVIEWER) {
            return InAppRoutingDecision.UNATTRIBUTABLE_SUBJECT;
        }
        List<PracticeResult> problems = problemsIn(evidence);
        if (problems.isEmpty()) {
            return InAppRoutingDecision.NO_EVIDENCE;
        }
        // Origin is asked of the evidence, not of the run: what makes a pattern claim sound is how the
        // measurements behind it were taken, and a live run can compose over a backfilled record.
        ResultOrigin origin = weakestOrigin(problems);
        if (!PracticeAutonomyPolicy.delivers(origin, autonomy, FeedbackChannel.IN_APP)) {
            return InAppRoutingDecision.PRACTICE_REQUIRES_APPROVAL;
        }
        if (problems.stream().allMatch(o -> o.getOrigin() == ResultOrigin.BACKFILL)) {
            return InAppRoutingDecision.BACKFILL_HELD;
        }
        if (distinctArtifacts(problems) < FeedbackCompositionInputs.MIN_DISTINCT_ARTIFACTS) {
            return InAppRoutingDecision.UNCORROBORATED;
        }
        if (lastSurfaced != null && lastSurfaced.isAfter(now.minus(Duration.ofDays(RESURFACE_COOLDOWN_DAYS)))) {
            return InAppRoutingDecision.RECENTLY_SURFACED;
        }
        return InAppRoutingDecision.ADMIT;
    }

    /**
     * The provenance the whole cluster is only as strong as. A cluster containing one backfilled
     * measurement is a live cluster; a cluster containing only backfilled ones is a backfilled one, and
     * {@link InAppRoutingDecision#BACKFILL_HELD} answers it below. LIVE is returned for an empty
     * cluster, which the caller has already refused.
     */
    private static ResultOrigin weakestOrigin(List<PracticeResult> problems) {
        return problems.stream().anyMatch(o -> o.getOrigin() != ResultOrigin.BACKFILL)
                ? ResultOrigin.LIVE
                : ResultOrigin.BACKFILL;
    }

    /**
     * The subset of a practice's measurements that a message about a recurring problem may stand on:
     * one row per piece of work whose newest review recorded a problem, in the window's order.
     *
     * <p>Public because it is the single definition of "the evidence" for this lane, and both the
     * decision here and the rows bound to the written unit must use it. Binding the unfiltered window
     * instead would list, under "the pieces of work this way of working was observed on", work where the practice
     * was done well — which reads as a false accusation to the one person who knows it is false. The same
     * goes for a pull request whose re-review came back clean: a piece of work counts once, at its newest
     * review ({@link LatestRun}), so a problem a later run no longer found is neither counted nor cited.
     *
     * <p>The row cited is the newest live run's: a requested review is a self-selected sample and raises no card,
     * but its verdict still says whether the work has the problem now, so a requested re-review that came back clean
     * drops the work and one that found the problem again keeps the live row. A backfilled run never supersedes
     * a live or requested one, and its problem stands only for work neither has judged.
     *
     * <p>Which of a run's several problems on one piece of work stands for it is decided by
     * {@link ResultOrder#worstFirst()}, not by the order the rows came back in: they all carry that
     * run's moment, so repository order would let the example the developer is shown be the mildest problem
     * on that work. The pieces of work keep the window's order.
     */
    public static List<PracticeResult> problemsIn(List<PracticeResult> evidence) {
        List<PracticeResult> unbackfilled = evidence.stream()
                .filter(practiceResult -> practiceResult.getOrigin() != ResultOrigin.BACKFILL)
                .toList();
        Set<ReviewedWorkKey> judged =
                unbackfilled.stream().map(ReviewedWorkKey::of).collect(Collectors.toSet());
        Set<ReviewedWorkKey> stillProblems = LatestRun.perWork(unbackfilled).stream()
                .filter(InAppFeedbackRouter::isProblem)
                .map(ReviewedWorkKey::of)
                .collect(Collectors.toSet());
        Set<UUID> citable = new HashSet<>();
        LatestRun.perWork(withOrigin(evidence, ResultOrigin.LIVE)).stream()
                .filter(practiceResult ->
                        isProblem(practiceResult) && stillProblems.contains(ReviewedWorkKey.of(practiceResult)))
                .forEach(practiceResult -> citable.add(practiceResult.getId()));
        LatestRun.perWork(withOrigin(evidence, ResultOrigin.BACKFILL)).stream()
                .filter(practiceResult ->
                        isProblem(practiceResult) && !judged.contains(ReviewedWorkKey.of(practiceResult)))
                .forEach(practiceResult -> citable.add(practiceResult.getId()));

        Comparator<PracticeResult> worstFirst = ResultOrder.worstFirst();
        Map<ReviewedWorkKey, PracticeResult> worstPerWork = new LinkedHashMap<>();
        for (PracticeResult practiceResult : evidence) {
            if (!citable.contains(practiceResult.getId())) {
                continue;
            }
            worstPerWork.merge(
                    ReviewedWorkKey.of(practiceResult),
                    practiceResult,
                    (kept, next) -> worstFirst.compare(next, kept) < 0 ? next : kept);
        }
        return List.copyOf(worstPerWork.values());
    }

    private static boolean isProblem(PracticeResult practiceResult) {
        return practiceResult.getOutcome() == Outcome.NOT_MET;
    }

    private static List<PracticeResult> withOrigin(List<PracticeResult> evidence, ResultOrigin origin) {
        return evidence.stream()
                .filter(practiceResult -> practiceResult.getOrigin() == origin)
                .toList();
    }

    /** How many separate pieces of work carry the problem — the unit of proof at the process level. */
    public static int distinctArtifacts(List<PracticeResult> problems) {
        return (int) problems.stream().map(ReviewedWorkKey::of).distinct().count();
    }
}
