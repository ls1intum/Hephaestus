package de.tum.cit.aet.hephaestus.practices.review.tier;

/**
 * Which level of the practice → area → workspace chain actually decided an effective review tier.
 *
 * <p>Reported alongside every effective tier because an administrator seeing {@code DELIVER} needs to know
 * whether it is this practice's own decision — which they can reset — or inherited from somewhere else.
 */
public enum ReviewTierSource {
    /** The only source that is an override rather than an inheritance. */
    PRACTICE,

    AREA,

    /**
     * Neither the practice nor its area holds a tier, including when the practice has no area at all. The
     * workspace's own default may itself be unset, in which case it resolves to
     * {@link de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier#DEFAULT} — still reported as a
     * workspace-level answer.
     */
    WORKSPACE,
}
