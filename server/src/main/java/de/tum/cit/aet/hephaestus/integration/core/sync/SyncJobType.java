package de.tum.cit.aet.hephaestus.integration.core.sync;

/**
 * What kind of pass a {@link SyncJob} represents.
 *
 * <p>The 60s-tick scheduler slices that most integrations already run do NOT create job rows
 * (they'd be noise) — only these three coarser, admin-visible passes are recorded.
 */
public enum SyncJobType {
    /**
     * First sync after a Connection goes ACTIVE (or a newly-monitored resource is added).
     *
     * <p>Runs the incremental fetch only. It deliberately does NOT sweep for deletions: a mirror
     * that is being populated for the first time has nothing stale in it, and every row it has not
     * fetched yet would look like an upstream deletion to a set difference.
     */
    INITIAL,
    /**
     * Periodic re-sync that repairs webhook drift.
     *
     * <p>Runs everything {@link #INITIAL} does, and then the part that repairs drift rather than
     * merely re-reading: a deletion sweep that set-differences the full upstream issue/pull-request
     * id set against the local mirror and tombstones what upstream no longer has
     * ({@code GitHubDeletionSweepService}). This is the only pass that removes anything, and so the
     * only thing separating it from {@link #INITIAL}.
     *
     * <p>The sweep exists because every other path is upsert-only, so an entity deleted upstream is
     * caught only by a webhook — and webhooks here are not redeliverable (ADR-0008), while GitHub
     * emits no {@code pull_request.deleted} event whatsoever. Without this pass a single missed
     * delivery leaves a phantom row that never expires and permanently inflates per-repository counts.
     *
     * <p>The sweep is fail-closed: it deletes nothing for a repository whose upstream listing it
     * cannot prove complete.
     */
    RECONCILIATION,
    /** Historical backfill of pre-existing data, bounded by a horizon/checkpoint. */
    BACKFILL,
}
