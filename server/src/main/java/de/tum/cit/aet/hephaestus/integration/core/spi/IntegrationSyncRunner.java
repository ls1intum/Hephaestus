package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;

/**
 * Per-kind sync execution body. Implementations should check
 * {@link SyncExecutionHandle#isCancellationRequested()} between work units and exit early when set.
 */
public interface IntegrationSyncRunner {
    IntegrationKind kind();

    /**
     * The body for both {@code INITIAL} and {@code RECONCILIATION} jobs.
     *
     * <p>{@code type} is passed rather than inferred because the two passes are not the same work:
     * {@code RECONCILIATION} additionally repairs drift that upserts cannot see (upstream deletions),
     * which would be actively wrong on {@code INITIAL} — a mirror mid-population has nothing stale,
     * and everything not yet fetched would read as deleted. Integrations with nothing type-specific
     * to do ignore the parameter, and the shared body stays shared.
     *
     * @param type the job type being executed; never {@code BACKFILL} (that dispatches to
     *             {@link #backfill})
     */
    void reconcile(IntegrationRef ref, SyncExecutionHandle handle, SyncJobType type);

    /** Whether this integration supports an explicit, separately-triggerable backfill pass. */
    default boolean supportsBackfill() {
        return false;
    }

    /**
     * Historical backfill body for {@code BACKFILL} jobs. Only called when {@link #supportsBackfill()}
     * returns {@code true}; the default throws so a misconfigured manifest fails loudly instead of
     * silently no-op'ing.
     */
    default void backfill(IntegrationRef ref, SyncExecutionHandle handle) {
        throw new UnsupportedOperationException("kind=" + kind() + " does not support backfill");
    }
}
