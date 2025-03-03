package de.tum.in.www1.hephaestus.meta;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Autowired
    private GitHub github;

    public MetaDataDTO getMetaData() {
        logger.info("Getting meta data...");
        var teams = teamService.getAllTeams();
        return new MetaDataDTO(
            teams.stream().sorted((team1, team2) -> team1.name().compareTo(team2.name())).toList(),
            scheduledDay,
            scheduledTime
        );
    }

    @Cacheable(value = "contributors", key = "'all'")
    public List<ContributorDTO> getContributors() {
        try {
            var contributors = github.getRepository("ls1intum/Hephaestus").listContributors().toList();
            List<ContributorDTO> contributorDTOs = new ArrayList<>();
            contributors
                .stream()
                .forEach(contributor -> {
                    try {
                        contributorDTOs.add(ContributorDTO.fromContributor(contributor));
                    } catch (IOException e) {
                        logger.error("Error converting contributor to DTO: {}", e.getMessage());
                    }
                });
            return contributorDTOs;
        } catch (IOException e) {
            logger.error("Error getting contributors: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @CacheEvict(value = "contributors", allEntries = true)
    @Scheduled(fixedRateString = "${hephaestus.cache.contributors.evict-rate:3600000}")
    public void evictContributorsCache() {}
}
