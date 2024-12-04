package de.tum.in.www1.hephaestus.meta;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetaService {

    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);

    @Autowired
    private TeamService teamService;

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    public MetaDataDTO getMetaData() {
        logger.info("Getting meta data...");
        var teams = teamService.getAllTeams();
        return new MetaDataDTO(
            teams.stream().sorted((team1, team2) -> team1.name().compareTo(team2.name())).toList(),
            scheduledDay,
            scheduledTime
        );
    }
}
