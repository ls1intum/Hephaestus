package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import java.time.Instant;
import java.util.Optional;

/**
 * Provides backfill state management for repository synchronization.
 * <p>
 * Backfill is the process of incrementally syncing historical data for a repository.
 * This interface tracks:
 * <ul>
 *   <li>High water mark: The highest issue/PR number when backfill started</li>
 *   <li>Checkpoint: The current position in the backfill process</li>
 *   <li>Last run time: When the backfill was last executed</li>
 * </ul>
 * <p>
 * Extracted from {@link SyncTargetProvider} to comply with the Interface Segregation Principle.
 * Services that only manage backfill state should depend on this interface.
 *
 * @see SyncTargetProvider for full sync target operations
 */
public interface BackfillStateProvider {
    /**
     * Finds a sync target by its ID.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to provide actual sync target lookup.
     *
     * @param syncTargetId the sync target ID
     * @return the sync target, or empty if not found
     */
    default Optional<SyncTarget> findSyncTargetById(Long syncTargetId) {
        return Optional.empty();
    }

    /**
     * Updates the backfill state for a sync target.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist backfill state, otherwise sync progress
     * will be lost between restarts.
     *
     * @param syncTargetId   the sync target ID
     * @param highWaterMark  the high water mark (highest issue/PR number at backfill start), or null to keep current
     * @param checkpoint     the current checkpoint (issue/PR number being processed), or null to keep current
     * @param lastRunAt      when the backfill was last run, or null to keep current
     */
    default void updateBackfillState(Long syncTargetId, Integer highWaterMark, Integer checkpoint, Instant lastRunAt) {
        // Default no-op - implementations MUST override to persist state
    }

    /**
     * Removes a sync target from the system.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to actually remove sync targets.
     *
     * @param syncTargetId the sync target ID to remove
     */
    default void removeSyncTarget(Long syncTargetId) {
        // Default no-op - implementations MUST override to remove targets
    }
}
