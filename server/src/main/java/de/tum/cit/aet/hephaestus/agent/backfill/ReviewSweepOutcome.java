package de.tum.cit.aet.hephaestus.agent.backfill;

/**
 * What one due schedule's turn produced.
 *
 * <p>Named rather than reduced to a boolean because the reasons are operationally different: an empty
 * scope is a quiet workspace, a campaign already under way is a sweep that is still catching up, and a
 * scope over the limit is a mirror that has gone wrong. Only {@link #OPENED} means work was bought.
 */
public enum ReviewSweepOutcome {
    /** A campaign was created, RUNNING, for this schedule's window. */
    OPENED,

    /** The schedule was turned off between being selected and being acted on. */
    SKIPPED_DISABLED,

    /** The workspace is suspended, or has practices switched off. */
    SKIPPED_WORKSPACE_UNAVAILABLE,

    /** One campaign at a time: two overlapping runs would each read the other's ledger rows as covered. */
    SKIPPED_CAMPAIGN_UNDER_WAY,

    SKIPPED_EMPTY_SCOPE,

    /**
     * More artifacts in a few days than a whole hand-confirmed campaign may cover — refused rather than
     * paid for, since that scale means a broken mirror, not a sweep to spend a month's budget on.
     */
    SKIPPED_SCOPE_TOO_LARGE,
}
