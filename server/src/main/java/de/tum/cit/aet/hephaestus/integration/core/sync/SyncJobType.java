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
     * Periodic re-sync that repairs webhook drift. Runs everything {@link #INITIAL} does and, for some
     * integrations, additionally infers upstream deletions from absence — webhooks here are not
     * redeliverable (ADR-0008), so a single missed delete otherwise leaves a phantom row forever.
     *
     * <p><strong>The work is per-integration; a recorded job of this type does NOT by itself imply
     * anything was or could have been removed.</strong> Whether a sweep runs at all, and how it
     * fail-closes, lives with each integration's deletion path ({@code GitHubDeletionSweepService},
     * {@code GitLabDeletionSweepService}, {@code OutlineDocumentSyncService#tombstoneVanished}); Slack
     * does not sweep by design.
     */
    RECONCILIATION,
    /** Historical backfill of pre-existing data, bounded by a horizon/checkpoint. */
    BACKFILL,
}
