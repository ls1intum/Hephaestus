package de.tum.in.www1.hephaestus.syncing;

import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
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
        var workspace = workspaceService.getWorkspace();
        workspace.getRepositoriesToMonitor().forEach(dataSyncService::syncRepositoryToMonitor);
        dataSyncService.syncUsers(workspace);
        logger.info("Scheduled GitHub data sync completed.");
    }
}
