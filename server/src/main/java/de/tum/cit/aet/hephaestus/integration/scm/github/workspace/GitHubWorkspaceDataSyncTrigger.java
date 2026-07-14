package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobConflictException;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobRequest;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobTrigger;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.scm.github.sync.GithubDataSyncService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * GitHub-side adapter that lets workspace lifecycle code (activation, post-install
 * provisioning) drive a full GraphQL sync without importing
 * {@link GithubDataSyncService} directly.
 *
 * <p>{@link GithubDataSyncService} (and, for the same reason, {@link SyncTargetProvider}) is
 * consumed via {@link ObjectProvider} because there is a known circular reference between the sync
 * service and the workspace activation layer (the sync service injects {@link SyncTargetProvider}
 * which in turn lives in the workspace module). Lazy lookup breaks the cycle without forcing the
 * workspace caller to know about the cycle. {@link ConnectionRepository} and {@link SyncJobService}
 * have no such documented cycle, but are looked up the same defensive way for consistency with the
 * rest of this class — a missing bean degrades to the untracked/unrecorded sync path rather than a
 * startup failure.
 */
@Component
public class GitHubWorkspaceDataSyncTrigger implements WorkspaceDataSyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(GitHubWorkspaceDataSyncTrigger.class);

    private final ObjectProvider<GithubDataSyncService> dataSyncServiceProvider;
    private final ObjectProvider<SyncTargetProvider> syncTargetProvider;
    private final ObjectProvider<ConnectionRepository> connectionRepositoryProvider;
    private final ObjectProvider<SyncJobService> syncJobServiceProvider;

    public GitHubWorkspaceDataSyncTrigger(
        ObjectProvider<GithubDataSyncService> dataSyncServiceProvider,
        ObjectProvider<SyncTargetProvider> syncTargetProvider,
        ObjectProvider<ConnectionRepository> connectionRepositoryProvider,
        ObjectProvider<SyncJobService> syncJobServiceProvider
    ) {
        this.dataSyncServiceProvider = dataSyncServiceProvider;
        this.syncTargetProvider = syncTargetProvider;
        this.connectionRepositoryProvider = connectionRepositoryProvider;
        this.syncJobServiceProvider = syncJobServiceProvider;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    /**
     * Resolves the workspace's ACTIVE GitHub {@link Connection} and, when both it and {@link
     * SyncJobService} are available, records the lifecycle sync as an {@code INITIAL}/{@code
     * LIFECYCLE} job (design doc §3.4). Falls back to the untracked sync when either is missing
     * (e.g. no Connection yet at the point this fires, or the bean genuinely isn't wired), and skips
     * the run — same as a manual "Sync now" would — when a job is already active for this
     * connection.
     */
    @Override
    public void syncAllRepositories(long workspaceId) {
        ConnectionRepository connectionRepository = connectionRepositoryProvider.getIfAvailable();
        SyncJobService syncJobService = syncJobServiceProvider.getIfAvailable();
        Optional<Connection> connection =
            connectionRepository == null
                ? Optional.empty()
                : connectionRepository.findFirstByWorkspaceIdAndKindAndStateOrderByCreatedAtDesc(
                      workspaceId,
                      IntegrationKind.GITHUB,
                      IntegrationState.ACTIVE
                  );

        if (syncJobService == null || connection.isEmpty()) {
            dataSyncServiceProvider.getObject().syncAllRepositories(workspaceId);
            return;
        }

        try {
            syncJobService.run(
                new SyncJobRequest(
                    workspaceId,
                    connection.get().getId(),
                    IntegrationKind.GITHUB,
                    SyncJobType.INITIAL,
                    SyncJobTrigger.LIFECYCLE,
                    null
                ),
                handle -> dataSyncServiceProvider.getObject().syncAllRepositories(workspaceId, handle)
            );
        } catch (SyncJobConflictException e) {
            log.info(
                "Skipped lifecycle sync: reason=activeJobAlready, workspaceId={}, activeJobId={}",
                workspaceId,
                e.activeJob().getId()
            );
        }
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
