package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The withholding-rationale axis of a {@link Feedback} unit: why it was withheld instead of delivered. Set iff the
 * unit's {@link FeedbackDeliveryState} is {@code SUPPRESSED}; NULL otherwise. Constrained at the DB by
 * {@code chk_feedback_suppression_reason}.
 */
public enum FeedbackSuppressionReason {
    /** Reviewer-side observation that the audience policy does not deliver to the author. */
    REVIEWER_SIDE,
    /** The underlying finding's severity fell below the delivery threshold. */
    BELOW_THRESHOLD,
    /** The underlying finding's confidence was too low to deliver. */
    LOW_CONFIDENCE,
    /** Dropped by a policy floor / rate cap that bounds how much is surfaced at once. */
    POLICY_FLOOR_DROP,
    /** The subject explicitly DISPUTED this locus on an earlier run; not re-surfaced unless the underlying evidence changes. */
    REACTED_DISPUTED,
    /** The subject marked this locus NOT_APPLICABLE on an earlier run; not re-surfaced unless the underlying evidence changes. */
    REACTED_NOT_APPLICABLE,
    /** A PREPARED conversational-feedback unit that was never raised in a mentor turn and aged out of the window. */
    CONVERSATION_EXPIRED,
}
