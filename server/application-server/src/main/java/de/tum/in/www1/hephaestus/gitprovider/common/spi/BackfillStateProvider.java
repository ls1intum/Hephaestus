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
     * Updates the issue backfill state for a sync target.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist backfill state, otherwise sync progress
     * will be lost between restarts.
     *
     * @param syncTargetId   the sync target ID
     * @param highWaterMark  the high water mark (highest issue number at backfill start), or null to keep current
     * @param checkpoint     the current checkpoint (lowest issue number synced), or null to keep current
     * @param lastRunAt      when the backfill was last run, or null to keep current
     */
    default void updateIssueBackfillState(
        Long syncTargetId,
        Integer highWaterMark,
        Integer checkpoint,
        Instant lastRunAt
    ) {
        // Default no-op - implementations MUST override to persist state
    }

    /**
     * Updates the pull request backfill state for a sync target.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist backfill state, otherwise sync progress
     * will be lost between restarts.
     *
     * @param syncTargetId   the sync target ID
     * @param highWaterMark  the high water mark (highest pull request number at backfill start), or null to keep current
     * @param checkpoint     the current checkpoint (lowest pull request number synced), or null to keep current
     * @param lastRunAt      when the backfill was last run, or null to keep current
     */
    default void updatePullRequestBackfillState(
        Long syncTargetId,
        Integer highWaterMark,
        Integer checkpoint,
        Instant lastRunAt
    ) {
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

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGINATION CURSOR PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates the pagination cursor for issue sync.
     * <p>
     * This allows sync to resume from where it left off if interrupted mid-pagination.
     * The cursor should be persisted within the same transaction as the synced data
     * to ensure consistency.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist cursor state for reliable resumption.
     *
     * @param syncTargetId the sync target ID
     * @param cursor       the GraphQL pagination cursor, or null to clear (sync complete)
     */
    default void updateIssueSyncCursor(Long syncTargetId, String cursor) {
        // Default no-op - implementations MUST override to persist cursor
    }

    /**
     * Updates the pagination cursor for pull request sync.
     * <p>
     * This allows sync to resume from where it left off if interrupted mid-pagination.
     * The cursor should be persisted within the same transaction as the synced data
     * to ensure consistency.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist cursor state for reliable resumption.
     *
     * @param syncTargetId the sync target ID
     * @param cursor       the GraphQL pagination cursor, or null to clear (sync complete)
     */
    default void updatePullRequestSyncCursor(Long syncTargetId, String cursor) {
        // Default no-op - implementations MUST override to persist cursor
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROJECT ITEM SYNC CURSOR PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════
    // Projects are organization-level entities, so cursors are keyed by projectId
    // rather than syncTargetId (which is repository-scoped).
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates the pagination cursor for project item sync.
     * <p>
     * This allows sync to resume from where it left off if interrupted mid-pagination.
     * Unlike issue/PR cursors which are keyed by syncTargetId (repository),
     * project item cursors are keyed by projectId since projects are organization-level.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist cursor state for reliable resumption.
     *
     * @param projectId the project ID (not syncTargetId - projects are independent entities)
     * @param cursor    the GraphQL pagination cursor, or null to clear (sync complete)
     */
    default void updateProjectItemSyncCursor(Long projectId, String cursor) {
        // Default no-op - implementations MUST override to persist cursor
    }

    /**
     * Updates the project item sync completion state.
     * <p>
     * Called when project item sync completes successfully to clear the cursor
     * and record the completion timestamp for incremental sync support.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist completion state.
     *
     * @param projectId the project ID
     * @param syncedAt  the timestamp of successful sync completion
     */
    default void updateProjectItemsSyncedAt(Long projectId, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist state
    }

    /**
     * Gets the current item sync cursor for a project.
     * <p>
     * Used to resume sync from where it left off after interruption.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted cursor state.
     *
     * @param projectId the project ID
     * @return the cursor if present, or empty if no checkpoint exists
     */
    default Optional<String> getProjectItemSyncCursor(Long projectId) {
        return Optional.empty();
    }

    /**
     * Gets the timestamp of the last successful item sync for a project.
     * <p>
     * Used for incremental sync - only items updated after this timestamp
     * need to be fetched on subsequent syncs.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted state.
     *
     * @param projectId the project ID
     * @return the timestamp if present, or empty if never synced
     */
    default Optional<Instant> getProjectItemsSyncedAt(Long projectId) {
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROJECT FIELD SYNC CURSOR PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates the pagination cursor for project field sync.
     * <p>
     * This allows field sync to resume from where it left off if interrupted mid-pagination.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist cursor state for reliable resumption.
     *
     * @param projectId the project ID
     * @param cursor    the GraphQL pagination cursor, or null to clear (sync complete)
     */
    default void updateProjectFieldSyncCursor(Long projectId, String cursor) {
        // Default no-op - implementations MUST override to persist cursor
    }

    /**
     * Updates the project field sync completion state.
     * <p>
     * Called when project field sync completes successfully to clear the cursor
     * and record the completion timestamp.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist completion state.
     *
     * @param projectId the project ID
     * @param syncedAt  the timestamp of successful sync completion
     */
    default void updateProjectFieldsSyncedAt(Long projectId, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist state
    }

    /**
     * Gets the current field sync cursor for a project.
     * <p>
     * Used to resume field sync from where it left off after interruption.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted cursor state.
     *
     * @param projectId the project ID
     * @return the cursor if present, or empty if no checkpoint exists
     */
    default Optional<String> getProjectFieldSyncCursor(Long projectId) {
        return Optional.empty();
    }

    /**
     * Gets the timestamp of the last successful field sync for a project.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted state.
     *
     * @param projectId the project ID
     * @return the timestamp if present, or empty if never synced
     */
    default Optional<Instant> getProjectFieldsSyncedAt(Long projectId) {
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROJECT STATUS UPDATE SYNC CURSOR PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Updates the pagination cursor for project status update sync.
     * <p>
     * This allows status update sync to resume from where it left off if interrupted mid-pagination.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist cursor state for reliable resumption.
     *
     * @param projectId the project ID
     * @param cursor    the GraphQL pagination cursor, or null to clear (sync complete)
     */
    default void updateProjectStatusUpdateSyncCursor(Long projectId, String cursor) {
        // Default no-op - implementations MUST override to persist cursor
    }

    /**
     * Updates the project status update sync completion state.
     * <p>
     * Called when project status update sync completes successfully to clear the cursor
     * and record the completion timestamp.
     * <p>
     * <b>Important:</b> The default implementation is a no-op. Implementations
     * MUST override this method to persist completion state.
     *
     * @param projectId the project ID
     * @param syncedAt  the timestamp of successful sync completion
     */
    default void updateProjectStatusUpdatesSyncedAt(Long projectId, Instant syncedAt) {
        // Default no-op - implementations MUST override to persist state
    }

    /**
     * Gets the current status update sync cursor for a project.
     * <p>
     * Used to resume status update sync from where it left off after interruption.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted cursor state.
     *
     * @param projectId the project ID
     * @return the cursor if present, or empty if no checkpoint exists
     */
    default Optional<String> getProjectStatusUpdateSyncCursor(Long projectId) {
        return Optional.empty();
    }

    /**
     * Gets the timestamp of the last successful status update sync for a project.
     * <p>
     * <b>Important:</b> The default implementation returns empty. Implementations
     * MUST override this method to retrieve persisted state.
     *
     * @param projectId the project ID
     * @return the timestamp if present, or empty if never synced
     */
    default Optional<Instant> getProjectStatusUpdatesSyncedAt(Long projectId) {
        return Optional.empty();
    }
}
