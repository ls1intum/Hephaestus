package de.tum.in.www1.hephaestus.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    public MetaDataDTO getMetaData() {
        logger.info("Getting meta data...");
        return new MetaDataDTO(repositoriesToMonitor, scheduledDay, scheduledTime);
    }
}
