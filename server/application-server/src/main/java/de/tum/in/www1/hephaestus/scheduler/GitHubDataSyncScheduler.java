package de.tum.in.www1.hephaestus.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.IOException;

@Component
public class GitHubDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);
    private final GitHubDataSyncService dataSyncService;

    @Value("${monitoring.runOnStartup:true}")
    private boolean runOnStartup;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    public GitHubDataSyncScheduler(GitHubDataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @PostConstruct
    public void onStartup() {
        if (runOnStartup) {
            logger.info("Starting initial GitHub data sync for Hephaestus...");
            syncAllRepositories();
            logger.info("Initial GitHub data sync completed successfully.");
        }
    }

    @Scheduled(cron = "${monitoring.repository-sync-cron}")
    public void syncDataCron() {
        logger.info("Starting daily GitHub data sync...");
        syncAllRepositories();
        logger.info("Daily GitHub data sync completed successfully.");
    }

    private void syncAllRepositories() {
        for (String repositoryName : repositoriesToMonitor) {
            try {
                dataSyncService.syncData(repositoryName);
                logger.info("GitHub data sync completed successfully for repository: " + repositoryName);
            } catch (IOException e) {
                logger.error("Error during GitHub data sync: ", e);
            }
        }
    }
}