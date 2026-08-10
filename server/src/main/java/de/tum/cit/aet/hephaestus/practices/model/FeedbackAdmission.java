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
 *       deliberate configuration choice, changeable at any time, and resolved through the practice → area →
 *       workspace chain before it gets here.
 *   <li>{@link FeedbackReach#reaches} — where this workspace lets feedback go at all. Also a deliberate
 *       choice, but one the workspace makes once rather than per practice.
 *   <li>{@link ObservationOrigin#delivers} — a fact about how the measurement was taken. Not configurable,
 *       because it is not an opinion: a finding about a pull request merged last quarter does not become
 *       actionable by turning a dial.
 * </ul>
 *
 * <p>Conjoined here rather than at each delivery site, so a new channel, tier, reach or origin has one place
 * to be reasoned about and a test can enumerate the whole product.
 */
public final class FeedbackAdmission {

    private FeedbackAdmission() {}

    /**
     * Whether an observation of this provenance, for a practice at this effective tier, may be delivered on
     * this channel in a workspace with this reach.
     *
     * @param tier the practice's <em>effective</em> tier, already resolved through the practice → area →
     *     workspace chain, or {@code null} when it could not be resolved — an unknown practice is admitted
     *     on the tier axis, because withholding feedback a developer was owed on the strength of a lookup
     *     miss is the worse failure. The other two axes still apply: both are known without any per-practice
     *     lookup at all.
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
