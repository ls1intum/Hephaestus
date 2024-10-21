package de.tum.in.www1.hephaestus.codereview.team;

import java.util.List;
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

    public Optional<Team> getTeam(Long id) {
        logger.info("Getting team with id: " + id);
        return teamRepository.findById(id);
    }

    public Optional<Team> getTeam(String name) {
        logger.info("Getting team with name: " + name);
        return teamRepository.findByName(name);
    }

    public List<TeamDTO> getAllTeams() {
        List<TeamDTO> teams = teamRepository.findAll().stream().map(TeamDTO::fromTeam).toList();
        logger.info("Getting all (" + teams.size() + ") teams");
        return teams;
    }

    public Team saveTeam(Team team) {
        logger.info("Saving team: " + team);
        return teamRepository.save(team);
    }

    public Boolean createDefaultTeams() {
        logger.info("Creating default teams");
        Team iris = teamRepository.save(new Team());
        iris.setName("Iris");
        Team athena = teamRepository.save(new Team());
        athena.setName("Athena");
        Team atlas = teamRepository.save(new Team());
        atlas.setName("Atlas");
        Team programming = teamRepository.save(new Team());
        programming.setName("Programming");
        Team hephaestus = teamRepository.save(new Team());
        hephaestus.setName("Hephaestus");
        Team communication = teamRepository.save(new Team());
        communication.setName("Communication");
        Team lectures = teamRepository.save(new Team());
        lectures.setName("Lectures");
        Team usability = teamRepository.save(new Team());
        usability.setName("Usability");
        Team ares = teamRepository.save(new Team());
        ares.setName("Ares");
        teamRepository.saveAll(
                List.of(iris, athena, atlas, programming, hephaestus, communication, lectures, usability, ares));
        return true;
    }
}
