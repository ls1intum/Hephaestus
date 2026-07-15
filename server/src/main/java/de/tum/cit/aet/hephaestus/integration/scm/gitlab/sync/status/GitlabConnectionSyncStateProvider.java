package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace.GitLabWorkspaceInitializationService;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/** Read-only GitLab sync state built without vendor API calls. */
@Component
@ConditionalOnBean(GitLabWorkspaceInitializationService.class)
public class GitlabConnectionSyncStateProvider implements ConnectionSyncStateProvider {

    private static final Logger log = LoggerFactory.getLogger(GitlabConnectionSyncStateProvider.class);

    private final ConnectionService connectionService;
    private final ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final IssueRepository issueRepository;

    public GitlabConnectionSyncStateProvider(
        ConnectionService connectionService,
        ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider,
        SyncSchedulerProperties syncSchedulerProperties,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        IssueRepository issueRepository
    ) {
        this.connectionService = connectionService;
        this.rateLimitTrackerProvider = rateLimitTrackerProvider;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.issueRepository = issueRepository;
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

        return new ConnectionSyncDetails(webhookRegistered, nextScheduledSyncAt(), rateLimit, null, false, null);
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
        Map<Long, Long> itemCountsByRepositoryId = repositoryIds.isEmpty()
            ? Map.of()
            : issueRepository
                  .countGroupedByRepositoryIds(repositoryIds)
                  .stream()
                  .collect(
                      Collectors.toMap(
                          IssueRepository.RepositoryItemCount::getRepositoryId,
                          IssueRepository.RepositoryItemCount::getItemCount
                      )
                  );

        return monitors
            .stream()
            .map(monitor -> toResourceState(monitor, reposByNameWithOwner, itemCountsByRepositoryId))
            .toList();
    }

    private SyncResourceState toResourceState(
        RepositoryToMonitor monitor,
        Map<String, Repository> reposByNameWithOwner,
        Map<Long, Long> itemCountsByRepositoryId
    ) {
        Repository repo = reposByNameWithOwner.get(monitor.getNameWithOwner());
        Instant lastSyncedAt = repo == null ? null : repo.getLastSyncAt();
        Long itemCount = repo == null ? null : itemCountsByRepositoryId.getOrDefault(repo.getId(), 0L);
        String state = lastSyncedAt != null ? SyncResourceState.STATE_SYNCED : SyncResourceState.STATE_PENDING;

        return new SyncResourceState(
            monitor.getId(),
            monitor.getNameWithOwner(),
            monitor.getNameWithOwner(),
            SyncResourceState.Type.REPOSITORY,
            state,
            lastSyncedAt,
            itemCount,
            null,
            null,
            null,
            null
        );
    }

    @Nullable
    private Instant nextScheduledSyncAt() {
        try {
            CronExpression cron = CronExpression.parse(syncSchedulerProperties.cron());
            LocalDateTime next = cron.next(LocalDateTime.now());
            return next == null ? null : next.atZone(ZoneId.systemDefault()).toInstant();
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse GitLab sync cron for nextScheduledSyncAt: {}", e.getMessage());
            return null;
        }
    }
}
