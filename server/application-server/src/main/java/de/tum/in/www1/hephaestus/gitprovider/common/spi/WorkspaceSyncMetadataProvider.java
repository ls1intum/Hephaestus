package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncType;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceTeamSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceUserSyncMetadata;
import java.time.Instant;
import java.util.Optional;

/**
 * Provides workspace-level sync metadata for the gitprovider ETL engine.
 * <p>
 * This interface handles workspace-wide synchronization state, including:
 * <ul>
 *   <li>Issue types, issue dependencies, and sub-issues sync timestamps</li>
 *   <li>User and team sync metadata</li>
 * </ul>
 * <p>
 * Extracted from {@link SyncTargetProvider} to comply with the Interface Segregation Principle.
 * Services that only need workspace-level metadata should depend on this interface.
 *
 * @see SyncTargetProvider for repository-level sync operations
 * @see BackfillStateProvider for backfill tracking operations
 */
public interface WorkspaceSyncMetadataProvider {
    // ═══════════════════════════════════════════════════════════════════════════
    // WORKSPACE SYNC METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the sync metadata for a workspace (issue types, dependencies, sub-issues).
     *
     * @param workspaceId the workspace ID
     * @return the workspace sync metadata, or empty if workspace not found
     */
    default Optional<WorkspaceSyncMetadata> getWorkspaceSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    /**
     * Updates the sync timestamp for workspace-level sync operations.
     *
     * @param workspaceId the workspace ID
     * @param syncType    the type of sync (ISSUE_TYPES, ISSUE_DEPENDENCIES, SUB_ISSUES)
     * @param syncedAt    the timestamp of the sync
     */
    default void updateWorkspaceSyncTimestamp(Long workspaceId, SyncType syncType, Instant syncedAt) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // USER SYNC METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the user sync metadata for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return the user sync metadata, or empty if workspace not found
     */
    default Optional<WorkspaceUserSyncMetadata> getWorkspaceUserSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    /**
     * Updates the users sync timestamp for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param syncedAt    the timestamp of the sync
     */
    default void updateUsersSyncTimestamp(Long workspaceId, Instant syncedAt) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // TEAM SYNC METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the team sync metadata for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return the team sync metadata, or empty if workspace not found
     */
    default Optional<WorkspaceTeamSyncMetadata> getWorkspaceTeamSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    /**
     * Updates the teams sync timestamp for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param syncedAt    the timestamp of the sync
     */
    default void updateTeamsSyncTimestamp(Long workspaceId, Instant syncedAt) {}
}
