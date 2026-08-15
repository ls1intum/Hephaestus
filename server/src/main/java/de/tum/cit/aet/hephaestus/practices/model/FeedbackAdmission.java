package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import org.jspecify.annotations.Nullable;

/**
 * The single expression of "may this measurement be said out loud, here?".
 *
 * <p>Two independent axes have to agree, and they answer different questions:
 *
 * <ul>
 *   <li>{@link PracticeReviewTier#deliversWithoutApproval} — how much autonomy this practice has. A
 *       deliberate configuration choice, resolved through the practice → area → workspace chain before
 *       it gets here.
 *   <li>{@link ObservationOrigin#delivers} — a fact about how the measurement was taken, not an opinion,
 *       so it is not configurable.
 * </ul>
 */
public final class FeedbackAdmission {

    private FeedbackAdmission() {}

    /**
     * Whether an observation of this provenance, for a practice at this effective tier, may be delivered on
     * this channel.
     *
     * @param tier the practice's <em>effective</em> tier, or {@code null} when the caller resolved none.
     *     That is always a lookup miss and never an unknown practice: both review handlers run
     *     {@code PracticeDetectionDeliveryService#deliver}, which refuses any finding whose slug is not
     *     among the job's admitted revisions, before anything reaches this gate. A miss is admitted on this
     *     axis, because withholding feedback a developer was owed on the strength of it is the worse
     *     failure; the provenance axis is unaffected and still applies.
     */
    public static boolean delivers(
        ObservationOrigin origin,
        @Nullable PracticeReviewTier tier,
        FeedbackChannel channel
    ) {
        if (!origin.delivers(channel)) {
            return false;
        }
        return tier == null || tier.deliversWithoutApproval();
    }
}
