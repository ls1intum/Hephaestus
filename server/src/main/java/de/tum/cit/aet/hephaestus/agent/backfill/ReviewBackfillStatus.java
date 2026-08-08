package de.tum.cit.aet.hephaestus.agent.backfill;

/**
 * Where a review-backfill campaign is in its life.
 *
 * <p>The first state is deliberately not "running". A campaign can spend a workspace's whole monthly LLM
 * budget, so the estimate is produced first, shown, and then confirmed by name — a spend that size has
 * to be a decision somebody made, not a side effect of setting a date range.
 */
public enum ReviewBackfillStatus {
    /** Scope enumerated and costed. Nothing has been submitted and nothing will be until it is confirmed. */
    AWAITING_CONFIRMATION,

    /** Confirmed. The driver is working through the scope from the cursor. */
    RUNNING,

    /**
     * Stopped part-way and expected to continue by itself once the reason clears. Distinct from
     * {@link #CANCELLED} because the cursor is still meaningful and the rest of the scope is still owed.
     */
    PAUSED,

    /** The cursor reached the end of the scope. Every artifact in it was offered to the review path. */
    COMPLETED,

    /** Stopped on purpose. The remainder of the scope will not be reviewed by this run. */
    CANCELLED;

    /** Whether the driver should still be doing work for a run in this state. */
    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    /** Whether a run in this state can still be confirmed into {@link #RUNNING}. */
    public boolean isConfirmable() {
        return this == AWAITING_CONFIRMATION || this == PAUSED;
    }
}
