package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.time.Instant;
import java.util.List;

/**
 * Provides sync targets (repositories) for the gitprovider ETL engine.
 * <p>
 * This is the main SPI (Service Provider Interface) for the sync engine to discover
 * and manage synchronization targets. It extends focused sub-interfaces to comply
 * with the Interface Segregation Principle (ISP):
 * <ul>
 *   <li>{@link WorkspaceSyncMetadataProvider} – Workspace-level sync state</li>
 *   <li>{@link BackfillStateProvider} – Backfill tracking for incremental sync</li>
 * </ul>
 * <p>
 * <b>Dependency Guidance:</b>
 * <ul>
 *   <li>For repository-level sync: depend on {@code SyncTargetProvider}</li>
 *   <li>For workspace metadata only: depend on {@link WorkspaceSyncMetadataProvider}</li>
 *   <li>For backfill management only: depend on {@link BackfillStateProvider}</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> Implementations must be thread-safe. The sync engine may call
 * methods concurrently from multiple sync threads.
 * <p>
 * <b>Null Handling:</b> All {@code workspaceId} parameters must be non-null.
 * Repository identifiers ({@code repositoryNameWithOwner}) follow the format "owner/repo".
 *
 * @see WorkspaceSyncMetadataProvider
 * @see BackfillStateProvider
 */
public interface SyncTargetProvider extends WorkspaceSyncMetadataProvider, BackfillStateProvider {
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE SYNC TARGET OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets all active sync targets (repositories) across all workspaces.
     * <p>
     * Active targets are those in workspaces with {@code ACTIVE} status that have
     * at least one configured repository.
     *
     * @return list of active sync targets, never null (may be empty)
     */
    List<SyncTarget> getActiveSyncTargets();

    /**
     * Gets sync targets for a specific workspace.
     *
     * @param workspaceId the workspace ID (must not be null)
     * @return list of sync targets for the workspace, never null (may be empty if workspace not found)
     */
    List<SyncTarget> getSyncTargetsForWorkspace(Long workspaceId);

    /**
     * Gets repository nameWithOwner values for a workspace's active sync targets.
     *
     * <p>This provides a lightweight alternative to fetching full Repository entities
     * when only the repository identifier is needed (e.g., for GraphQL queries).
     *
     * @param workspaceId the workspace ID (must not be null)
     * @return list of repository nameWithOwner strings, never null (may be empty)
     */
    default List<String> getRepositoryNamesForWorkspace(Long workspaceId) {
        return getSyncTargetsForWorkspace(workspaceId).stream().map(SyncTarget::repositoryNameWithOwner).toList();
    }

    /**
     * Updates the sync timestamp for a repository-level sync operation.
     *
     * @param workspaceId             the workspace ID (must not be null)
     * @param repositoryNameWithOwner the repository identifier in "owner/repo" format (must not be null)
     * @param syncType                the type of sync (must not be null)
     * @param syncedAt                the timestamp of the sync (must not be null)
     * @throws IllegalArgumentException if any parameter is null or repositoryNameWithOwner format is invalid
     */
    void updateSyncTimestamp(Long workspaceId, String repositoryNameWithOwner, SyncType syncType, Instant syncedAt);

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKSPACE SYNC SESSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets workspace sync sessions for batch synchronization.
     * Each session contains all sync targets for a workspace with its sync context.
     *
     * @return list of workspace sync sessions
     */
    default List<WorkspaceSyncSession> getWorkspaceSyncSessions() {
        return List.of();
    }

    /**
     * Gets statistics about sync target filtering.
     *
     * @return sync statistics
     */
    default SyncStatistics getSyncStatistics() {
        return new SyncStatistics(0, 0, 0, 0, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECORDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A batch of sync targets for a single workspace, used for parallel processing.
     *
     * @param workspaceId    unique workspace identifier
     * @param workspaceSlug  URL-safe workspace identifier
     * @param displayName    human-readable workspace name
     * @param accountLogin   GitHub organization/user login
     * @param installationId GitHub App installation ID (null for PAT auth)
     * @param syncTargets    repositories to sync in this workspace
     * @param syncContext    thread-local context for logging and workspace isolation
     */
    record WorkspaceSyncSession(
        Long workspaceId,
        String workspaceSlug,
        String displayName,
        String accountLogin,
        Long installationId,
        List<SyncTarget> syncTargets,
        SyncContextProvider.SyncContext syncContext
    ) {}

    /**
     * Statistics about sync target filtering for observability.
     *
     * @param totalWorkspaces  total number of workspaces in the system
     * @param skippedByStatus  workspaces skipped due to non-ACTIVE status
     * @param skippedByFilter  workspaces skipped due to allowlist filtering
     * @param activeAndAllowed workspaces that passed all filters
     * @param filterActive     whether allowlist filtering is enabled
     */
    record SyncStatistics(
        int totalWorkspaces,
        int skippedByStatus,
        int skippedByFilter,
        int activeAndAllowed,
        boolean filterActive
    ) {}

    /**
     * Workspace-level sync metadata for organization-wide features.
     *
     * @param workspaceId              unique workspace identifier
     * @param displayName              human-readable workspace name
     * @param organizationLogin        GitHub organization login
     * @param organizationId           GitHub organization database ID
     * @param issueTypesSyncedAt       last sync of issue types (org-level feature)
     * @param issueDependenciesSyncedAt last sync of issue blocking relationships
     * @param subIssuesSyncedAt        last sync of sub-issues hierarchy
     */
    record WorkspaceSyncMetadata(
        Long workspaceId,
        String displayName,
        String organizationLogin,
        Long organizationId,
        Instant issueTypesSyncedAt,
        Instant issueDependenciesSyncedAt,
        Instant subIssuesSyncedAt
    ) {
        private static final long SECONDS_PER_MINUTE = 60L;

        public boolean needsIssueTypesSync(int cooldownMinutes) {
            return needsSync(issueTypesSyncedAt, cooldownMinutes);
        }

        public boolean needsIssueDependenciesSync(int cooldownMinutes) {
            return needsSync(issueDependenciesSyncedAt, cooldownMinutes);
        }

        public boolean needsSubIssuesSync(int cooldownMinutes) {
            return needsSync(subIssuesSyncedAt, cooldownMinutes);
        }

        private static boolean needsSync(Instant lastSyncedAt, int cooldownMinutes) {
            return (
                lastSyncedAt == null ||
                lastSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * SECONDS_PER_MINUTE))
            );
        }
    }

