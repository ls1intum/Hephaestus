package de.tum.cit.aet.hephaestus.practices.review.tier;

/**
 * Which level of the practice → area → workspace chain actually decided an effective review tier.
 *
 * <p>Reported alongside every effective tier because the effective value alone cannot be rendered: an
 * administrator looking at a practice showing {@code DELIVER} needs to know whether that is this practice's
 * own decision — which they can reset — or the area's, or the workspace's, which they would have to change
 * somewhere else. Deriving it in the client would mean the client re-implementing the chain.
 */
public enum ReviewTierSource {
    /** The practice holds its own tier. This is the only source that is an override rather than an inheritance. */
    PRACTICE,

    /** The practice holds no tier and its area does. */
    AREA,

    /**
     * Neither the practice nor its area holds a tier — or the practice has no area at all, which falls
     * straight through to here. The workspace's own default may itself be unset, in which case it resolves
     * to {@link de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier#DEFAULT}; that is still a
     * workspace-level answer and is reported as one, so the chain the UI renders is exactly three levels
     * deep rather than three-and-a-hidden-one.
     */
    WORKSPACE,
}
