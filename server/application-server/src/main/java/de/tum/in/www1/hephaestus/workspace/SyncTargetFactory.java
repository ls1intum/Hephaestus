package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.AuthMode;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;

/**
 * Factory class for creating SyncTarget instances from workspace and repository monitor entities.
 * Centralizes the conversion logic that was previously duplicated across multiple services.
 */
public final class SyncTargetFactory {

    private SyncTargetFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts a RepositoryToMonitor to a SyncTarget for the sync service.
     *
     * @param workspace the workspace containing the repository monitor
     * @param rtm the repository to monitor entity
     * @return a SyncTarget instance for use with sync services
     */
    public static SyncTarget create(Workspace workspace, RepositoryToMonitor rtm) {
        AuthMode authMode = workspace.getGitProviderMode() == Workspace.GitProviderMode.GITHUB_APP_INSTALLATION
            ? AuthMode.GITHUB_APP
            : AuthMode.PERSONAL_ACCESS_TOKEN;

        return new SyncTarget(
            rtm.getId(),
            workspace.getId(),
            workspace.getInstallationId(),
            workspace.getPersonalAccessToken(),
            authMode,
            rtm.getNameWithOwner(),
            rtm.getLabelsSyncedAt(),
            rtm.getMilestonesSyncedAt(),
            rtm.getIssuesAndPullRequestsSyncedAt(),
            rtm.getCollaboratorsSyncedAt(),
            rtm.getRepositorySyncedAt(),
            rtm.getBackfillHighWaterMark(),
            rtm.getBackfillCheckpoint(),
            rtm.getBackfillLastRunAt(),
            rtm.getIssueSyncCursor(),
            rtm.getPullRequestSyncCursor()
        );
    }
}
