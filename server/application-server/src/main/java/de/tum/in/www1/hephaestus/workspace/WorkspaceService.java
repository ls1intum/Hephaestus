package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardMode;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.organization.Organization;
import de.tum.in.www1.hephaestus.organization.OrganizationService;
import de.tum.in.www1.hephaestus.syncing.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.syncing.NatsConsumerService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.kohsuke.github.GHRepositorySelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private TeamRepository teamRepository;

    @Autowired
    private TeamInfoDTOConverter teamInfoDTOConverter;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaguePointsCalculationService leaguePointsCalculationService;

    @Autowired
    private OrganizationService organizationService;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${monitoring.run-on-startup}")
    private boolean runMonitoringOnStartup;

    /**
     * Prepare every workspace and start monitoring/sync routines for those that are ready.
     * Intended to run after provisioning so the workspace catalog is populated.
     */
    public void activateAllWorkspaces() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        if (workspaces.isEmpty()) {
            logger.info("No workspaces found on startup; waiting for GitHub App backfill or manual provisioning.");
            return;
        }

        List<Workspace> prepared = new ArrayList<>(workspaces.size());
        for (Workspace workspace : workspaces) {
            prepared.add(ensureWorkspaceMetadata(workspace));
        }

        Set<String> organizationConsumersStarted = new HashSet<>();
        for (Workspace workspace : prepared) {
            if (shouldSkipActivation(workspace)) {
                continue;
            }
            activateWorkspace(workspace, organizationConsumersStarted);
        }
    }

    private boolean shouldSkipActivation(Workspace workspace) {
        if (
            workspace.getGitProviderMode() == Workspace.GitProviderMode.PAT_ORG &&
            isBlank(workspace.getPersonalAccessToken())
        ) {
            logger.info(
                "Workspace id={} remains idle: PAT mode without personal access token. Configure a token or migrate to the GitHub App.",
                workspace.getId()
            );
            return true;
        }
        return false;
    }

    private void activateWorkspace(Workspace workspace, Set<String> organizationConsumersStarted) {
        Set<RepositoryToMonitor> repositoriesToMonitor = workspace.getRepositoriesToMonitor();

        if (isNatsEnabled) {
            repositoriesToMonitor.forEach(repositoryToMonitor ->
                natsConsumerService.startConsumingRepositoryToMonitorAsync(repositoryToMonitor)
            );
            String organizationLogin = workspace.getAccountLogin();
            if (!isBlank(organizationLogin)) {
                String key = organizationLogin.toLowerCase();
                if (organizationConsumersStarted.add(key)) {
                    natsConsumerService.startConsumingOrganizationAsync(organizationLogin);
                }
            }
        }

        if (!runMonitoringOnStartup) {
            return;
        }

        logger.info("Running monitoring on startup for workspace id={}", workspace.getId());

        CompletableFuture<?>[] repoFutures = repositoriesToMonitor
            .stream()
            .map(repo -> CompletableFuture.runAsync(() -> gitHubDataSyncService.syncRepositoryToMonitor(repo)))
            .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> reposDone = CompletableFuture.allOf(repoFutures);

        CompletableFuture<Void> usersFuture = reposDone
            .thenRunAsync(() -> {
                logger.info("All repositories synced, now syncing users for workspace id={}", workspace.getId());
                gitHubDataSyncService.syncUsers(workspace);
            })
            .exceptionally(ex -> {
                logger.error("Error during syncUsers: {}", ex.getMessage(), ex);
                return null;
            });

        CompletableFuture<Void> teamsFuture = usersFuture
            .thenRunAsync(() -> {
                logger.info("Syncing teams for workspace id={}", workspace.getId());
                gitHubDataSyncService.syncTeams(workspace);
            })
            .exceptionally(ex -> {
                logger.error("Error during syncTeams: {}", ex.getMessage(), ex);
                return null;
            });

        CompletableFuture.allOf(teamsFuture).thenRun(() ->
            logger.info("Finished running monitoring on startup for workspace id={}", workspace.getId())
        );
    }

    public Workspace getWorkspaceByRepositoryOwner(String nameWithOwner) {
        return workspaceRepository
            .findByRepositoriesToMonitor_NameWithOwner(nameWithOwner)
            .or(() -> resolveFallbackWorkspace("repository " + nameWithOwner))
            .orElseThrow(() -> new IllegalStateException("No workspace found for repository: " + nameWithOwner));
    }

    public List<Workspace> listAllWorkspaces() {
        return workspaceRepository.findAll();
    }

    public List<String> getRepositoriesToMonitor() {
        logger.info("Getting repositories to monitor from all workspaces");
        return workspaceRepository
            .findAll()
            .stream()
            .flatMap(ws -> ws.getRepositoriesToMonitor().stream())
            .map(RepositoryToMonitor::getNameWithOwner)
            .distinct()
            .toList();
    }

    public void addRepositoryToMonitor(String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, RepositoryNotFoundException {
        logger.info("Adding repository to monitor: " + nameWithOwner);
        Workspace workspace = resolveWorkspaceForRepo(nameWithOwner);

        if (workspace.getRepositoriesToMonitor().stream().anyMatch(r -> r.getNameWithOwner().equals(nameWithOwner))) {
            logger.info("Repository is already being monitored");
            throw new RepositoryAlreadyMonitoredException(nameWithOwner);
        }

        var workspaceId = workspace.getId();

        // Validate that repository exists
        var repository = repositorySyncService.syncRepository(workspaceId, nameWithOwner);
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
        Workspace workspace = getWorkspaceByRepositoryOwner(nameWithOwner);

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
        repositoryRepository.delete(repository.get());

        if (isNatsEnabled) {
            natsConsumerService.stopConsumingRepositoryToMonitorAsync(repositoryToMonitor);
        }
    }

    public List<UserTeamsDTO> getUsersWithTeams() {
        logger.info("Getting all users with their teams");
        return userRepository.findAllHuman().stream().map(UserTeamsDTO::fromUser).toList();
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
        return Optional.ofNullable(teamInfoDTOConverter.convert(team));
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
        return Optional.ofNullable(teamInfoDTOConverter.convert(team));
    }

    //TODO: Method never used
    public Optional<TeamInfoDTO> deleteTeam(Long teamId) {
        logger.info("Deleting team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        teamRepository.delete(optionalTeam.get());
        return Optional.ofNullable(teamInfoDTOConverter.convert(optionalTeam.get()));
    }

    //TODO: Method never used
    @Transactional
    public void automaticallyAssignTeams() {
        logger.info("Automatically assigning teams");

        var teams = teamRepository.findAll();
        teams.forEach(team -> {
            var contributors = userRepository.findAllContributingToTeam(team.getId());
            contributors.forEach(contributor -> {
                var membership = new TeamMembership(team, contributor, TeamMembership.Role.MEMBER);
                team.addMembership(membership);
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
        var now = Instant.now();
        var weekAgo = now.minus(7, ChronoUnit.DAYS);

        // While we still have reviews in the past, calculate leaderboard and update points
        do {
            var leaderboard = leaderboardService.createLeaderboard(
                weekAgo,
                now,
                Optional.empty(),
                Optional.empty(),
                Optional.of(LeaderboardMode.INDIVIDUAL)
            );
            if (leaderboard.isEmpty()) {
                break;
            }

            // Update league points for each user
            leaderboard.forEach(entry -> {
                var leaderboardUser = entry.getUser();
                if (leaderboardUser == null) {
                    return;
                }
                var user = userRepository.findByLoginWithEagerMergedPullRequests(leaderboardUser.login()).orElseThrow();
                int newPoints = leaguePointsCalculationService.calculateNewPoints(user, entry);
                user.setLeaguePoints(newPoints);
                userRepository.save(user);
            });

            // Move time window back one week
            now = weekAgo;
            weekAgo = weekAgo.minus(7, ChronoUnit.DAYS);
            // only recalculate points for the last year
        } while (weekAgo.isAfter(Instant.parse("2024-01-01T00:00:00Z")));

        logger.info("Finished recalculating league points");
    }

    @Transactional
    public Workspace ensureForInstallation(
        long installationId,
        String accountLogin,
        GHRepositorySelection repositorySelection
    ) {
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElse(null);

        if (workspace == null && !isBlank(accountLogin)) {
            workspace = workspaceRepository.findByAccountLoginIgnoreCase(accountLogin).orElse(null);
            if (workspace != null) {
                logger.info(
                    "Linking existing workspace id={} login={} to installation {}.",
                    workspace.getId(),
                    accountLogin,
                    installationId
                );
            }
        }

        if (workspace == null) {
            workspace = new Workspace();
        }

        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(installationId);
        workspace.setPersonalAccessToken(null);

        if (!isBlank(accountLogin)) {
            workspace.setAccountLogin(accountLogin);
        }

        if (repositorySelection != null) {
            workspace.setGithubRepositorySelection(repositorySelection);
        }

        if (workspace.getInstallationLinkedAt() == null) {
            workspace.setInstallationLinkedAt(Instant.now());
        }

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateAccountLogin(Long workspaceId, String accountLogin) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        if (!Objects.equals(workspace.getAccountLogin(), accountLogin)) {
            workspace.setAccountLogin(accountLogin);
            workspace = workspaceRepository.save(workspace);
        }

        return workspace;
    }

    Workspace ensureWorkspaceMetadata(Workspace workspace) {
        boolean changed = false;

        if (workspace.getGitProviderMode() == null) {
            Workspace.GitProviderMode mode = workspace.getInstallationId() != null
                ? Workspace.GitProviderMode.GITHUB_APP_INSTALLATION
                : Workspace.GitProviderMode.PAT_ORG;
            workspace.setGitProviderMode(mode);
            changed = true;
        }

        if (isBlank(workspace.getAccountLogin())) {
            String derived = deriveAccountLogin(workspace);
            if (!isBlank(derived)) {
                workspace.setAccountLogin(derived);
                changed = true;
            }
        }

        if (changed) {
            workspace = workspaceRepository.save(workspace);
        }

        return workspace;
    }

    String deriveAccountLogin(Workspace workspace) {
        if (!isBlank(workspace.getAccountLogin())) {
            return workspace.getAccountLogin();
        }

        String organizationLogin = null;
        Long installationId = workspace.getInstallationId();
        if (installationId != null) {
            organizationLogin = organizationService
                .getByInstallationId(installationId)
                .map(Organization::getLogin)
                .filter(login -> !isBlank(login))
                .orElse(null);
        }

        if (!isBlank(organizationLogin)) {
            return organizationLogin;
        }

        String repoOwner = workspace
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .map(this::extractOwner)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (!isBlank(repoOwner)) {
            return repoOwner;
        }

        return null;
    }

    private Optional<Workspace> resolveFallbackWorkspace(String context) {
        List<Workspace> all = workspaceRepository.findAll();
        if (all.size() == 1) {
            logger.info("Falling back to the only configured workspace id={} for {}.", all.get(0).getId(), context);
            return Optional.of(all.get(0));
        }
        logger.warn("Unable to resolve workspace for {}. Available workspace count={}", context, all.size());
        return Optional.empty();
    }

    private String extractOwner(String nameWithOwner) {
        if (isBlank(nameWithOwner)) {
            return null;
        }
        int idx = nameWithOwner.indexOf('/');
        if (idx <= 0) {
            return null;
        }
        return nameWithOwner.substring(0, idx);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Workspace resolveWorkspaceForRepo(String nameWithOwner) {
        int i = nameWithOwner.indexOf('/');
        if (i < 1) {
            throw new IllegalArgumentException("Expected 'owner/name', got: " + nameWithOwner);
        }
        String owner = nameWithOwner.substring(0, i);

        return workspaceRepository
            .findByOrganization_Login(owner)
            .or(() -> workspaceRepository.findByAccountLoginIgnoreCase(owner))
            .or(() -> workspaceRepository.findByRepositoriesToMonitor_NameWithOwner(nameWithOwner))
            .or(() -> resolveFallbackWorkspace("login '" + owner + "'"))
            .orElseThrow(() ->
                new IllegalStateException(
                    "No workspace linked to organization/login '" +
                    owner +
                    "'. " +
                    "Ensure a PAT workspace exists or the GitHub App installation was reconciled."
                )
            );
    }
}