    /**
     * A repository configured for synchronization within a workspace.
     *
     * @param id                              unique sync target identifier
     * @param workspaceId                     parent workspace identifier
     * @param installationId                  GitHub App installation ID (null for PAT auth)
     * @param personalAccessToken             PAT for authentication (null for App auth)
     * @param authMode                        authentication mechanism to use
     * @param repositoryNameWithOwner         repository identifier in "owner/repo" format
     * @param lastLabelsSyncedAt              last labels sync timestamp
     * @param lastMilestonesSyncedAt          last milestones sync timestamp
     * @param lastIssuesAndPullRequestsSyncedAt last issues/PRs sync timestamp
     * @param lastCollaboratorsSyncedAt       last collaborators sync timestamp
     * @param lastFullSyncAt                  last full repository sync timestamp
     * @param backfillHighWaterMark           highest issue/PR number at backfill start
     * @param backfillCheckpoint              current backfill position (counts down to 0)
     * @param backfillLastRunAt               when backfill last executed
     */
    record SyncTarget(
        Long id,
        Long workspaceId,
        Long installationId,
        String personalAccessToken,
        AuthMode authMode,
        String repositoryNameWithOwner,
        Instant lastLabelsSyncedAt,
        Instant lastMilestonesSyncedAt,
        Instant lastIssuesAndPullRequestsSyncedAt,
        Instant lastCollaboratorsSyncedAt,
        Instant lastFullSyncAt,
        Integer backfillHighWaterMark,
        Integer backfillCheckpoint,
        Instant backfillLastRunAt
    ) {
        /**
         * Checks if a full sync is needed based on staleness threshold.
         *
         * @param staleThreshold instant before which data is considered stale
         * @return true if full sync has never run or is stale
         */
        public boolean needsFullSync(Instant staleThreshold) {
            return lastFullSyncAt == null || lastFullSyncAt.isBefore(staleThreshold);
        }

        /**
         * Checks if labels need to be synced based on staleness threshold.
         *
         * @param staleThreshold instant before which data is considered stale
         * @return true if labels have never been synced or are stale
         */
        public boolean needsLabelSync(Instant staleThreshold) {
            return lastLabelsSyncedAt == null || lastLabelsSyncedAt.isBefore(staleThreshold);
        }

        /** @return true if backfill has been initialized (high water mark captured) */
        public boolean isBackfillInitialized() {
            return backfillHighWaterMark != null;
        }

        /** @return true if backfill has completed (checkpoint reached 0 or no items to backfill) */
        public boolean isBackfillComplete() {
            return (
                isBackfillInitialized() &&
                (backfillHighWaterMark == 0 || (backfillCheckpoint != null && backfillCheckpoint <= 0))
            );
        }

        /** @return number of items remaining to backfill (0 if complete or not initialized) */
        public int getBackfillRemaining() {
            if (!isBackfillInitialized() || backfillHighWaterMark == 0) return 0;
            if (backfillCheckpoint == null) return backfillHighWaterMark;
            return Math.max(0, backfillCheckpoint);
        }
    }

    /**
     * User sync metadata for a workspace.
     *
     * @param workspaceId   unique workspace identifier
     * @param usersSyncedAt last users sync timestamp
     */
    record WorkspaceUserSyncMetadata(Long workspaceId, Instant usersSyncedAt) {
        private static final long SECONDS_PER_MINUTE = 60L;

        public boolean needsSync(int cooldownMinutes) {
            return (
                usersSyncedAt == null ||
                usersSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * SECONDS_PER_MINUTE))
            );
        }
    }

    /**
     * Team sync metadata for a workspace.
     *
     * @param workspaceId       unique workspace identifier
     * @param teamsSyncedAt     last teams sync timestamp
     * @param organizationNames GitHub organizations to sync teams from
     */
    record WorkspaceTeamSyncMetadata(Long workspaceId, Instant teamsSyncedAt, List<String> organizationNames) {
        private static final long SECONDS_PER_MINUTE = 60L;

        public boolean needsSync(int cooldownMinutes) {
            return (
                teamsSyncedAt == null ||
                teamsSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * SECONDS_PER_MINUTE))
            );
        }
    }

    /**
     * Types of synchronization operations tracked by the system.
     */
    enum SyncType {
        /** Repository labels sync */
        LABELS,
        /** Repository milestones sync */
        MILESTONES,
        /** Issues and pull requests incremental sync */
        ISSUES_AND_PULL_REQUESTS,
        /** Repository collaborators sync */
        COLLABORATORS,
        /** Full repository metadata sync */
        FULL_REPOSITORY,
        /** Organization-level issue types sync */
        ISSUE_TYPES,
        /** Issue blocking/blocked-by relationships sync */
        ISSUE_DEPENDENCIES,
        /** Issue parent/child hierarchy sync */
        SUB_ISSUES,
    }
}
