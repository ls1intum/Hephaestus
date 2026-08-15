package de.tum.cit.aet.hephaestus.practices.model;

/**
 * How much autonomy the system has over one practice — the settable form of the system's own principle
 * that a measurement and an intervention are separate decisions.
 *
 * <p>The tiers are ordered by autonomy, and each one names what it adds to the one before it:
 *
 * <table>
 *   <caption>Autonomy tiers</caption>
 *   <tr><th>Tier</th><th>Review runs</th><th>Observation recorded</th><th>Feedback delivered</th></tr>
 *   <tr><td>{@link #OFF}</td>     <td>no</td>  <td>no</td>  <td>no</td></tr>
 *   <tr><td>{@link #PROPOSE}</td> <td>yes</td> <td>yes</td> <td>no — held back</td></tr>
 *   <tr><td>{@link #DELIVER}</td> <td>yes</td> <td>yes</td> <td>yes, without asking</td></tr>
 * </table>
 *
 * <p><b>Three modes, not four.</b> A rung an administrator cannot tell apart from its neighbour is not a
 * rung. Splitting the middle — say, into one tier that prepares nothing and one that prepares feedback for
 * approval — buys nothing an administrator can see, because both send the same thing: nothing. One middle
 * rung, {@link #PROPOSE}, carries the whole of "runs, records, says nothing".
 *
 * <p><b>Autonomy, not reach.</b> This axis says how far the system may act on its own. <em>Where</em> the
 * feedback may go — the mentor conversation only, or also on the work itself — is a separate, workspace-level
 * decision carried by {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach}, and it does not
 * belong on this ladder because it does not order: a mentor chat is not obviously quieter or louder than one
 * review comment. Autonomy does order, and it is the adoption ladder a team actually walks.
 *
 * <p>{@link #PROPOSE} is the tier that separates measurement from intervention: the review runs and every
 * observation is recorded, so the behaviour series stays unbroken, and nobody is told anything. Silencing a
 * practice by dropping it from new reviews instead puts a hole in that series. Semgrep (<em>Monitor /
 * Comment / Block</em>), Kyverno (<em>Audit / Enforce</em>) and ESLint (<em>off / warn / error</em>) all draw
 * the same line.
 *
 * <p>A tier is resolved, never read raw: a practice may hold no opinion and inherit its area's, and an area
 * may inherit the workspace's. See
 * {@link de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver}.
 */
public enum PracticeReviewTier {
    /** Not reviewed at all. No review is started, so there is no observation and no feedback. */
    OFF,

    /**
     * Reviewed and recorded, and nothing is sent — the only way to turn a practice down without turning its
     * measurement off. No feedback unit is delivered on any channel.
     *
     * <p>What is withheld here is not lost. Every finding a review produces at this tier is persisted as an
     * observation and gets a {@code SUPPRESSED} feedback row stamped
     * {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason#PRACTICE_TIER_QUIET}, so
     * the artifact's trace shows the practice as reviewed with measurements taken, nothing delivered, and
     * the tier named as the reason. "Held, waiting for a person to decide" is a recorded outcome; vanished
     * is not.
     *
     * <p>The name looks forward to an approval queue that does not exist yet. Until it does, a held unit is
     * released by raising the practice — nobody approves an individual finding, and no surface claims
     * otherwise. Delivery after approval will be a different act, recorded differently; see
     * {@link #deliversWithoutApproval()}.
     */
    PROPOSE,

    /** Reviewed, recorded, and delivered without asking, as far as the workspace's reach allows. */
    DELIVER;

    /** Width of every {@code review_tier} column; every constant name must fit. */
    public static final int MAX_LENGTH = 16;

    /**
     * The tier in force where nobody has expressed an opinion — the last fallback of the practice → area →
     * workspace chain. {@link #DELIVER}, because that is what every practice did before the chain existed,
     * and a migration must never make a system louder or quieter than the one it replaced.
     */
    public static final PracticeReviewTier DEFAULT = DELIVER;

    /** Whether a review may be started for this practice at all — the detection gate's admission test. */
    public boolean admitsReview() {
        return this != OFF;
    }

    /**
     * Whether feedback may be delivered without a human first approving it.
     *
     * <p>The autonomy half of the delivery gate; {@code FeedbackReach} answers the other half, which is
     * where. Both must say yes — see
     * {@link FeedbackAdmission#delivers(ObservationOrigin, PracticeReviewTier,
     * de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach,
     * de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel)}.
     *
     * <p>{@link #PROPOSE} answers {@code false} here and will keep doing so once the approval queue ships:
     * approved delivery is a different act, recorded differently, and not this one.
     */
    public boolean deliversWithoutApproval() {
        return this == DELIVER;
    }
}
