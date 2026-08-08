package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import org.jspecify.annotations.Nullable;

/**
 * The single expression of "may this measurement be said out loud, here?".
 *
 * <p>Two independent axes have to agree, and they answer different questions:
 *
 * <ul>
 *   <li>{@link PracticeReviewTier#delivers} — the workspace's standing policy on how loud this practice
 *       is allowed to be. A deliberate configuration choice, changeable at any time.
 *   <li>{@link ObservationOrigin#delivers} — a fact about how the measurement was taken. Not
 *       configurable, because it is not an opinion: a finding about a pull request merged last quarter
 *       does not become actionable by turning a dial.
 * </ul>
 *
 * <p>Conjoined here rather than at each delivery site, so a new channel, tier or origin has one place
 * to be reasoned about and a test can enumerate the whole product.
 */
public final class FeedbackAdmission {

    private FeedbackAdmission() {}

    /**
     * Whether an observation of this provenance, for a practice at this tier, may be delivered on this
     * channel.
     *
     * @param tier the practice's loudness tier, or {@code null} when it could not be resolved — an
     *     unknown practice is admitted, because withholding feedback a developer was owed on the
     *     strength of a lookup miss is the worse failure. The origin still applies: it is known without
     *     any lookup at all.
     */
    public static boolean delivers(
        ObservationOrigin origin,
        @Nullable PracticeReviewTier tier,
        FeedbackChannel channel
    ) {
        if (!origin.delivers(channel)) {
            return false;
        }
        return tier == null || tier.delivers(channel);
    }
}
