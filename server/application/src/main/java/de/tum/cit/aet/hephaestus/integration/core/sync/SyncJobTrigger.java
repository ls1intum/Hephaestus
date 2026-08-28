package de.tum.cit.aet.hephaestus.integration.core.sync;

/** What initiated a {@link SyncJob} — drives the "triggered by" column in job history UI. */
public enum SyncJobTrigger {
    /** Periodic cron/scheduler tick (a scope-level fan-out, not the 60s micro-slice). */
    SCHEDULED,
    /** Workspace admin clicked "Sync now" / "Backfill" in the UI. */
    MANUAL,
    /** Connection lifecycle transition (e.g. activation) drove the sync via {@code WorkspaceDataSyncTrigger}. */
    LIFECYCLE,
    /** Internal system trigger not attributable to a user (e.g. startup catch-up). */
    SYSTEM,
}
