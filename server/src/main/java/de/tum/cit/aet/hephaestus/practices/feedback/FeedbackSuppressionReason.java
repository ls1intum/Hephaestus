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
    /**
     * Nothing produces this: the practice's binding decides whether a draft occasions a review, so a
     * review the gate let run is one whose result the author is meant to see. Kept because rows written
     * under it must still read back.
     */
    @Deprecated
    ARTIFACT_DRAFT,
    /** The recipient disabled practice-feedback delivery. */
    RECIPIENT_OPTED_OUT,
    /** The composed body sanitised to blank and no inline note was placed. */
    EMPTY_AFTER_SANITIZE,
    /** The instance-wide silent-mode brake was engaged, so nothing was posted anywhere. */
    INSTANCE_SILENCED,
    /**
     * The practice's autonomy tier does not admit this channel, so the finding was measured and recorded
     * but never said out loud. Written rather than dropped so "we saw it and chose to stay quiet" reads
     * differently from "we missed it" in every downstream evaluation.
     */
    PRACTICE_TIER_QUIET,
    /**
     * The observation came from a backfill campaign, whose only entitled channel has no producer.
     *
     * <p>Chosen over {@link #PRACTICE_TIER_QUIET} because the two are undone by different acts: a tier is
     * a dial an admin can turn, this lifts only when a profile-surface producer exists.
     */
    BACKFILL_QUIET,
}
