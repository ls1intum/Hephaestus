package de.tum.cit.aet.hephaestus.practices.model;

/**
 * How much autonomy the system has over one practice.
 *
 * <table>
 *   <caption>Autonomy tiers</caption>
 *   <tr><th>Tier</th><th>Review runs</th><th>Observation recorded</th><th>Feedback delivered</th></tr>
 *   <tr><td>{@link #OFF}</td>     <td>no</td>  <td>no</td>  <td>no</td></tr>
 *   <tr><td>{@link #PROPOSE}</td> <td>yes</td> <td>yes</td> <td>no — held back</td></tr>
 *   <tr><td>{@link #DELIVER}</td> <td>yes</td> <td>yes</td> <td>yes, without asking</td></tr>
 * </table>
 *
 * <p>Autonomy is not reach. <em>Where</em> feedback may go is carried separately by
 * {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach}, which does not order and so cannot join
 * this ladder.
 *
 * <p>A tier is resolved, never read raw: a practice may inherit its area's, and an area the workspace's. See
 * {@link de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver}.
 */
public enum PracticeReviewTier {
    OFF,

    /**
     * Nothing measured here is lost: every finding is persisted as an observation and gets a
     * {@code SUPPRESSED} feedback row stamped
     * {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason#PRACTICE_TIER_QUIET}, so
     * the behaviour series stays unbroken where dropping the practice from reviews would put a hole in it.
     */
    PROPOSE,

    DELIVER;

    /** Width of every {@code review_tier} column; every constant name must fit. */
    public static final int MAX_LENGTH = 16;

    /**
     * The tier in force where nobody has expressed an opinion. {@link #DELIVER}, because a migration must
     * never make a system louder or quieter than the one it replaced.
     */
    public static final PracticeReviewTier DEFAULT = DELIVER;

    public boolean admitsReview() {
        return this != OFF;
    }

    /** The autonomy half of the delivery gate; {@link FeedbackAdmission} joins it with reach and origin. */
    public boolean deliversWithoutApproval() {
        return this == DELIVER;
    }
}
