package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TeamService {
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private RepositoryRepository repositoryRepository;
    @Autowired
    private LabelRepository labelRepository;

    public Optional<Team> getTeam(Long id) {
        logger.info("Getting team with id: " + id);
        return teamRepository.findById(id);
    }

    @Transactional
    public List<TeamInfoDTO> getAllTeams() {
        List<TeamInfoDTO> teams = teamRepository.findAll().stream().map(TeamInfoDTO::fromTeam).toList();
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
        return teamRepository.saveAndFlush(team);
    }

    public void deleteTeam(Long id) {
        logger.info("Deleting team with id: " + id);
        teamRepository.deleteById(id);
        teamRepository.flush();
    }

    @Transactional
    public Boolean createDefaultTeams() {
        logger.info("Creating default teams");
        List<DefaultTeamRecord> defaultTeams = new ArrayList<>();
        defaultTeams.add(new DefaultTeamRecord("Iris", "#69feff", List.of("ls1intum/Artemis", "ls1intum/Pyris"), List.of("iris", "component:iris")));
        defaultTeams.add(new DefaultTeamRecord("Athena", "#69feff", List.of("ls1intum/Artemis", "ls1intum/Athena"), List.of("athena")));
        defaultTeams.add(new DefaultTeamRecord("Atlas", "#69feff", List.of("ls1intum/Artemis"), List.of("atlas")));
        defaultTeams.add(new DefaultTeamRecord("Programming", "#69feff", List.of("ls1intum/Artemis", "ls1intum/Pyris", "ls1intum/Atlas"), List.of("programming", "component:programming")));
        defaultTeams.add(new DefaultTeamRecord("Hephaestus", "#69feff", List.of("ls1intum/Hephaestus"), List.of()));
        defaultTeams.add(new DefaultTeamRecord("Communication", "#69feff", List.of("ls1intum/Artemis"), List.of("communication", "component:communication")));
        defaultTeams.add(new DefaultTeamRecord("Lectures", "#69feff", List.of("ls1intum/Artemis"), List.of("lecture", "Component:Lecture")));
        defaultTeams.add(new DefaultTeamRecord("Usability", "#69feff", List.of("ls1intum/Artemis"), List.of()));
        defaultTeams.add(new DefaultTeamRecord("Ares", "#69feff", List.of("ls1intum/Ares", "ls1intum/Ares2"), List.of()));
        
        for (DefaultTeamRecord defaultTeam : defaultTeams) {
            Team team = teamRepository.findByName(defaultTeam.name()).orElse(new Team());
            team.setName(defaultTeam.name());
            team.setColor(defaultTeam.color());
            defaultTeam.repositories().forEach(repositoryName -> repositoryRepository.findByNameWithOwner(repositoryName).ifPresent(team::addRepository));
            defaultTeam.labels().forEach(labelName -> labelRepository.findByName(labelName).ifPresent(team::addLabel));
            teamRepository.save(team);
        }
        teamRepository.flush();
        return true;
    }

    private record DefaultTeamRecord (
        String name,
        String color,
        List<String> repositories,
        List<String> labels
    ) {}
}
