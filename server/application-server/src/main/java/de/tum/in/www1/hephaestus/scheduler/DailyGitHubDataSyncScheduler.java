package de.tum.in.www1.hephaestus.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.IOException;

@Component
public class DailyGitHubDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyGitHubDataSyncScheduler.class);
    private final GitHubDataSyncService dataSyncService;

    @Value("${monitoring.runOnStartup:true}")
    private boolean runOnStartup;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    public DailyGitHubDataSyncScheduler(GitHubDataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @PostConstruct
    public void onStartup() {
        if (runOnStartup) {
            logger.info("Starting initial GitHub data sync for Hephaestus...");
            try {
                dataSyncService.syncData("ls1intum/hephaestus");
                logger.info("Initial GitHub data sync completed successfully for repository: ls1intum/hephaestus");
            } catch (IOException e) {
                logger.error("Error during GitHub data sync: ", e);
            }
        }
    }

    @Scheduled(cron = "${monitoring.repository-sync-cron}")
    public void syncDataDaily() {
        logger.info("Starting daily GitHub data sync...");
        for (String repositoryName : repositoriesToMonitor) {
            try {
                dataSyncService.syncData(repositoryName);
                logger.info("Daily GitHub data sync completed successfully for repository: " + repositoryName);
            } catch (IOException e) {
                logger.error("Error during GitHub data sync: ", e);
            }
        }
        logger.info("Daily GitHub data sync completed.");
    }
}