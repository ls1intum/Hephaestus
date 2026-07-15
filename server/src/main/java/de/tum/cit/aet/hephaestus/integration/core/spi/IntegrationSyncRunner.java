package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Per-kind sync execution body. Implementations should check
 * {@link SyncExecutionHandle#isCancellationRequested()} between work units and exit early when set.
 */
public interface IntegrationSyncRunner {
    IntegrationKind kind();

    /** Full reconciliation pass — the body for both {@code INITIAL} and {@code RECONCILIATION} jobs. */
    void reconcile(IntegrationRef ref, SyncExecutionHandle handle);

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
