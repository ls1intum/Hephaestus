package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The delivery-lifecycle axis of a {@link Feedback} unit. Append-only: a transition is recorded by inserting a
 * new row, never by mutating an existing one (the entity is {@code @Immutable}). Constrained at the DB by
 * {@code chk_feedback_state}.
 */
public enum FeedbackDeliveryState {
    /** Synthesised and rendered, but not yet placed on its surface. */
    PREPARED,
    /** Successfully placed on its surface. */
    DELIVERED,
    /** Replaced by a newer row that points back via {@code replaces_id}; kept for the temporal record. */
    SUPERSEDED,
    /** Deliberately withheld; the reason is in {@code suppression_reason}. */
    SUPPRESSED,
    /** Delivery was attempted but failed. */
    FAILED,
}
