package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
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
     *
     * @param syncTargetId the sync target ID
     * @return the sync target, or empty if not found
     */
    Optional<SyncTarget> findSyncTargetById(Long syncTargetId);

    /**
     * Updates the issue backfill state for a sync target.
     *
     * @param syncTargetId   the sync target ID
     * @param highWaterMark  the high water mark (highest issue number at backfill start), or null to keep current
     * @param checkpoint     the current checkpoint (lowest issue number synced), or null to keep current
     * @param lastRunAt      when the backfill was last run, or null to keep current
     */
    void updateIssueBackfillState(Long syncTargetId, Integer highWaterMark, Integer checkpoint, Instant lastRunAt);

    /**
     * Updates the pull request backfill state for a sync target.
     *
     * @param syncTargetId   the sync target ID
     * @param highWaterMark  the high water mark (highest pull request number at backfill start), or null to keep current
     * @param checkpoint     the current checkpoint (lowest pull request number synced), or null to keep current
     * @param lastRunAt      when the backfill was last run, or null to keep current
     */
    void updatePullRequestBackfillState(
        Long syncTargetId,
        Integer highWaterMark,
        Integer checkpoint,
        Instant lastRunAt
    );

    /**
     * Removes a sync target from the system.
     *
     * @param syncTargetId the sync target ID to remove
     */
    void removeSyncTarget(Long syncTargetId);

    /**
     * Reconciles a sync target's stable identity against what the provider currently reports for the
     * repository, healing a rename/transfer:
     * <ul>
     *   <li>If the target has no {@code nativeId} yet (legacy/PAT row), captures {@code resolvedNativeId}.</li>
     *   <li>If {@code resolvedNameWithOwner} differs from the stored name — and the stable id agrees —
     *       re-keys the monitor's {@code nameWithOwner} and refreshes the workspace's NATS consumer so
     *       the subject filter tracks the new name. Never renames by name alone.</li>
     * </ul>
     * Idempotent and safe to call every sync cycle; a no-op when nothing changed. Implementations must
     * not throw for a missing sync target (it may have been removed mid-sync).
     *
     * @param syncTargetId          the sync target (monitor) id
     * @param resolvedNativeId      the provider's stable numeric repository id, or {@code null} if unknown
     * @param resolvedNameWithOwner the repository's current {@code owner/name} as the provider reports it
     */
    default void reconcileSyncTargetIdentity(
        Long syncTargetId,
        @org.jspecify.annotations.Nullable Long resolvedNativeId,
        @org.jspecify.annotations.Nullable String resolvedNameWithOwner
    ) {
        // Default no-op: providers/tests that don't track a stable monitor identity keep prior behavior.
    }

    /**
     * Updates the pagination cursor for the given {@link SyncCursorKind}, keyed by
     * {@code syncTargetId}. Collapses the former per-entity {@code update<X>SyncCursor}
     * methods; the implementer switches on the kind to reach the correct column.
     * <p>
     * Lets sync resume mid-pagination after interruption; persist within the same
     * transaction as the synced data for consistency.
     *
     * @param scopeId the {@code syncTargetId} (repository) the cursor belongs to
     * @param kind    the entity kind whose cursor is being persisted
     * @param cursor  the GraphQL pagination cursor, or null to clear (sync complete)
     */
    void updateSyncCursor(Long scopeId, SyncCursorKind kind, String cursor);
}
