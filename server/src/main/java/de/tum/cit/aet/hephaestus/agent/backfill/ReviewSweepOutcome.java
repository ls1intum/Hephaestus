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

    /**
     * The workspace still has an unfinished campaign. One at a time, exactly as for a hand-scoped
     * backfill: two overlapping runs would each read the other's ledger rows as already covered.
     */
    SKIPPED_CAMPAIGN_UNDER_WAY,

    /** Nothing was created in the window. There is nothing to review and nothing to record. */
    SKIPPED_EMPTY_SCOPE,

    /**
     * More artifacts in a few days than a whole hand-confirmed campaign may cover. Refused rather than
     * paid for: a workspace that produced thousands of pull requests overnight has a broken mirror, and
     * a nightly sweep is the wrong place to discover that by spending a month's budget on it.
     */
    SKIPPED_SCOPE_TOO_LARGE,
}
