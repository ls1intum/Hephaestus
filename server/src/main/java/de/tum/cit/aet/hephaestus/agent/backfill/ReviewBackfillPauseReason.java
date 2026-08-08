package de.tum.cit.aet.hephaestus.agent.backfill;

/**
 * Why a campaign stopped short of its scope, and therefore what has to change for it to continue.
 *
 * <p>A value belongs here only if it is recoverable and pausing on it skips no artifact — see
 * {@code ReviewBackfillRun.cursorArtifactId} for why a campaign must never leave a hole.
 */
public enum ReviewBackfillPauseReason {
    /** The purse funding this workspace's practice-review binding has reached its monthly cap. */
    BUDGET_EXHAUSTED,

    /** The workspace has no enabled practice-review binding, so there is nothing to submit to. */
    BINDING_DISABLED,

    /** The workspace is no longer ACTIVE, or has turned practice review off. */
    WORKSPACE_UNAVAILABLE,
}
