package de.tum.in.www1.hephaestus.gitprovider.team;

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

    public List<TeamInfoDTO> getAllTeams() {
        List<TeamInfoDTO> teams = teamRepository.findAll().stream().map(TeamInfoDTO::fromTeam).toList();
        logger.info("Getting all (" + teams.size() + ") teams");
        return teams;
    }

    public Team saveTeam(Team team) {
        logger.info("Saving team: " + team);
        return teamRepository.saveAndFlush(team);
    }

    public Team createTeam(String name, String color) {
        logger.info("Creating team with name: " + name + " and color: " + color);
        Team team = new Team();
        team.setName(name);
        team.setColor(color);
        return teamRepository.save(team);
    }

    public void deleteTeam(Long id) {
        logger.info("Deleting team with id: " + id);
        teamRepository.deleteById(id);
    }

    public Boolean createDefaultTeams() {
        logger.info("Creating default teams");
        Team iris = teamRepository.save(new Team());
        iris.setName("Iris");
        iris.setColor("#69feff");
        Team athena = teamRepository.save(new Team());
        athena.setName("Athena");
        athena.setColor("#69feff");
        Team atlas = teamRepository.save(new Team());
        atlas.setName("Atlas");
        atlas.setColor("#69feff");
        Team programming = teamRepository.save(new Team());
        programming.setName("Programming");
        programming.setColor("#69feff");
        Team hephaestus = teamRepository.save(new Team());
        hephaestus.setName("Hephaestus");
        hephaestus.setColor("#69feff");
        Team communication = teamRepository.save(new Team());
        communication.setName("Communication");
        communication.setColor("#69feff");
        Team lectures = teamRepository.save(new Team());
        lectures.setName("Lectures");
        lectures.setColor("#69feff");
        Team usability = teamRepository.save(new Team());
        usability.setName("Usability");
        usability.setColor("#69feff");
        Team ares = teamRepository.save(new Team());
        ares.setName("Ares");
        ares.setColor("#69feff");
        teamRepository.saveAll(
                List.of(iris, athena, atlas, programming, hephaestus, communication, lectures, usability, ares));
        return true;
    }
}
