package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
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

    public List<TeamInfoDTO> getAllTeams() {
        List<TeamInfoDTO> teams = teamRepository.findAll().stream().map(TeamInfoDTO::fromTeam).toList();
        return teams;
    }

    public TeamInfoDTO hideTeam(Long id, Boolean hidden) {
        Team team = teamRepository.findById(id).orElseThrow();
        team.setHidden(hidden);
        teamRepository.saveAndFlush(team);
        return TeamInfoDTO.fromTeam(team);
    }

    public Team createTeam(String name, String color) {
        logger.info("Creating team with name: " + name + " and color: " + color);
        Team team = new Team();
        team.setName(name);
        team.setColor(color);
        return teamRepository.saveAndFlush(team);
    }

    @Transactional
    public void setupDefaultTeams() {
        logger.info("Creating default teams");
        List<DefaultTeamRecord> defaultTeams = new ArrayList<>();
        defaultTeams.add(
            new DefaultTeamRecord(
                "Artemis",
                "#69feff",
                List.of(new RepositoryWithLabels("ls1intum/Artemis", List.of()))
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Iris",
                "#69feff",
                List.of(
                    new RepositoryWithLabels("ls1intum/Artemis", List.of("iris", "component:iris")),
                    new RepositoryWithLabels("ls1intum/Pyris", List.of())
                )
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Athena",
                "#69feff",
                List.of(
                    new RepositoryWithLabels("ls1intum/Artemis", List.of("athena")),
                    new RepositoryWithLabels("ls1intum/Athena", List.of())
                )
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Atlas",
                "#69feff",
                List.of(new RepositoryWithLabels("ls1intum/Artemis", List.of("atlas")))
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Programming",
                "#69feff",
                List.of(
                    new RepositoryWithLabels("ls1intum/Artemis", List.of("programming", "component:programming")),
                    new RepositoryWithLabels("ls1intum/Pyris", List.of()),
                    new RepositoryWithLabels("ls1intum/Atlas", List.of())
                )
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Hephaestus",
                "#69feff",
                List.of(new RepositoryWithLabels("ls1intum/Hephaestus", List.of()))
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Communication",
                "#69feff",
                List.of(
                    new RepositoryWithLabels("ls1intum/Artemis", List.of("communication", "component:communication"))
                )
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Lectures",
                "#69feff",
                List.of(new RepositoryWithLabels("ls1intum/Artemis", List.of("lecture", "Component:Lecture")))
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Usability",
                "#69feff",
                List.of(new RepositoryWithLabels("ls1intum/Artemis", List.of()))
            )
        );
        defaultTeams.add(
            new DefaultTeamRecord(
                "Ares",
                "#69feff",
                List.of(
                    new RepositoryWithLabels("ls1intum/Ares", List.of()),
                    new RepositoryWithLabels("ls1intum/Ares2", List.of())
                )
            )
        );

        for (DefaultTeamRecord defaultTeam : defaultTeams) {
            Team team = teamRepository.findByName(defaultTeam.name()).orElse(new Team());
            team.setName(defaultTeam.name());
            team.setColor(defaultTeam.color());

            defaultTeam
                .repositories()
                .forEach(repositoryWithLabels -> {
                    var repository = repositoryRepository.findByNameWithOwner(repositoryWithLabels.nameWithOwner());
                    if (repository.isEmpty()) {
                        logger.warn("Repository " + repositoryWithLabels.nameWithOwner() + " not found");
                        return;
                    }
                    team.addRepository(repository.get());
                    repositoryWithLabels
                        .labels()
                        .forEach(labelName ->
                            labelRepository
                                .findByRepositoryIdAndName(repository.get().getId(), labelName)
                                .ifPresent(team::addLabel)
                        );
                });

            teamRepository.save(team);
        }
        teamRepository.flush();
    }

    private record DefaultTeamRecord(String name, String color, List<RepositoryWithLabels> repositories) {}

    private record RepositoryWithLabels(String nameWithOwner, List<String> labels) {}
}
