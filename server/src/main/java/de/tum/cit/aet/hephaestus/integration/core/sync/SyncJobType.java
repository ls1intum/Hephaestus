package de.tum.cit.aet.hephaestus.integration.core.sync;

/**
 * What kind of pass a {@link SyncJob} represents.
 *
 * <p>The 60s-tick scheduler slices that most integrations already run do NOT create job rows
 * (they'd be noise) — only these three coarser, admin-visible passes are recorded.
 */
public enum SyncJobType {
    /** First sync after a Connection goes ACTIVE (or a newly-monitored resource is added). */
    INITIAL,
    /** Periodic full re-sync that repairs webhook drift ("catches anything webhooks missed"). */
    RECONCILIATION,
    /** Historical backfill of pre-existing data, bounded by a horizon/checkpoint. */
    BACKFILL,
}
