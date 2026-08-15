package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import org.jspecify.annotations.Nullable;

/**
 * The single expression of "may this measurement be said out loud, here?".
 *
 * <p>Three independent axes have to agree, and they answer different questions:
 *
 * <ul>
 *   <li>{@link PracticeReviewTier#deliversWithoutApproval} — how much autonomy this practice has. A
 *       deliberate configuration choice, resolved through the practice → area → workspace chain before
 *       it gets here.
 *   <li>{@link FeedbackReach#reaches} — where this workspace lets feedback go at all. Also a deliberate
 *       choice, but one the workspace makes once rather than per practice.
 *   <li>{@link ObservationOrigin#delivers} — a fact about how the measurement was taken, not an opinion,
 *       so it is not configurable.
 * </ul>
 */
public final class FeedbackAdmission {

    private FeedbackAdmission() {}

    /**
     * Whether an observation of this provenance, for a practice at this effective tier, may be delivered on
     * this channel in a workspace with this reach.
     *
     * @param tier the practice's <em>effective</em> tier, or {@code null} when the caller could not resolve
     *     one (a failed lookup, not an unknown practice — those never get this far). Admitted either way:
     *     withholding feedback on the strength of a lookup miss is the worse failure, and the other two
     *     axes still apply.
     */
    public static boolean delivers(
        ObservationOrigin origin,
        @Nullable PracticeReviewTier tier,
        FeedbackReach reach,
        FeedbackChannel channel
    ) {
        if (!origin.delivers(channel)) {
            return false;
        }
        if (!reach.reaches(channel)) {
            return false;
        }
        return tier == null || tier.deliversWithoutApproval();
    }
}
