package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Two axes in one column. PENDING, CLAIMED, SENT, UNCERTAIN and FAILED are transport: where the provider
 * write got to. HELD and SUPPRESSED are policy: a check refused this row, and {@code suppression_reason}
 * says which. HELD is the refusal an operator can lift, so the row is re-evaluated later; SUPPRESSED is
 * the refusal nothing will revisit.
 */
public enum FeedbackDispatchState {
    PENDING,
    CLAIMED,
    SENT,
    HELD,
    SUPPRESSED,
    UNCERTAIN,
    FAILED,
}
