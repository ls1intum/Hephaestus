package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncStatistics;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncSession;
import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic GitHub data synchronization across all workspaces.
 *
 * <h2>Purpose</h2>
 * Runs scheduled sync jobs using Spring's {@code @Scheduled} annotation.
 * Iterates over all active workspaces and syncs their repositories via GraphQL.
 *
 * <h2>Architecture</h2>
 * Uses SPI interfaces to remain decoupled from workspace module:
 * <ul>
 *   <li>{@link SyncTargetProvider} - provides workspace/repository info to sync</li>
 *   <li>{@link SyncContextProvider} - manages context for logging and isolation</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe by design:
 * <ul>
 *   <li>Spring's default scheduling uses a single-threaded executor, so
 *       {@code syncDataCron()} will not run concurrently with itself</li>
 *   <li>Each workspace sync is isolated - no shared mutable state between workspaces</li>
 *   <li>Context is set/cleared per workspace via {@code SyncContextProvider}</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Spring creates this component at startup (after {@code NatsConsumerService}
 *       due to {@code @Order(2)})</li>
 *   <li>{@code @Scheduled} method runs at cron interval from {@code monitoring.sync-cron}</li>
 *   <li>Each run processes all ACTIVE workspaces, respecting monitoring filters</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code monitoring.sync-cron} - Cron expression for sync schedule (default: "0 0 3 * * *" = 3 AM daily)</li>
 *   <li>{@code monitoring.filters.allowed-organizations} - Limit to specific orgs (dev filter)</li>
 *   <li>{@code monitoring.filters.allowed-repositories} - Limit to specific repos (dev filter)</li>
 * </ul>
 *
 * @see GitHubDataSyncService
 * @see SyncTargetProvider
 */
@Order(value = 2)
@Component
@RequiredArgsConstructor
public class GitHubDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);

    private final SyncTargetProvider syncTargetProvider;
    private final SyncContextProvider syncContextProvider;
    private final GitHubDataSyncService dataSyncService;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;

    /**
     * Scheduled job to sync GitHub data for all active workspaces.
     * Respects monitoring filters to limit sync scope during development.
     */
    @Scheduled(cron = "${monitoring.sync-cron}")
    public void syncDataCron() {
        log.info("Starting scheduled GitHub data sync...");

        // Get statistics for logging
        SyncStatistics stats = syncTargetProvider.getSyncStatistics();

        // Get workspace sync sessions (already filtered by status and monitoring scope)
        List<WorkspaceSyncSession> sessions = syncTargetProvider.getWorkspaceSyncSessions();

        if (sessions.isEmpty()) {
            log.info(
                "No workspaces to sync. Total: {}, skipped by status: {}, skipped by filter: {}",
                stats.totalWorkspaces(),
                stats.skippedByStatus(),
                stats.skippedByFilter()
            );
            return;
        }

        if (stats.filterActive()) {
            log.info(
                "Monitoring filter active. Syncing {} of {} workspaces (skipped {} by status, {} by filter)",
                stats.activeAndAllowed(),
                stats.totalWorkspaces(),
                stats.skippedByStatus(),
                stats.skippedByFilter()
            );
        } else {
            log.info(
                "Found {} ACTIVE workspaces to sync (skipped {} non-active)",
                stats.activeAndAllowed(),
                stats.skippedByStatus()
            );
        }

        for (WorkspaceSyncSession session : sessions) {
            syncWorkspace(session);
        }

        log.info("Scheduled GitHub data sync completed for {} workspaces.", sessions.size());
    }

    private void syncWorkspace(WorkspaceSyncSession session) {
        try {
            // Set context for logging and isolation
            syncContextProvider.setContext(session.syncContext());

            log.info(
                "Syncing workspace {} (slug={}, login={})",
                session.workspaceId(),
                session.workspaceSlug(),
                session.accountLogin()
            );

            // Wrap sync operations with context propagation for async threads
            Runnable syncTask = syncContextProvider.wrapWithContext(() -> {
                // Sync repositories, organizations, and teams (via syncSyncTarget)
                for (SyncTarget target : session.syncTargets()) {
                    dataSyncService.syncSyncTarget(target);
                }

                // Sync sub-issues, issue types, and issue dependencies via GraphQL
                // These are workspace-level operations (fetched across all repositories)
                syncSubIssues(session);
                syncIssueTypes(session);
                syncIssueDependencies(session);
            });

            // Execute synchronously in the scheduler thread
            syncTask.run();
        } catch (Exception e) {
            log.error(
                "Error syncing workspace {} (slug={}): {}",
                session.workspaceId(),
                session.workspaceSlug(),
                e.getMessage(),
                e
            );
        } finally {
            syncContextProvider.clearContext();
        }
    }

    private void syncSubIssues(WorkspaceSyncSession session) {
        try {
            log.debug("Syncing sub-issue relationships for workspace {}", session.workspaceSlug());
            subIssueSyncService.syncSubIssuesForWorkspace(session.workspaceId());
        } catch (Exception e) {
            log.error("Failed to sync sub-issues for workspace {}: {}", session.workspaceSlug(), e.getMessage());
        }
    }

    private void syncIssueTypes(WorkspaceSyncSession session) {
        try {
            log.debug("Syncing issue types for workspace {}", session.workspaceSlug());
            issueTypeSyncService.syncIssueTypesForWorkspace(session.workspaceId());
        } catch (Exception e) {
            log.error("Failed to sync issue types for workspace {}: {}", session.workspaceSlug(), e.getMessage());
        }
    }

    private void syncIssueDependencies(WorkspaceSyncSession session) {
        // NOTE (Dec 2025): issue_dependencies webhook is STILL NOT AVAILABLE
        // (GitHub shipped UI without API/webhook - see Discussion #165749)
        // GraphQL bulk sync is currently the ONLY way to get dependency data
        try {
            log.debug("Syncing issue dependencies for workspace {}", session.workspaceSlug());
            issueDependencySyncService.syncDependenciesForWorkspace(session.workspaceId());
        } catch (Exception e) {
            log.error(
                "Failed to sync issue dependencies for workspace {}: {}",
                session.workspaceSlug(),
                e.getMessage()
            );
        }
    }
}
