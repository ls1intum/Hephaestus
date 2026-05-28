package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.GithubDataSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * GitHub-side adapter that lets workspace lifecycle code (activation, post-install
 * provisioning) drive a full GraphQL sync without importing
 * {@link GithubDataSyncService} directly.
 *
 * <p>{@link GithubDataSyncService} is consumed via {@link ObjectProvider} because there is
 * a known circular reference between the sync service and the workspace activation layer
 * (the sync service injects {@link SyncTargetProvider} which in turn lives in the
 * workspace module). Lazy lookup breaks the cycle without forcing the workspace caller to
 * know about the cycle.
 */
@Component
public class GitHubWorkspaceDataSyncTrigger implements WorkspaceDataSyncTrigger {

    private final ObjectProvider<GithubDataSyncService> dataSyncServiceProvider;
    private final ObjectProvider<SyncTargetProvider> syncTargetProvider;

    public GitHubWorkspaceDataSyncTrigger(
        ObjectProvider<GithubDataSyncService> dataSyncServiceProvider,
        ObjectProvider<SyncTargetProvider> syncTargetProvider
    ) {
        this.dataSyncServiceProvider = dataSyncServiceProvider;
        this.syncTargetProvider = syncTargetProvider;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public void syncAllRepositories(long workspaceId) {
        dataSyncServiceProvider.getObject().syncAllRepositories(workspaceId);
    }

    @Override
    public void syncSingleSyncTarget(long syncTargetId) {
        var providerOpt = syncTargetProvider.getIfAvailable();
        if (providerOpt == null) {
            return;
        }
        providerOpt
            .findSyncTargetById(syncTargetId)
            .ifPresent(target -> dataSyncServiceProvider.getObject().syncSyncTargetAsync(target));
    }
}
