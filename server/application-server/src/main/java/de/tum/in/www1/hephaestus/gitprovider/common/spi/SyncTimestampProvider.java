package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.TeamSyncState;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.UserSyncState;
import java.time.Instant;
import java.util.Optional;

/**
 * Provides sync timestamp operations for the gitprovider ETL engine.
 * <p>
 * This interface handles scope-level sync timestamps, including:
 * <ul>
 *   <li>Issue types, issue dependencies, and sub-issues sync timestamps</li>
 *   <li>User and team sync timestamps</li>
 * </ul>
 * <p>
 * Extracted from {@link SyncTargetProvider} to comply with the Interface Segregation Principle.
 * Services that only need timestamp operations should depend on this interface.
 *
 * @see SyncTargetProvider for repository-level sync operations
 * @see BackfillStateProvider for backfill tracking operations
 */
public interface SyncTimestampProvider {
    // ═══════════════════════════════════════════════════════════════════════════
    // SCOPE SYNC TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the sync metadata for a scope (issue types, dependencies, sub-issues).
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to provide actual sync metadata.
     *
     * @param scopeId the scope ID
     * @return the sync metadata, or empty if scope not found
     */
    default Optional<SyncMetadata> getSyncMetadata(Long scopeId) {
        return Optional.empty();
    }

    /**
     * Updates the sync timestamp for scope-level sync operations.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist sync timestamps.
     *
     * @param scopeId  the scope ID
     * @param syncType the type of sync (ISSUE_TYPES, ISSUE_DEPENDENCIES, SUB_ISSUES)
     * @param syncedAt the timestamp of the sync
     */
    default void updateScopeSyncTimestamp(Long scopeId, SyncType syncType, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist timestamps
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USER SYNC TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the user sync state for a scope.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to provide actual user sync state.
     *
     * @param scopeId the scope ID
     * @return the user sync state, or empty if scope not found
     */
    default Optional<UserSyncState> getUserSyncState(Long scopeId) {
        return Optional.empty();
    }

    /**
     * Updates the users sync timestamp for a scope.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist sync timestamps.
     *
     * @param scopeId  the scope ID
     * @param syncedAt the timestamp of the sync
     */
    default void updateUsersSyncTimestamp(Long scopeId, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist timestamps
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEAM SYNC TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the team sync state for a scope.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to provide actual team sync state.
     *
     * @param scopeId the scope ID
     * @return the team sync state, or empty if scope not found
     */
    default Optional<TeamSyncState> getTeamSyncState(Long scopeId) {
        return Optional.empty();
    }

    /**
     * Updates the teams sync timestamp for a scope.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist sync timestamps.
     *
     * @param scopeId  the scope ID
     * @param syncedAt the timestamp of the sync
     */
    default void updateTeamsSyncTimestamp(Long scopeId, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist timestamps
    }

}
