package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.FeedbackAdmission;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether one composed process-level message may be shown on its recipient's reflection surface.
 *
 * <p>Pure: it is handed the message, the recipient's own measurements behind it, and the two facts a
 * query has already established (the practice's effective tier, and when this practice was last surfaced
 * to this person). It reads nothing and writes nothing, so every rule below is a unit test with no
 * database in it.
 *
 * <p>Admission runs through the same {@link FeedbackAdmission#delivers} predicate the other two lanes
 * use, so "may we say this here" cannot drift per lane; everything after it is what makes the
 * reflection lane the <em>process</em> level rather than the task level.
 */
public final class ReflectionFeedbackRouter {

    /**
     * Distinct artifacts a problem must appear on before it is a pattern. One occurrence is a task-level
     * note and belongs on the work itself; claiming a habit from it would be the surface asserting more
     * than the evidence carries. Mirrors {@code ObservationService.CORROBORATION_TARGETS}, which already
     * holds the same line on the reflective read model.
     */
    public static final int CORROBORATION_ARTIFACTS = 2;

    /**
     * How long the same practice stays quiet on this surface after it was last shown. A habit does not
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

    private ReflectionFeedbackRouter() {}

    /**
     * Route one message.
     *
     * @param message      what the composition stage wrote
     * @param evidence     the recipient's own observations of this message's practice inside
     *                     {@link #PATTERN_WINDOW_DAYS}, already workspace- and recipient-scoped and
     *                     already filtered to what the visibility policy permits
     * @param tier         the practice's <em>effective</em> tier, or {@code null} when the caller resolved
     *                     none. Passed in rather than read off a lazy association, for the same reason
     *                     {@code FeedbackChannelRouter} does it.
     * @param subjectRole  whose conduct the practice's occasion judges
     * @param lastSurfaced when a REFLECTION unit for this practice was last written for this recipient, or
     *                     {@code null} if never
     * @param now          the clock, injected so the cooldown is testable
     */
    public static ReflectionRoutingDecision route(
        ComposedReflectionMessage message,
        List<Observation> evidence,
        @Nullable PracticeReviewTier tier,
        ActorRole subjectRole,
        @Nullable Instant lastSurfaced,
        Instant now
    ) {
        if (!message.isComplete()) {
            return ReflectionRoutingDecision.INCOMPLETE;
        }
        // Checked before anything else about the evidence: a message about a practice whose results may
        // be filed against the wrong person must not be shown to that person, whatever else is true of it.
        if (subjectRole != ActorRole.AUTHOR) {
            return ReflectionRoutingDecision.REVIEWER_ATTRIBUTED;
        }
        List<Observation> problems = problemsIn(evidence);
        if (problems.isEmpty()) {
            return ReflectionRoutingDecision.NO_EVIDENCE;
        }
        // Provenance and tier in one predicate, shared with the in-context and conversation lanes.
        // Origin is asked of the evidence, not of the run: what makes a pattern claim sound is how the
        // measurements behind it were taken, and a live run can compose over a backfilled record.
        ObservationOrigin origin = weakestOrigin(problems);
        if (!FeedbackAdmission.delivers(origin, tier, FeedbackChannel.REFLECTION)) {
            return ReflectionRoutingDecision.PRACTICE_TIER_QUIET;
        }
        if (problems.stream().allMatch(o -> o.getOrigin() == ObservationOrigin.BACKFILL)) {
            return ReflectionRoutingDecision.BACKFILL_HELD;
        }
        if (distinctArtifacts(problems) < CORROBORATION_ARTIFACTS) {
            return ReflectionRoutingDecision.UNCORROBORATED;
        }
        if (lastSurfaced != null && lastSurfaced.isAfter(now.minus(Duration.ofDays(RESURFACE_COOLDOWN_DAYS)))) {
            return ReflectionRoutingDecision.RECENTLY_SURFACED;
        }
        return ReflectionRoutingDecision.ADMIT;
    }

    /**
     * The provenance the whole cluster is only as strong as. A cluster containing one backfilled
     * measurement is a live cluster; a cluster containing only backfilled ones is a backfilled one, and
     * {@link ReflectionRoutingDecision#BACKFILL_HELD} answers it below. LIVE is returned for an empty
     * cluster, which the caller has already refused.
     */
    private static ObservationOrigin weakestOrigin(List<Observation> problems) {
        return problems.stream().anyMatch(o -> o.getOrigin() != ObservationOrigin.BACKFILL)
            ? ObservationOrigin.LIVE
            : ObservationOrigin.BACKFILL;
    }

    /**
     * The subset of a practice's measurements that a message about a recurring problem may stand on:
     * the ones that actually recorded a problem.
     *
     * <p>Public because it is the single definition of "the evidence" for this lane, and both the
     * decision here and the rows bound to the written unit must use it. Binding the unfiltered window
     * instead would list, under "the pieces of work this habit was observed on", work where the practice
     * was done well — which reads as a false accusation to the one person who knows it is false.
     */
    public static List<Observation> problemsIn(List<Observation> evidence) {
        return evidence
            .stream()
            .filter(o -> o.getPresence() != null && o.getPresence().carriesValence())
            .filter(o -> o.getAssessment() == Assessment.BAD)
            .toList();
    }

    /** How many separate pieces of work carry the problem — the unit of proof at the process level. */
    public static int distinctArtifacts(List<Observation> problems) {
        Set<String> artifacts = new HashSet<>();
        for (Observation problem : problems) {
            if (problem.getArtifactKind() == null || problem.getArtifactId() == null) {
                continue;
            }
            artifacts.add(problem.getArtifactKind().value() + ":" + problem.getArtifactId());
        }
        return artifacts.size();
    }
}
