package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLink;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardMode;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardSortType;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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
    private InstallationRepository installationRepository;

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
        Set<RepositoryToMonitor> monitors = workspace.getRepositoriesToMonitor();
        if (monitors == null || monitors.isEmpty()) {
            return;
        }

        List<RepositoryToMonitor> repositoriesToMonitor = monitors
            .stream()
            .filter(RepositoryToMonitor::isActive)
            .toList();

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
            .findActiveByRepositoryNameWithOwner(nameWithOwner)
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
            .flatMap(ws -> ws.getRepositoriesToMonitor().stream().filter(RepositoryToMonitor::isActive))
            .map(RepositoryToMonitor::getNameWithOwner)
            .distinct()
            .toList();
    }

    public void addRepositoryToMonitor(String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, RepositoryNotFoundException {
        logger.info("Adding repository to monitor: " + nameWithOwner);
        Workspace workspace = resolveWorkspaceForRepo(nameWithOwner);

        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.PAT_ORG) {
            throw new WorkspaceRepositoryMutationNotAllowedException(
                "Manual repository management is disabled for installation-backed workspaces"
            );
        }

        var existingMonitor = repositoryToMonitorRepository
            .findByWorkspaceIdAndNameWithOwnerIgnoreCaseAndSource(
                workspace.getId(),
                nameWithOwner,
                RepositoryToMonitor.Source.PAT
            )
            .orElse(null);

        if (existingMonitor != null && existingMonitor.isActive()) {
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

        var repositoryEntity = repositoryRepository.findByNameWithOwner(nameWithOwner).orElse(null);
        persistRepositoryToMonitor(
            workspace,
            nameWithOwner,
            repositoryEntity,
            RepositoryToMonitor.Source.PAT,
            true,
            Instant.now()
        );
        workspaceRepository.save(workspace);
    }

    public void removeRepositoryToMonitor(String nameWithOwner) throws RepositoryNotFoundException {
        logger.info("Removing repository from monitor: " + nameWithOwner);
        Workspace workspace = getWorkspaceByRepositoryOwner(nameWithOwner);

        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.PAT_ORG) {
            throw new WorkspaceRepositoryMutationNotAllowedException(
                "Manual repository management is disabled for installation-backed workspaces"
            );
        }

        RepositoryToMonitor repositoryToMonitor = repositoryToMonitorRepository
            .findByWorkspaceIdAndNameWithOwnerIgnoreCaseAndSource(
                workspace.getId(),
                nameWithOwner,
                RepositoryToMonitor.Source.PAT
            )
            .filter(RepositoryToMonitor::isActive)
            .orElse(null);

        if (repositoryToMonitor == null) {
            logger.info("Repository is not being monitored");
            throw new RepositoryNotFoundException(nameWithOwner);
        }

        deactivateRepositoryMonitor(repositoryToMonitor, Instant.now());
        workspaceRepository.save(workspace);
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
                "all",
                LeaderboardSortType.SCORE,
                LeaderboardMode.INDIVIDUAL
            );
            if (leaderboard.isEmpty()) {
                break;
            }

            // Update league points for each user
            leaderboard.forEach(entry -> {
                var leaderboardUser = entry.user();
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
            .filter(RepositoryToMonitor::isActive)
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

    @Transactional
    public boolean registerInstallationRepositorySnapshot(
        Workspace workspace,
        long repositoryId,
        String nameWithOwner,
        String name,
        boolean isPrivate
    ) {
        if (workspace == null || workspace.getGitProviderMode() != Workspace.GitProviderMode.GITHUB_APP_INSTALLATION) {
            return false;
        }

        Long installationId = workspace.getInstallationId();
        if (installationId == null) {
            logger.debug(
                "Workspace {} missing installation id while registering installation snapshot for {}",
                workspace.getId(),
                nameWithOwner
            );
            return false;
        }

        Installation installation = installationRepository
            .findById(installationId)
            .orElseGet(() -> {
                Installation stub = new Installation();
                stub.setId(installationId);
                stub.setCreatedAt(Instant.now());
                stub.setUpdatedAt(Instant.now());
                stub.setRepositorySelection(Installation.RepositorySelection.UNKNOWN);
                return installationRepository.save(stub);
            });

        Repository repository = repositorySyncService.upsertFromInstallationPayload(
            repositoryId,
            nameWithOwner,
            name,
            isPrivate
        );

        InstallationRepositoryLink link = installation
            .getRepositoryLinks()
            .stream()
            .filter(
                existing ->
                    existing.getRepository() != null && Objects.equals(existing.getRepository().getId(), repositoryId)
            )
            .findFirst()
            .orElse(null);

        boolean created = false;
        if (link == null) {
            link = new InstallationRepositoryLink();
            link.setId(new InstallationRepositoryLink.Id(installationId, repositoryId));
            link.setInstallation(installation);
            installation.getRepositoryLinks().add(link);
            created = true;
        }

        boolean wasActive = link.isActive();
        link.setInstallation(installation);
        link.setRepository(repository);
        link.setActive(true);
        link.setLinkedAt(link.getLinkedAt() != null ? link.getLinkedAt() : Instant.now());
        link.setRemovedAt(null);
        link.setLastSyncedAt(Instant.now());

        installationRepository.save(installation);

        RepositoryToMonitor existingMonitor = findExistingMonitor(
            workspace,
            repository,
            nameWithOwner,
            RepositoryToMonitor.Source.INSTALLATION
        );
        boolean monitorWasActive = existingMonitor != null && existingMonitor.isActive();

        RepositoryToMonitor persistedMonitor = persistRepositoryToMonitor(
            workspace,
            nameWithOwner,
            repository,
            RepositoryToMonitor.Source.INSTALLATION,
            false,
            link.getLinkedAt()
        );

        boolean monitorActivated = persistedMonitor.isActive() && !monitorWasActive;

        return created || !wasActive || monitorActivated;
    }

    @Transactional
    public void reconcileRepositoriesForInstallation(Installation installation) {
        if (installation == null || installation.getId() == null) {
            logger.debug("Skipping workspace reconciliation: installation missing identifier");
            return;
        }

        Installation persistedInstallation = installationRepository
            .findById(installation.getId())
            .orElse(null);

        if (persistedInstallation == null) {
            logger.debug("Installation {} no longer exists. Skipping repository reconciliation.", installation.getId());
            return;
        }

        var workspaceOptional = workspaceRepository.findByInstallationId(persistedInstallation.getId());
        if (workspaceOptional.isEmpty()) {
            logger.debug(
                "No workspace linked to installation {}. Skipping repository reconciliation.",
                installation.getId()
            );
            return;
        }

        Workspace workspace = workspaceOptional.get();
        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.GITHUB_APP_INSTALLATION) {
            logger.debug("Workspace id={} not using GitHub App mode. Skipping reconciliation.", workspace.getId());
            return;
        }

        Set<InstallationRepositoryLink> repositoryLinks = Optional.ofNullable(
            persistedInstallation.getRepositoryLinks()
        ).orElseGet(Set::of);

        var activeLinksByRepoId = repositoryLinks
            .stream()
            .filter(InstallationRepositoryLink::isActive)
            .filter(link -> link.getRepository() != null && !isBlank(link.getRepository().getNameWithOwner()))
            .collect(
                Collectors.toMap(
                    link -> link.getRepository().getId(),
                    link -> link,
                    (first, second) -> second,
                    LinkedHashMap::new
                )
            );

        Set<Long> processedActiveRepoIds = new LinkedHashSet<>();
        activeLinksByRepoId
            .values()
            .forEach(link -> {
                Repository repository = link.getRepository();
                String nameWithOwner = repository.getNameWithOwner();
                persistRepositoryToMonitor(
                    workspace,
                    nameWithOwner,
                    repository,
                    RepositoryToMonitor.Source.INSTALLATION,
                    false,
                    link.getLinkedAt()
                );
                processedActiveRepoIds.add(repository.getId());
            });

        repositoryLinks
            .stream()
            .filter(link -> !link.isActive())
            .filter(link -> link.getRepository() != null && !isBlank(link.getRepository().getNameWithOwner()))
            .forEach(link -> {
                RepositoryToMonitor monitor = findExistingMonitor(
                    workspace,
                    link.getRepository(),
                    link.getRepository().getNameWithOwner(),
                    RepositoryToMonitor.Source.INSTALLATION
                );
                if (monitor != null) {
                    deactivateRepositoryMonitor(monitor, link.getRemovedAt());
                }
            });

        workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(monitor -> monitor.getSource() == RepositoryToMonitor.Source.INSTALLATION)
            .filter(RepositoryToMonitor::isActive)
            .filter(monitor -> monitor.getRepository() != null)
            .filter(monitor -> !processedActiveRepoIds.contains(monitor.getRepository().getId()))
            .forEach(monitor -> deactivateRepositoryMonitor(monitor, Instant.now()));

        workspaceRepository.save(workspace);
    }

    private RepositoryToMonitor persistRepositoryToMonitor(
        Workspace workspace,
        String nameWithOwner,
        Repository repository,
        RepositoryToMonitor.Source source,
        boolean triggerSync,
        Instant linkedAt
    ) {
        RepositoryToMonitor monitor = findExistingMonitor(workspace, repository, nameWithOwner, source);
        boolean isNew = false;
        boolean wasActive = false;

        if (monitor == null) {
            monitor = new RepositoryToMonitor();
            monitor.setWorkspace(workspace);
            monitor.setSource(source);
            workspace.getRepositoriesToMonitor().add(monitor);
            isNew = true;
        } else {
            wasActive = monitor.isActive();
        }

        monitor.setNameWithOwner(nameWithOwner);
        monitor.setRepository(repository);
        monitor.setSource(source);
        monitor.setInstallationId(
            source == RepositoryToMonitor.Source.INSTALLATION ? workspace.getInstallationId() : null
        );
        if (linkedAt != null) {
            monitor.setLinkedAt(linkedAt);
        } else if (monitor.getLinkedAt() == null) {
            monitor.setLinkedAt(Instant.now());
        }
        monitor.setUnlinkedAt(null);
        monitor.setActive(true);

        RepositoryToMonitor saved = repositoryToMonitorRepository.save(monitor);

        if (isNatsEnabled && (isNew || !wasActive)) {
            natsConsumerService.startConsumingRepositoryToMonitorAsync(saved);
        }

        if (triggerSync && source == RepositoryToMonitor.Source.PAT) {
            gitHubDataSyncService.syncRepositoryToMonitorAsync(saved);
        }

        return saved;
    }

    private RepositoryToMonitor findExistingMonitor(
        Workspace workspace,
        Repository repository,
        String nameWithOwner,
        RepositoryToMonitor.Source source
    ) {
        return workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(monitor -> monitor.getSource() == source)
            .filter(monitor -> {
                if (repository != null && monitor.getRepository() != null) {
                    return Objects.equals(monitor.getRepository().getId(), repository.getId());
                }
                return monitor.getNameWithOwner().equalsIgnoreCase(nameWithOwner);
            })
            .findFirst()
            .orElseGet(() -> {
                if (repository != null && repository.getId() != null) {
                    return repositoryToMonitorRepository
                        .findByWorkspaceIdAndRepository_IdAndSource(workspace.getId(), repository.getId(), source)
                        .orElse(null);
                }
                return repositoryToMonitorRepository
                    .findByWorkspaceIdAndNameWithOwnerIgnoreCaseAndSource(workspace.getId(), nameWithOwner, source)
                    .orElse(null);
            });
    }

    private void deactivateRepositoryMonitor(RepositoryToMonitor repositoryToMonitor, Instant removedAt) {
        boolean wasActive = repositoryToMonitor.isActive();
        repositoryToMonitor.setActive(false);
        repositoryToMonitor.setUnlinkedAt(removedAt != null ? removedAt : Instant.now());
        repositoryToMonitorRepository.save(repositoryToMonitor);

        if (wasActive && isNatsEnabled) {
            natsConsumerService.stopConsumingRepositoryToMonitorAsync(repositoryToMonitor);
        }
    }

    private Workspace resolveWorkspaceForRepo(String nameWithOwner) {
        int i = nameWithOwner.indexOf('/');
        if (i < 1) {
            throw new IllegalArgumentException("Expected 'owner/name', got: " + nameWithOwner);
        }
        String owner = nameWithOwner.substring(0, i);

        Optional<Workspace> workspaceOptional = workspaceRepository.findByOrganization_Login(owner);
        if (workspaceOptional.isEmpty()) {
            workspaceOptional = workspaceRepository.findByAccountLoginIgnoreCase(owner);
        }
        if (workspaceOptional.isEmpty()) {
            workspaceOptional = workspaceRepository.findActiveByRepositoryNameWithOwner(nameWithOwner);
        }
        if (workspaceOptional.isEmpty()) {
            workspaceOptional = resolveFallbackWorkspace("login '" + owner + "'");
        }

        return workspaceOptional.orElseThrow(() ->
            new IllegalStateException(
                "No workspace linked to organization/login '" +
                owner +
                "'. " +
                "Ensure a PAT workspace exists or the GitHub App installation was reconciled."
            )
        );
    }

    @Transactional
    public void syncInstallationRepositoryLinks(Long installationId, Set<Long> activeRepositoryIds) {
        if (installationId == null) {
            logger.debug("Skipping repository link sync: missing installation id");
            return;
        }

        var installationOptional = installationRepository.findById(installationId);
        if (installationOptional.isEmpty()) {
            logger.debug("Skipping repository link sync: installation {} not found", installationId);
            return;
        }

        Installation installation = installationOptional.get();
        Set<Long> expectedActive = activeRepositoryIds != null ? activeRepositoryIds : Set.of();
        Instant referenceTime = Instant.now();
        boolean changed = false;

        for (InstallationRepositoryLink link : installation.getRepositoryLinks()) {
            var repository = link.getRepository();
            if (
                link.isActive() &&
                repository != null &&
                repository.getId() != null &&
                !expectedActive.contains(repository.getId())
            ) {
                link.setActive(false);
                link.setRemovedAt(referenceTime);
                link.setLastSyncedAt(referenceTime);
                changed = true;
            }
        }

        if (changed) {
            installationRepository.save(installation);
        }

        reconcileRepositoriesForInstallation(installation);
    }
}
