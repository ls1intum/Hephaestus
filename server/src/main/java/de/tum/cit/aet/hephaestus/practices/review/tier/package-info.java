/**
 * Review-tier resolution — {@code ReviewTierResolver}, {@code EffectiveReviewTier},
 * {@code ReviewTierSource}, {@code ReviewTierRollupService}.
 *
 * <p>A nested package is its own Modulith boundary, not an extension of its parent's grant, so this
 * declaration is what makes the resolver callable from outside — needed because the {@code agent} module
 * resolves a practice's effective tier at both delivery gates and on the review path.
 */
@org.springframework.modulith.NamedInterface("review-tier")
package de.tum.cit.aet.hephaestus.practices.review.tier;
