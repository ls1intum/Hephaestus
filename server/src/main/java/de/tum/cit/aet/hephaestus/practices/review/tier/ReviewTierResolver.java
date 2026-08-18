package de.tum.cit.aet.hephaestus.practices.review.tier;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the practice → area → workspace inheritance chain: a null column means "not decided", and one
 * decision at the top covers everything below it that hasn't disagreed.
 *
 * <p>Pure and static so every caller shares one implementation rather than each pushing its own
 * {@code COALESCE}, which would agree with this one only until the next change to either.
 */
public final class ReviewTierResolver {

    private ReviewTierResolver() {}

    /**
     * The bottom of the chain. An unset default resolves to {@link PracticeReviewTier#DEFAULT} rather than
     * a fleet-wide property, since a tier is a statement about how one team wants to be spoken to.
     */
    public static PracticeReviewTier workspaceDefault(@Nullable PracticeReviewTier workspaceOverride) {
        return workspaceOverride != null ? workspaceOverride : PracticeReviewTier.DEFAULT;
    }

    /** Resolves an area's own effective tier: its override, else the workspace's. */
    public static EffectiveReviewTier resolveArea(
        @Nullable PracticeReviewTier areaTier,
        PracticeReviewTier workspaceDefault
    ) {
        return areaTier != null
            ? new EffectiveReviewTier(areaTier, ReviewTierSource.AREA)
            : new EffectiveReviewTier(workspaceDefault, ReviewTierSource.WORKSPACE);
    }

    /** Resolves an area entity's effective tier. A null area is the workspace's answer. */
    public static EffectiveReviewTier resolveArea(@Nullable PracticeArea area, PracticeReviewTier workspaceDefault) {
        return resolveArea(area == null ? null : area.getReviewTier(), workspaceDefault);
    }

    /**
     * Resolves a practice's effective tier: its own override, else its area's, else the workspace's.
     *
     * @param areaTier null when the area holds no opinion <em>or</em> there is no area
     */
    public static EffectiveReviewTier resolvePractice(
        @Nullable PracticeReviewTier practiceTier,
        @Nullable PracticeReviewTier areaTier,
        PracticeReviewTier workspaceDefault
    ) {
        if (practiceTier != null) {
            return new EffectiveReviewTier(practiceTier, ReviewTierSource.PRACTICE);
        }
        return resolveArea(areaTier, workspaceDefault);
    }

    /**
     * Touches {@code practice.getArea()}, a lazy association — call it only where the area is already
     * fetched (every repository method feeding a resolution site declares
     * {@code @EntityGraph(attributePaths = {"area", ...})}).
     */
    public static EffectiveReviewTier resolvePractice(Practice practice, PracticeReviewTier workspaceDefault) {
        PracticeArea area = practice.getArea();
        return resolvePractice(practice.getReviewTier(), area == null ? null : area.getReviewTier(), workspaceDefault);
    }

    /** Shorthand for the common case where only the tier itself is wanted. */
    public static PracticeReviewTier effectiveTierOf(Practice practice, PracticeReviewTier workspaceDefault) {
        return resolvePractice(practice, workspaceDefault).tier();
    }
}
