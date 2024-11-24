package de.tum.in.www1.hephaestus.workspace;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.syncing.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.syncing.NatsConsumerService;

@Service
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    @Autowired
    private NatsConsumerService natsConsumerService;

    @Autowired
    private GitHubDataSyncService gitHubDataSyncService;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private LabelRepository labelRepository;
    
    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${hephaestus.workspace.init-default}")
    private boolean initDefaultWorkspace;

    @Value("${hephaestus.workspace.default.repositories-to-monitor}")
    private String[] defaultRepositoriesToMonitor;

    @Value("${monitoring.run-on-startup}")
    private boolean runMonitoringOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Workspace workspace = getWorkspace();
        Set<RepositoryToMonitor> repositoriesToMonitor = workspace.getRepositoriesToMonitor();

        if (isNatsEnabled) {
            repositoriesToMonitor.forEach(repositoryToMonitor -> {
                natsConsumerService.startConsumingRepositoryToMonitorAsync(repositoryToMonitor);
            });
        }

        if (runMonitoringOnStartup) {
            logger.info("Running monitoring on startup");
            repositoriesToMonitor.forEach(repositoryToMonitor -> {
                gitHubDataSyncService.syncRepositoryToMonitor(repositoryToMonitor);
            });
            gitHubDataSyncService.syncUsers(workspace);
            logger.info("Finished running monitoring on startup");

            if (initDefaultWorkspace) {
                // Setup default teams
                logger.info("Setting up default teams");
                teamService.setupDefaultTeams();
            }
        }
    }

    private Workspace createInitialWorkspace() {
        Workspace workspace = new Workspace();
        
        // If the default workspace should be initialized, add the default repositories to monitor
        if (initDefaultWorkspace) {
            logger.info("Initializing default workspace");
            Set<RepositoryToMonitor> workspaceRepositoriesToMonitor = Set.of(defaultRepositoriesToMonitor)
                .stream()
                .map(nameWithOwner -> {
                    var repositoryToMonitor = new RepositoryToMonitor();
                    repositoryToMonitor.setNameWithOwner(nameWithOwner);
                    repositoryToMonitor.setWorkspace(workspace);
                    return repositoryToMonitor;
                })
                .collect(Collectors.toSet());
            workspace.setRepositoriesToMonitor(workspaceRepositoriesToMonitor);
        }

        return workspaceRepository.save(workspace);
    }

    public Workspace getWorkspace() {
        return workspaceRepository.findFirstByOrderByIdAsc().orElseGet(this::createInitialWorkspace);
    }

    public List<String> getRepositoriesToMonitor() {
        logger.info("Getting repositories to monitor");
        return getWorkspace()
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .toList();
    }

    public void addRepositoryToMonitor(String nameWithOwner) throws RepositoryAlreadyMonitoredException, RepositoryNotFoundException {
        logger.info("Adding repository to monitor: " + nameWithOwner);
        Workspace workspace = getWorkspace();
        
        if (workspace.getRepositoriesToMonitor().stream().anyMatch(r -> r.getNameWithOwner().equals(nameWithOwner))) {
            logger.info("Repository is already being monitored");
            throw new RepositoryAlreadyMonitoredException(nameWithOwner);
        }

        // Validate that repository exists
        var repository = repositorySyncService.syncRepository(nameWithOwner);
        if (repository.isEmpty()) {
            logger.info("Repository does not exist");
            throw new RepositoryNotFoundException(nameWithOwner);
        }

        RepositoryToMonitor repositoryToMonitor = new RepositoryToMonitor();
        repositoryToMonitor.setNameWithOwner(nameWithOwner);
        repositoryToMonitor.setWorkspace(workspace);
        repositoryToMonitorRepository.save(repositoryToMonitor);
        workspace.getRepositoriesToMonitor().add(repositoryToMonitor);
        workspaceRepository.save(workspace);

        // Start syncing the repository
        if (isNatsEnabled) {
            natsConsumerService.startConsumingRepositoryToMonitorAsync(repositoryToMonitor);
        }
        gitHubDataSyncService.syncRepositoryToMonitorAsync(repositoryToMonitor);
    }

    public void removeRepositoryToMonitor(String nameWithOwner) throws RepositoryNotFoundException {
        logger.info("Removing repository from monitor: " + nameWithOwner);
        Workspace workspace = getWorkspace();
        
        RepositoryToMonitor repositoryToMonitor = workspace.getRepositoriesToMonitor().stream()
            .filter(r -> r.getNameWithOwner().equals(nameWithOwner))
            .findFirst()
            .orElse(null);

        if (repositoryToMonitor == null) {
            logger.info("Repository is not being monitored");
            throw new RepositoryNotFoundException(nameWithOwner);
        }

        repositoryToMonitorRepository.delete(repositoryToMonitor);
        workspace.getRepositoriesToMonitor().remove(repositoryToMonitor);
        workspaceRepository.save(workspace);

        // Delete repository if present
        var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
        if (repository.isEmpty()) {
            return;
        }

        repository.get().getLabels().forEach(label -> label.removeAllTeams());
        repository.get().removeAllTeams();
        repositoryRepository.delete(repository.get());

        if (isNatsEnabled) {
            natsConsumerService.stopConsumingRepositoryToMonitorAsync(repositoryToMonitor);
        }
    }

    public List<UserTeamsDTO> getUsersWithTeams() {
        logger.info("Getting all users with their teams");
        return userRepository.findAll().stream().map(UserTeamsDTO::fromUser).toList();
    }

    public Optional<UserInfoDTO> addTeamToUser(String login, Long teamId) {
        logger.info("Adding team (ID: " + teamId + ") to user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.addMember(user);
        teamRepository.save(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public Optional<UserInfoDTO> removeUserFromTeam(String login, Long teamId) {
        logger.info("Removing team (ID: " + teamId + ") from user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.removeMember(user);
        teamRepository.save(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public TeamInfoDTO createTeam(String name, String color) {
        logger.info("Creating team with name: " + name + " and color: " + color);
        return TeamInfoDTO.fromTeam(teamService.createTeam(name, color));
    }

    public Optional<TeamInfoDTO> addRepositoryToTeam(Long teamId, String repositoryName) {
        logger.info("Adding repository with name: " + repositoryName + " to team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        repositoryRepository.findByNameWithOwner(repositoryName).ifPresent(team::addRepository);
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> removeRepositoryFromTeam(Long teamId, String repositoryName) {
        logger.info("Removing repository with name: " + repositoryName + " from team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        repositoryRepository.findByNameWithOwner(repositoryName).ifPresent(team::removeRepository);
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> addLabelToTeam(Long teamId, Long repositoryId, String label) {
        logger.info("Adding label '" + label + "' of repository with ID: " + repositoryId + " to team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        Optional<Label> labelEntity = labelRepository.findByRepositoryIdAndName(repositoryId, label);
        if (labelEntity.isEmpty()) {
            return Optional.empty();
        }
        team.addLabel(labelEntity.get());
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> removeLabelFromTeam(Long teamId, Long labelId) {
        logger.info("Removing label with ID: " + labelId + " from team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        Optional<Label> labelEntity = labelRepository.findById(labelId);
        if (labelEntity.isEmpty()) {
            return Optional.empty();
        }
        team.removeLabel(labelEntity.get());
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> deleteTeam(Long teamId) {
        logger.info("Deleting team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        teamRepository.delete(optionalTeam.get());
        return Optional.of(TeamInfoDTO.fromTeam(optionalTeam.get()));
    }

    @Transactional
    public void automaticallyAssignTeams() {
        logger.info("Automatically assigning teams");

        var teams = teamRepository.findAll();
        teams.forEach(team -> {
            var contributors = userRepository.findAllContributingToTeam(team.getId());
            contributors.forEach(contributor -> {
                contributor.addTeam(team);
                userRepository.save(contributor);
                team.addMember(contributor);
                teamRepository.save(team);
            });
        });
    }
}
