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

/**
 * GitLab's {@link ConnectionSyncStateProvider}. Both methods are O(DB + in-memory) only — no
 * vendor API calls — per the SPI contract (the overview page renders every connected integration
 * on one load).
 *
 * <p><b>{@code describe()}:</b>
 * <ul>
 *   <li>{@code webhookRegistered} — "stored group webhook id present" (existence-only, mirrors
 *       {@code GitLabWebhookService}'s own health-check semantics: it re-registers when the id
 *       goes missing, so "present" is the closest cheap proxy for "should be receiving events").
 *       {@code null} when there is no active GitLab config for this workspace at all.</li>
 *   <li>{@code rateLimit} — from {@link GitLabRateLimitTracker#snapshot}; {@code null} until the
 *       first real API call since the last restart.</li>
 *   <li>{@code nextScheduledSyncAt} — derived from {@code hephaestus.sync.cron} via
 *       {@link CronExpression}, the same property {@code GitlabDataSyncScheduler} runs on.</li>
 *   <li>{@code backfill} — {@code null} in v1. GitLab has a commit backfill
 *       ({@code GitLabHistoricalBackfillService}), but it runs on its own always-on schedule and
 *       isn't rolled into a connection-level "how far back have we gone" summary yet; wiring it is
 *       out of scope for this pass.</li>
 *   <li>{@code vendorHealthDegraded} — {@code false} in v1: GitLab has no independent
 *       vendor-suspension signal analogous to GitHub's {@code InstallationSuspensionTracker}.</li>
 * </ul>
 *
 * <p><b>{@code resources()}:</b> one row per {@link RepositoryToMonitor} (GitLab sync is
 * workspace-scoped, so every monitor in the workspace belongs to this connection — there is at
 * most one ACTIVE GitLab connection per workspace). {@code state} is derived from whether the
 * repository has completed at least one full sync ({@code Repository.lastSyncAt} set); there is no
 * richer per-repo status enum today. {@code lastSyncedAt} is {@code Repository.lastSyncAt} — the
 * freshest "full repo sync done" timestamp already stamped by
 * {@code GitLabWorkspaceInitializationService}/{@code GitlabDataSyncScheduler}. {@code itemCount}
 * is the combined issue+MR count from a single grouped query
 * ({@link IssueRepository#countGroupedByRepositoryIds}) — cheap even for a connection with many
 * repositories. {@code lastError} is always {@code null}: no per-repo error tracking exists yet.
 */
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
        String state = lastSyncedAt != null ? "SYNCED" : "PENDING";

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
