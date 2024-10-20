package de.tum.in.www1.hephaestus.codereview.team;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TeamService {
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Optional<Team> getTeam(String name) {
        logger.info("Getting team with name: " + name);
        return teamRepository.findByName(name);
    }
}
