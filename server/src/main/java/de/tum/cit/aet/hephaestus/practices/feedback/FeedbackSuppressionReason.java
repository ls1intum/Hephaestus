package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Why a {@link Feedback} unit was withheld instead of delivered. Set only when the unit's
 * {@link FeedbackState} is {@code SUPPRESSED}; null otherwise.
 */
public enum FeedbackSuppressionReason {
    /** Reviewer-side observation that the audience policy does not deliver to the author. */
    AUDIENCE_REVIEWER,
    /** The underlying finding's severity fell below the delivery threshold. */
    BELOW_THRESHOLD,
    /** The underlying finding's confidence was too low to deliver. */
    LOW_CONFIDENCE,
    /** Dropped by a policy floor / rate cap that bounds how much is surfaced at once. */
    POLICY_FLOOR_DROP,
}
