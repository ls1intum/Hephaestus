package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.enums.RepositorySelection;
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
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.syncing.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.syncing.NatsConsumerService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

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

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaguePointsCalculationService leaguePointsCalculationService;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${hephaestus.workspace.init-default}")
    private boolean initDefaultWorkspace;

    @Value("${hephaestus.workspace.default.organization}")
    private String defaultOrganization;

    @Value("${hephaestus.workspace.default.repositories-to-monitor}")
    private String[] defaultRepositoriesToMonitor;

    @Value("${monitoring.run-on-startup}")
    private boolean runMonitoringOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Workspace workspace = getWorkspace();
        Set<RepositoryToMonitor> repositoriesToMonitor = workspace.getRepositoriesToMonitor();

        if (isNatsEnabled) {
            repositoriesToMonitor.forEach(repositoryToMonitor ->
                natsConsumerService.startConsumingRepositoryToMonitorAsync(repositoryToMonitor)
            );
            natsConsumerService.startConsumingOrganizationAsync(defaultOrganization);
        }

        if (runMonitoringOnStartup) {
            logger.info("Running monitoring on startup");

            // Run all repository syncs asynchronously
            CompletableFuture<?>[] repoFutures = repositoriesToMonitor
                .stream()
                .map(repo -> CompletableFuture.runAsync(() -> gitHubDataSyncService.syncRepositoryToMonitor(repo)))
                .toArray(CompletableFuture[]::new);
            CompletableFuture<Void> reposDone = CompletableFuture.allOf(repoFutures);

            // When all repository syncs complete, then sync users
            CompletableFuture<Void> usersFuture = reposDone
                .thenRunAsync(() -> {
                    logger.info("All repositories synced, now syncing users");
                    gitHubDataSyncService.syncUsers(workspace);
                })
                .exceptionally(ex -> {
                    logger.error("Error during syncUsers: {}", ex.getMessage(), ex);
                    return null;
                });

            // When all users syncs complete, then sync teams
            CompletableFuture<Void> teamsFuture = usersFuture
                .thenRunAsync(() -> {
                    logger.info("Syncing teams");
                    gitHubDataSyncService.syncTeams(workspace);
                })
                .exceptionally(ex -> {
                    logger.error("Error during syncTeams: {}", ex.getMessage(), ex);
                    return null;
                });

            CompletableFuture.allOf(teamsFuture).thenRun(() -> {
                //TODO: to be removed after teamV2 is released
                if (initDefaultWorkspace) {
                    // Setup default teams
                    logger.info("Setting up default teams");
                    teamService.setupDefaultTeams();
                }
                logger.info("Finished running monitoring on startup");
            });
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
        return getWorkspace().getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList();
    }

    public void addRepositoryToMonitor(String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, RepositoryNotFoundException {
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

        RepositoryToMonitor repositoryToMonitor = workspace
            .getRepositoriesToMonitor()
            .stream()
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

        repository.get().getLabels().forEach(Label::removeAllTeams);
        repository.get().removeAllTeams();
        repositoryRepository.delete(repository.get());

        if (isNatsEnabled) {
            natsConsumerService.stopConsumingRepositoryToMonitorAsync(repositoryToMonitor);
        }
    }

    public List<UserTeamsDTO> getUsersWithTeams() {
        logger.info("Getting all users with their teams");
        return userRepository.findAllHuman().stream().map(UserTeamsDTO::fromUser).toList();
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
        logger.info(
            "Adding label '" + label + "' of repository with ID: " + repositoryId + " to team with ID: " + teamId
        );
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

    /**
     * Reset and recalculate league points for all users until 01/01/2024
     */
    @Transactional
    public void resetAndRecalculateLeagues() {
        logger.info("Resetting and recalculating league points for all users");

        // Reset all users to default points (1000)
        userRepository
            .findAll()
            .forEach(user -> {
                user.setLeaguePoints(LeaguePointsCalculationService.POINTS_DEFAULT);
                userRepository.save(user);
            });

        // Get all pull request reviews and issue comments to calculate past leaderboards
        var now = OffsetDateTime.now();
        var weekAgo = now.minusWeeks(1);

        // While we still have reviews in the past, calculate leaderboard and update points
        do {
            var leaderboard = leaderboardService.createLeaderboard(weekAgo, now, Optional.empty(), Optional.empty());
            if (leaderboard.isEmpty()) {
                break;
            }

            // Update league points for each user
            leaderboard.forEach(entry -> {
                var user = userRepository.findByLoginWithEagerMergedPullRequests(entry.user().login()).orElseThrow();
                int newPoints = leaguePointsCalculationService.calculateNewPoints(user, entry);
                user.setLeaguePoints(newPoints);
                userRepository.save(user);
            });

            // Move time window back one week
            now = weekAgo;
            weekAgo = weekAgo.minusWeeks(1);
            // only recalculate points for the last year
        } while (weekAgo.isAfter(OffsetDateTime.parse("2024-01-01T00:00:00Z")));

        logger.info("Finished recalculating league points");
    }

    @Transactional
    public Workspace ensureForInstallation(
        long installationId,
        RepositorySelection repositorySelection
    ) {
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElseGet(Workspace::new);
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(installationId);

        if (repositorySelection != null) {
            workspace.setGithubRepositorySelection(repositorySelection);
        }

        if (workspace.getInstallationLinkedAt() == null) {
            workspace.setInstallationLinkedAt(OffsetDateTime.now());
        }

        return workspaceRepository.save(workspace);
    }
}
