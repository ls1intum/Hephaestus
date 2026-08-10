/**
 * Review-tier resolution — {@code ReviewTierResolver}, {@code EffectiveReviewTier},
 * {@code ReviewTierSource}, {@code ReviewTierRollupService}.
 *
 * <p>Exposed as its own named interface because the {@code agent} module has to resolve a practice's
 * effective tier at both delivery gates and on the review path. Reading the raw column instead would ask a
 * practice that holds no opinion for one, and the whole point of the chain is that most of them hold none.
 *
 * <p>A nested package under {@code review} is a separate module boundary to Modulith, not an extension of
 * its parent's grant, so this declaration is what makes the resolver callable at all from outside.
 */
@org.springframework.modulith.NamedInterface("review-tier")
package de.tum.cit.aet.hephaestus.practices.review.tier;
