package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;

/** Read-only sync state for one integration kind. Implementations must not call vendor APIs. */
public interface ConnectionSyncStateProvider {
    IntegrationKind kind();

    /** Connection-level snapshot: webhook registration, next scheduled run, rate limit, backfill rollup. */
    ConnectionSyncDetails describe(IntegrationRef ref, long connectionId);

    /** Unified per-resource rows (repos / channels / collections) for this connection. */
    List<SyncResourceState> resources(IntegrationRef ref, long connectionId);
}
