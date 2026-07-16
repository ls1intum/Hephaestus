package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.framework.CronSchedules;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWorkspaceInitializationService;
import de.tum.cit.aet.hephaestus.integration.scm.sync.status.ScmResourceCountReader;
import de.tum.cit.aet.hephaestus.integration.scm.sync.status.ScmResourceCountReader.ScmResourceCounts;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Read-only GitLab sync state built without vendor API calls. */
@Component
@ConditionalOnBean(GitLabWorkspaceInitializationService.class)
public class GitlabConnectionSyncStateProvider implements ConnectionSyncStateProvider {

    private final ConnectionService connectionService;
    private final ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final ScmResourceCountReader countReader;

    public GitlabConnectionSyncStateProvider(
        ConnectionService connectionService,
        ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider,
        SyncSchedulerProperties syncSchedulerProperties,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        ScmResourceCountReader countReader
    ) {
        this.connectionService = connectionService;
        this.rateLimitTrackerProvider = rateLimitTrackerProvider;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.countReader = countReader;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public ConnectionSyncDetails describe(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        Optional<ConnectionConfig.GitLabConfig> config = connectionService.findActiveGitLabConfig(workspaceId);
        Boolean webhookRegistered = config.map(c -> c.gitlabWebhookId() != null).orElse(null);

        GitLabRateLimitTracker tracker = rateLimitTrackerProvider.getIfAvailable();
        RateLimitSnapshot rateLimit = tracker == null ? null : tracker.snapshot(workspaceId);

        return new ConnectionSyncDetails(
            webhookRegistered,
            CronSchedules.nextRun(syncSchedulerProperties.cron()),
            CronSchedules.interval(syncSchedulerProperties.cron()),
            rateLimit,
            null,
            false
        );
    }

    @Override
    public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
        long workspaceId = ref.workspaceId();
        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspaceId);
        if (monitors.isEmpty()) {
            return List.of();
        }

        Map<String, Repository> reposByNameWithOwner = repositoryRepository
            .findAllByWorkspaceMonitors(workspaceId)
            .stream()
            .collect(Collectors.toMap(Repository::getNameWithOwner, r -> r, (a, b) -> a));

        List<Long> repositoryIds = reposByNameWithOwner.values().stream().map(Repository::getId).toList();
        Map<Long, ScmResourceCounts> countsByRepositoryId = countReader.countsByRepositoryId(repositoryIds);

        return monitors
            .stream()
            .map(monitor -> toResourceState(monitor, reposByNameWithOwner, countsByRepositoryId))
            .toList();
    }

    private SyncResourceState toResourceState(
        RepositoryToMonitor monitor,
        Map<String, Repository> reposByNameWithOwner,
        Map<Long, ScmResourceCounts> countsByRepositoryId
    ) {
        Repository repo = reposByNameWithOwner.get(monitor.getNameWithOwner());
        Instant lastSyncedAt = repo == null ? null : repo.getLastSyncAt();
        ScmResourceCounts counts =
            repo == null ? null : countsByRepositoryId.getOrDefault(repo.getId(), ScmResourceCounts.empty());
        Long itemCount = counts == null ? null : counts.headlineItemCount();
        String state = lastSyncedAt != null ? SyncResourceState.STATE_SYNCED : SyncResourceState.STATE_PENDING;

        return new SyncResourceState(
            monitor.getId(),
            monitor.getNameWithOwner(),
            monitor.getNameWithOwner(),
            SyncResourceState.Type.REPOSITORY,
            state,
            lastSyncedAt,
            itemCount,
            // Counts are read from the same shared tables GitHub mirrors into, so the breakdown is
            // identical. The per-class watermarks are NOT: the GitLab sync path writes only
            // repository.last_sync_at, never repository_to_monitor's per-class *_synced_at columns. Both
            // classes therefore report a null lastSyncedAt — "not tracked" — instead of borrowing the
            // repository-wide timestamp, which would assert a per-class freshness nobody measured.
            counts == null ? List.of() : counts.toSyncResourceCounts(null, null),
            null,
            null,
            null,
            null
        );
    }
}
