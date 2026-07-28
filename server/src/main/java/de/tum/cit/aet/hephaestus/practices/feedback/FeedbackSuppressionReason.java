package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The withholding-rationale axis of a {@link Feedback} unit: why it was withheld instead of delivered. Set iff the
 * unit's {@link FeedbackDeliveryState} is {@code SUPPRESSED}; NULL otherwise. Constrained at the DB by
 * {@code chk_feedback_suppression_reason}.
 */
public enum FeedbackSuppressionReason {
    /** Dropped by the per-run volume cap on the non-blocking improvement tail ({@code DeliveryComposer}). */
    VOLUME_CAPPED,
    /** Collapsed as a near-duplicate of another finding delivered in the same run ({@code DeliveryComposer}). */
    COMPOSER_DEDUPED,
    /** The subject explicitly DISPUTED this locus on an earlier run; not re-surfaced unless the underlying evidence changes. */
    REACTED_DISPUTED,
    /** The subject marked this locus NOT_APPLICABLE on an earlier run; not re-surfaced unless the underlying evidence changes. */
    REACTED_NOT_APPLICABLE,
    /** A PREPARED conversational-feedback unit that was never raised in a mentor turn and aged out of the window. */
    CONVERSATION_EXPIRED,
    /** The target artifact could not be resolved at delivery time (e.g. the PR row is gone). */
    ARTIFACT_GONE,
    /** The target artifact was closed, so nothing was posted. */
    ARTIFACT_CLOSED,
    /** The target PR was already merged and merged-delivery is disabled for the workspace. */
    ARTIFACT_MERGED,
    /** The target PR is a draft and draft-delivery is disabled for the workspace. */
    ARTIFACT_DRAFT,
    /** The recipient opted out of AI review feedback. */
    RECIPIENT_OPTED_OUT,
    /** The composed body sanitised to blank and no inline note was placed. */
    EMPTY_AFTER_SANITIZE,
}
