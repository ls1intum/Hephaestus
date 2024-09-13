package de.tum.in.www1.hephaestus.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GitHubDataSyncScheduler implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDataSyncScheduler.class);
    private final GitHubDataSyncService dataSyncService;

    @Value("${monitoring.runOnStartup:true}")
    private boolean runOnStartup;

    public GitHubDataSyncScheduler(GitHubDataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
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