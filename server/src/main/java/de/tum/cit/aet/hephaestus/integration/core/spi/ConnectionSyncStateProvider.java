package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;

/**
 * Per-kind read-only sync-observability provider. Implementations are injected as a {@code List<>}
 * and dispatched by {@link IntegrationKind} (one impl per kind, duplicate registration is a startup
 * failure — same pattern as {@code ConnectionStrategy}). Missing provider for a kind is NOT an error:
 * callers fall back to {@link ConnectionSyncDetails#empty()} / an empty resource list.
 *
 * <p><strong>Both methods MUST be O(DB + in-memory) — never a live vendor API call.</strong> The
 * overview page renders every connected integration on one load; if {@code describe}/{@code resources}
 * made vendor calls, that page would fan out N vendor probes per render. Read from already-persisted
 * watermarks/state and already-in-memory trackers (e.g. the GitHub/GitLab rate-limit trackers) only.
 */
public interface ConnectionSyncStateProvider {
    IntegrationKind kind();

    /** Connection-level snapshot: webhook registration, next scheduled run, rate limit, backfill rollup. */
    ConnectionSyncDetails describe(IntegrationRef ref, long connectionId);

    /** Unified per-resource rows (repos / channels / collections) for this connection. */
    List<SyncResourceState> resources(IntegrationRef ref, long connectionId);
}
