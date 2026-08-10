package de.tum.cit.aet.hephaestus.practices.review.tier;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;

/**
 * One resolved answer to "how much autonomy does this practice have here", together with where the answer
 * came from.
 *
 * @param tier the tier actually in force — never null, because the chain always bottoms out at
 *     {@link PracticeReviewTier#DEFAULT}
 * @param source the level that decided it
 */
public record EffectiveReviewTier(PracticeReviewTier tier, ReviewTierSource source) {}
