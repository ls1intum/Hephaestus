package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextExecutor;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Order(value = 2)
@Component
public class GitHubDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private GitHubDataSyncService dataSyncService;

    @Scheduled(cron = "${monitoring.sync-cron}")
    public void syncDataCron() {
        logger.info("Starting scheduled GitHub data sync...");

        var allWorkspaces = workspaceService.listAllWorkspaces();

        // Filter only ACTIVE workspaces - skip SUSPENDED and PURGED
        var activeWorkspaces = allWorkspaces.stream().filter(w -> w.getStatus() == WorkspaceStatus.ACTIVE).toList();

        if (activeWorkspaces.isEmpty()) {
            logger.info("No ACTIVE workspaces found for scheduled sync. Total workspaces: {}", allWorkspaces.size());
            return;
        }

        logger.info(
            "Found {} ACTIVE workspaces to sync (skipped {} non-active)",
            activeWorkspaces.size(),
            allWorkspaces.size() - activeWorkspaces.size()
        );

        for (Workspace workspace : activeWorkspaces) {
            // Create workspace context for this sync operation
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());

            try {
                WorkspaceContextHolder.setContext(workspaceContext);

                logger.info(
                    "Syncing workspace {} (slug={}, login={})",
                    workspace.getId(),
                    workspace.getWorkspaceSlug(),
                    workspace.getAccountLogin()
                );

                // Wrap sync operations with context executor to propagate context to async threads
                Runnable syncTask = WorkspaceContextExecutor.wrap(() -> {
                    workspace.getRepositoriesToMonitor().forEach(dataSyncService::syncRepositoryToMonitor);
                    dataSyncService.syncUsers(workspace);
                    dataSyncService.syncTeams(workspace);
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
                // Clean up context after each workspace
                WorkspaceContextHolder.clearContext();
            }
        }

        logger.info("Scheduled GitHub data sync completed for {} ACTIVE workspaces.", activeWorkspaces.size());
    }
}
