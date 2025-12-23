package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.issuedependency.github.GitHubIssueDependencySyncService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringScopeFilter;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextExecutor;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic GitHub data synchronization across all workspaces.
 * Applies monitoring filters to restrict sync to allowed workspaces and
 * repositories.
 */
@Order(value = 2)
@Component
@RequiredArgsConstructor
public class GitHubDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);

    private final WorkspaceService workspaceService;
    private final GitHubDataSyncService dataSyncService;
    private final MonitoringScopeFilter monitoringScopeFilter;
    private final GitHubSubIssueSyncService subIssueSyncService;
    private final GitHubIssueTypeSyncService issueTypeSyncService;
    private final GitHubIssueDependencySyncService issueDependencySyncService;

    /**
     * Scheduled job to sync GitHub data for all active workspaces.
     * Respects monitoring filters to limit sync scope during development.
     */
    @Scheduled(cron = "${monitoring.sync-cron}")
    public void syncDataCron() {
        logger.info("Starting scheduled GitHub data sync...");

        List<Workspace> allWorkspaces = workspaceService.listAllWorkspaces();

        // Filter only ACTIVE workspaces that pass monitoring filter
        List<Workspace> workspacesToSync = allWorkspaces
            .stream()
            .filter(w -> w.getStatus() == WorkspaceStatus.ACTIVE)
            .filter(monitoringScopeFilter::isWorkspaceAllowed)
            .toList();

        int skippedByStatus = (int) allWorkspaces.stream().filter(w -> w.getStatus() != WorkspaceStatus.ACTIVE).count();
        int skippedByFilter = allWorkspaces.size() - skippedByStatus - workspacesToSync.size();

        if (workspacesToSync.isEmpty()) {
            logger.info(
                "No workspaces to sync. Total: {}, skipped by status: {}, skipped by filter: {}",
                allWorkspaces.size(),
                skippedByStatus,
                skippedByFilter
            );
            return;
        }

        if (monitoringScopeFilter.isActive()) {
            logger.info(
                "Monitoring filter active. Syncing {} of {} workspaces (skipped {} by status, {} by filter)",
                workspacesToSync.size(),
                allWorkspaces.size(),
                skippedByStatus,
                skippedByFilter
            );
        } else {
            logger.info(
                "Found {} ACTIVE workspaces to sync (skipped {} non-active)",
                workspacesToSync.size(),
                skippedByStatus
            );
        }

        for (Workspace workspace : workspacesToSync) {
            syncWorkspace(workspace);
        }

        logger.info("Scheduled GitHub data sync completed for {} workspaces.", workspacesToSync.size());
    }

    private void syncWorkspace(Workspace workspace) {
        WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());

        try {
            WorkspaceContextHolder.setContext(workspaceContext);

            logger.info(
                "Syncing workspace {} (slug={}, login={})",
                workspace.getId(),
                workspace.getWorkspaceSlug(),
                workspace.getAccountLogin()
            );

            // Filter repositories that pass the monitoring filter
            List<RepositoryToMonitor> repositoriesToSync = workspace
                .getRepositoriesToMonitor()
                .stream()
                .filter(monitoringScopeFilter::isRepositoryAllowed)
                .toList();

            if (monitoringScopeFilter.isActive()) {
                int skipped = workspace.getRepositoriesToMonitor().size() - repositoriesToSync.size();
                if (skipped > 0) {
                    logger.debug(
                        "Workspace {} has {} repositories, syncing {} (skipped {} by filter)",
                        workspace.getWorkspaceSlug(),
                        workspace.getRepositoriesToMonitor().size(),
                        repositoriesToSync.size(),
                        skipped
                    );
                }
            }

            // Wrap sync operations with context executor to propagate context to async
            // threads
            Runnable syncTask = WorkspaceContextExecutor.wrap(() -> {
                repositoriesToSync.forEach(dataSyncService::syncRepository);
                // TODO: User and Team sync via GraphQL not yet implemented
                // dataSyncService.syncUsers(workspace);
                // dataSyncService.syncTeams(workspace);

                // Sync sub-issues, issue types, and issue dependencies via GraphQL
                // These are workspace-level operations (fetched across all repositories)
                try {
                    logger.debug("Syncing sub-issue relationships for workspace {}", workspace.getWorkspaceSlug());
                    subIssueSyncService.syncSubIssuesForWorkspace(workspace.getId());
                } catch (Exception e) {
                    logger.error(
                        "Failed to sync sub-issues for workspace {}: {}",
                        workspace.getWorkspaceSlug(),
                        e.getMessage()
                    );
                }

                try {
                    logger.debug("Syncing issue types for workspace {}", workspace.getWorkspaceSlug());
                    issueTypeSyncService.syncIssueTypesForWorkspace(workspace.getId());
                } catch (Exception e) {
                    logger.error(
                        "Failed to sync issue types for workspace {}: {}",
                        workspace.getWorkspaceSlug(),
                        e.getMessage()
                    );
                }

                // NOTE (Dec 2025): issue_dependencies webhook is STILL NOT AVAILABLE
                // (GitHub shipped UI without API/webhook - see Discussion #165749)
                // GraphQL bulk sync is currently the ONLY way to get dependency data
                try {
                    logger.debug("Syncing issue dependencies for workspace {}", workspace.getWorkspaceSlug());
                    issueDependencySyncService.syncDependenciesForWorkspace(workspace.getId());
                } catch (Exception e) {
                    logger.error(
                        "Failed to sync issue dependencies for workspace {}: {}",
                        workspace.getWorkspaceSlug(),
                        e.getMessage()
                    );
                }
            });

            // Execute synchronously in the scheduler thread
            syncTask.run();
        } catch (Exception e) {
            logger.error(
                "Error syncing workspace {} (slug={}): {}",
                workspace.getId(),
                workspace.getWorkspaceSlug(),
                e.getMessage(),
                e
            );
        } finally {
            WorkspaceContextHolder.clearContext();
        }
    }
}
