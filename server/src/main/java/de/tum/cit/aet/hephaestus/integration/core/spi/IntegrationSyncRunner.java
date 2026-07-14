package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;

/**
 * Per-kind sync execution body, invoked by {@link SyncJobService#run} inside the job-tracking
 * template (creation, one-active-job guard, lease heartbeat, outcome mapping). Implementations are
 * injected as a {@code List<>} and dispatched by {@link IntegrationKind}; a kind with no registered
 * runner cannot be manually triggered (the trigger endpoint answers 409).
 *
 * <p>{@code reconcile}/{@code backfill} run on the calling thread inside {@code SyncJobService.run} —
 * implementations must periodically check {@link SyncJobHandle#isCancellationRequested()} (e.g.
 * between repositories/pages, and inside any rate-limit wait in bounded slices) and exit early when
 * true. Cancellation is best-effort/cooperative, never a hard interrupt.
 */
public interface IntegrationSyncRunner {
    IntegrationKind kind();

    /** Full reconciliation pass — the body for both {@code INITIAL} and {@code RECONCILIATION} jobs. */
    void reconcile(IntegrationRef ref, SyncJobHandle handle);

    /** Whether this integration supports an explicit, separately-triggerable backfill pass. */
    default boolean supportsBackfill() {
        return false;
    }

    /**
     * Historical backfill body for {@code BACKFILL} jobs. Only called when {@link #supportsBackfill()}
     * returns {@code true}; the default throws so a misconfigured manifest fails loudly instead of
     * silently no-op'ing.
     */
    default void backfill(IntegrationRef ref, SyncJobHandle handle) {
        throw new UnsupportedOperationException("kind=" + kind() + " does not support backfill");
    }
}
