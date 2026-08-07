package de.tum.cit.aet.hephaestus.agent.backfill;

/**
 * Why a campaign stopped short of its scope, and therefore what has to change for it to continue.
 *
 * <p>Every value here is recoverable and none of them skips an artifact. The distinction matters more
 * than it looks: a run that skipped what it could not afford would leave a baseline where "not reviewed"
 * and "reviewed, nothing found" are indistinguishable, and no downstream reader could tell which it was
 * looking at. A pause is legible; a hole is not.
 */
public enum ReviewBackfillPauseReason {
    /** The purse funding this workspace's practice-review binding has reached its monthly cap. */
    BUDGET_EXHAUSTED,

    /** The workspace has no enabled practice-review binding, so there is nothing to submit to. */
    BINDING_DISABLED,

    /** The workspace is no longer ACTIVE, or has turned practice review off. */
    WORKSPACE_UNAVAILABLE,
}
