package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Delivery lifecycle of a {@link Feedback} unit. Append-only: transitions are recorded by inserting a new
 * superseding row, never by mutating an existing one.
 */
public enum FeedbackState {
    /** Synthesised and rendered, but not yet placed on its surface. */
    PREPARED,
    /** Successfully delivered to its surface. */
    DELIVERED,
    /** Replaced by a newer unit (see {@code supersedes_id}); kept for the temporal record. */
    SUPERSEDED,
    /** Deliberately withheld; see {@code suppression_reason}. */
    SUPPRESSED,
    /** Delivery was attempted but failed. */
    FAILED,
}
