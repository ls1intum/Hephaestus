package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The withholding-rationale axis of a {@link Feedback} unit: why it was withheld instead of delivered. Set iff the
 * unit's {@link FeedbackDeliveryState} is {@code SUPPRESSED}; NULL otherwise. Constrained at the DB by
 * {@code chk_feedback_suppression_reason}.
 */
public enum FeedbackSuppressionReason {
    /** Dropped by the per-run volume cap on the non-blocking improvement tail ({@code DeliveryComposer}). */
    VOLUME_CAPPED,
    /** Collapsed as a near-duplicate of another observation delivered in the same run ({@code DeliveryComposer}). */
    COMPOSER_DEDUPED,
    /** The subject explicitly DISPUTED this locus on an earlier run; not re-surfaced unless the evidence changes. */
    REACTED_DISPUTED,
    /** Same re-surfacing rule as {@link #REACTED_DISPUTED}, for a locus the subject marked NOT_APPLICABLE. */
    REACTED_NOT_APPLICABLE,
    /** A PREPARED conversational-feedback unit that was never raised in a mentor turn and aged out of the window. */
    CONVERSATION_EXPIRED,
    /** The target artifact could not be resolved at delivery time. */
    ARTIFACT_GONE,
    ARTIFACT_CLOSED,
    /** The target PR was already merged and merged-delivery is disabled for the workspace. */
    ARTIFACT_MERGED,
    /**
     * Nothing produces this: the practice's binding decides whether a draft occasions a review, so a
     * review the gate let run is one whose result the author is meant to see. Kept because rows written
     * under it must still read back.
     */
    @Deprecated
    ARTIFACT_DRAFT,
    RECIPIENT_OPTED_OUT,
    /** The composed body sanitised to blank and no inline note was placed. */
    EMPTY_AFTER_SANITIZE,
    INSTANCE_SILENCED,
    WORKSPACE_DISABLED,
    WORKSPACE_DELIVERY_PAUSED,
    STALE_ROLLOUT_REVISION,
    OUTSIDE_CURRENT_COVERAGE,
    ADMINISTRATIVE_INTERNAL_ONLY,
    APPROVAL_STALE,
    APPROVAL_NO_LONGER_ELIGIBLE,
    PRACTICE_REQUIRES_APPROVAL,
    /**
     * Like {@link #PRACTICE_REQUIRES_APPROVAL}, but for a backfill campaign: measured retrospectively, so it is
     * not said out loud where it would read as today's work.
     *
     * <p>The in-app lane holds a backfill back too, under its own name
     * ({@code InAppRoutingDecision.BACKFILL_HELD}) and for its own reason: a process-level message is
     * a trend claim, and {@link de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin} calls a
     * backfilled population "sound as a snapshot, unusable as a trend against LIVE rows".
     */
    BACKFILL_QUIET,
}
