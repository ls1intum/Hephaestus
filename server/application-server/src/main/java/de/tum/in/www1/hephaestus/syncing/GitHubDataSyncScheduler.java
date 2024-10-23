package de.tum.in.www1.hephaestus.syncing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GitHubDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);
    private final GitHubDataSyncService dataSyncService;

    @Value("${monitoring.runOnStartup:true}")
    private boolean runOnStartup;

    public GitHubDataSyncScheduler(GitHubDataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (runOnStartup) {
            logger.info("Starting initial GitHub data sync...");
            dataSyncService.syncData();
            logger.info("Initial GitHub data sync completed.");
        }
    }

    @Scheduled(cron = "${monitoring.repository-sync-cron}")
    public void syncDataCron() {
        logger.info("Starting scheduled GitHub data sync...");
        dataSyncService.syncData();
        logger.info("Scheduled GitHub data sync completed.");
    }
}