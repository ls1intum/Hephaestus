package de.tum.cit.aet.hephaestus.practices.review.tier;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the practice → area → workspace inheritance chain. The one definition of what a review tier
 * <em>is</em> for a given practice.
 *
 * <p><b>Why a chain at all.</b> A tier column that cannot be null cannot express "I have not decided", so
 * every practice ships holding an opinion it was never asked for — and turning the system down means editing
 * every row by hand. At forty practices that is tedious; at a hundred across twenty-five areas nobody does
 * it, so the shipped default is the only configuration that ever runs. Null is the fix: it is the difference
 * between a decision and the absence of one, and it lets one decision at the top cover everything below it
 * that has not disagreed.
 *
 * <p><b>Resolution is server-side, always.</b> A client that resolved the chain itself would be a second
 * implementation of it, and the two would drift on the first change. Callers get the effective tier and the
 * level it came from.
 *
 * <p>Pure and static: resolution is a function of three nullable values and nothing else. That is what lets
 * every caller — the detection gate, both delivery gates, the catalogue, the trace and the rollup — share one
 * implementation rather than each pushing a {@code COALESCE} into its own query. A second expression of the
 * chain in SQL would agree with this one only until the next change to either, and a rollup that disagrees
 * with the rows beneath it is worse than no rollup at all.
 */
public final class ReviewTierResolver {

    private ReviewTierResolver() {}

    /**
     * The workspace's own answer, which is the bottom of the chain.
     *
     * <p>An unset workspace default resolves to {@link PracticeReviewTier#DEFAULT} rather than to a
     * fleet-wide property. A tier is a statement about how one team wants to be spoken to; there is nothing
     * useful for an instance-wide setting to say about that, and a fourth level would only make the chain
     * harder to explain for a value that is already the constant every level bottoms out at.
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
     * @param practiceTier the practice's raw column, null when it holds no opinion
     * @param areaTier its area's raw column, null when the area holds no opinion <em>or</em> there is no area
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
     * Resolves a practice entity's effective tier.
     *
     * <p>Touches {@code practice.getArea()}, which is a lazy association. Call it where the area is already
     * fetched — every repository method that feeds a resolution site declares
     * {@code @EntityGraph(attributePaths = {"area", ...})} for exactly this reason.
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
