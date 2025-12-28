package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Provides sync targets (repositories) for the gitprovider ETL engine.
 */
public interface SyncTargetProvider {
    List<SyncTarget> getActiveSyncTargets();

    default List<WorkspaceSyncSession> getWorkspaceSyncSessions() {
        return List.of();
    }

    default SyncStatistics getSyncStatistics() {
        return new SyncStatistics(0, 0, 0, 0, false);
    }

    List<SyncTarget> getSyncTargetsForWorkspace(Long workspaceId);

    void updateSyncTimestamp(Long workspaceId, String repositoryNameWithOwner, SyncType syncType, Instant syncedAt);

    default Optional<WorkspaceSyncMetadata> getWorkspaceSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    default void updateWorkspaceSyncTimestamp(Long workspaceId, SyncType syncType, Instant syncedAt) {}

    default Optional<WorkspaceUserSyncMetadata> getWorkspaceUserSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    default void updateUsersSyncTimestamp(Long workspaceId, Instant syncedAt) {}

    default Optional<WorkspaceTeamSyncMetadata> getWorkspaceTeamSyncMetadata(Long workspaceId) {
        return Optional.empty();
    }

    default void updateTeamsSyncTimestamp(Long workspaceId, Instant syncedAt) {}

    default Optional<SyncTarget> findSyncTargetById(Long syncTargetId) {
        return Optional.empty();
    }

    default void updateBackfillState(Long syncTargetId, Integer highWaterMark, Integer checkpoint, Instant lastRunAt) {}

    default void removeSyncTarget(Long syncTargetId) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // Records
    // ═══════════════════════════════════════════════════════════════════════════

    record WorkspaceSyncSession(
        Long workspaceId,
        String workspaceSlug,
        String displayName,
        String accountLogin,
        Long installationId,
        List<SyncTarget> syncTargets,
        SyncContextProvider.SyncContext syncContext
    ) {}

    record SyncStatistics(
        int totalWorkspaces,
        int skippedByStatus,
        int skippedByFilter,
        int activeAndAllowed,
        boolean filterActive
    ) {}

    record WorkspaceSyncMetadata(
        Long workspaceId,
        String displayName,
        String organizationLogin,
        Long organizationId,
        Instant issueTypesSyncedAt,
        Instant issueDependenciesSyncedAt,
        Instant subIssuesSyncedAt
    ) {
        public boolean needsIssueTypesSync(int cooldownMinutes) {
            return (
                issueTypesSyncedAt == null ||
                issueTypesSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * 60L))
            );
        }

        public boolean needsIssueDependenciesSync(int cooldownMinutes) {
            return (
                issueDependenciesSyncedAt == null ||
                issueDependenciesSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * 60L))
            );
        }

        public boolean needsSubIssuesSync(int cooldownMinutes) {
            return (
                subIssuesSyncedAt == null ||
                subIssuesSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * 60L))
            );
        }
    }

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
        public boolean needsFullSync(Instant staleThreshold) {
            return lastFullSyncAt == null || lastFullSyncAt.isBefore(staleThreshold);
        }

        public boolean needsLabelSync(Instant staleThreshold) {
            return lastLabelsSyncedAt == null || lastLabelsSyncedAt.isBefore(staleThreshold);
        }

        public boolean isBackfillInitialized() {
            return backfillHighWaterMark != null;
        }

        public boolean isBackfillComplete() {
            return (
                isBackfillInitialized() &&
                (backfillHighWaterMark == 0 || (backfillCheckpoint != null && backfillCheckpoint <= 0))
            );
        }

        public int getBackfillRemaining() {
            if (!isBackfillInitialized() || backfillHighWaterMark == 0) return 0;
            if (backfillCheckpoint == null) return backfillHighWaterMark;
            return Math.max(0, backfillCheckpoint);
        }
    }

    record WorkspaceUserSyncMetadata(Long workspaceId, Instant usersSyncedAt) {
        public boolean needsSync(int cooldownMinutes) {
            return usersSyncedAt == null || usersSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * 60L));
        }
    }

    record WorkspaceTeamSyncMetadata(Long workspaceId, Instant teamsSyncedAt, List<String> organizationNames) {
        public boolean needsSync(int cooldownMinutes) {
            return teamsSyncedAt == null || teamsSyncedAt.isBefore(Instant.now().minusSeconds(cooldownMinutes * 60L));
        }
    }

    enum SyncType {
        LABELS,
        MILESTONES,
        ISSUES_AND_PULL_REQUESTS,
        COLLABORATORS,
        FULL_REPOSITORY,
        ISSUE_TYPES,
        ISSUE_DEPENDENCIES,
        SUB_ISSUES,
    }

    enum AuthMode {
        GITHUB_APP,
        PAT,
    }
}
