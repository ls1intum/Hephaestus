package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.RepositorySyncException;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardMode;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringScopeFilter;
import de.tum.in.www1.hephaestus.workspace.exception.*;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private static final boolean DEFAULT_PUBLIC_VISIBILITY = false;

    private static final Pattern SLACK_CHANNEL_ID_PATTERN = Pattern.compile("^[CGD][A-Z0-9]{8,}$");

    @Autowired
    private NatsConsumerService natsConsumerService;

    @Autowired
    @Lazy
    private GitHubDataSyncService gitHubDataSyncService;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator;

    @Autowired
    private MonitoringScopeFilter monitoringScopeFilter;

    @Autowired
    @Qualifier("monitoringExecutor")
    private AsyncTaskExecutor monitoringExecutor;

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

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private GitHubUserSyncService gitHubUserSyncService;

    @Autowired
    private GitHubAppTokenService gitHubAppTokenService;

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

        Set<String> organizationConsumersStarted = ConcurrentHashMap.newKeySet();

        // Activate all workspaces in parallel for scalability.
        // Each workspace's monitoring runs independently and can sync repos concurrently.
        List<CompletableFuture<Void>> activationFutures = prepared
            .stream()
            .filter(workspace -> !shouldSkipActivation(workspace))
            .map(workspace ->
                CompletableFuture.runAsync(
                    () -> activateWorkspace(workspace, organizationConsumersStarted),
                    monitoringExecutor
                )
            )
            .toList();

        // Wait for all workspace activations to complete (non-blocking to main thread
        // but ensures all are started before the method returns)
        CompletableFuture.allOf(activationFutures.toArray(CompletableFuture[]::new)).exceptionally(ex -> {
            logger.error("Error during workspace activation: {}", ex.getMessage(), ex);
            return null;
        });
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
        if (!monitoringScopeFilter.isWorkspaceAllowed(workspace)) {
            logger.info("Workspace id={} skipped: monitoring filters active.", workspace.getId());
            return;
        }

        Set<RepositoryToMonitor> repositoriesToMonitor = workspace.getRepositoriesToMonitor();
        var eligibleRepositories = repositoriesToMonitor
            .stream()
            .filter(monitoringScopeFilter::isRepositoryAllowed)
            .toList();

        if (runMonitoringOnStartup) {
            logger.info("Running monitoring on startup for workspace id={}", workspace.getId());

            // Sync repositories SEQUENTIALLY within each workspace.
            // This avoids race conditions for shared entities (Organization, Users)
            // and respects GitHub API rate limits per installation/PAT.
            // Workspaces themselves run in parallel using virtual threads.
            for (var repo : eligibleRepositories) {
                try {
                    gitHubDataSyncService.syncRepositoryToMonitor(repo);
                } catch (Exception ex) {
                    logger.error(
                        "Error syncing repository {}: {}",
                        repo.getNameWithOwner(),
                        LoggingUtils.sanitizeForLog(ex.getMessage()),
                        ex
                    );
                }
            }

            // Users and teams sync sequentially after all repos
            try {
                logger.info("All repositories synced, now syncing users for workspace id={}", workspace.getId());
                gitHubDataSyncService.syncUsers(workspace);
            } catch (Exception ex) {
                logger.error("Error during syncUsers: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
            }

            try {
                logger.info("Users synced, now syncing teams for workspace id={}", workspace.getId());
                gitHubDataSyncService.syncTeams(workspace);
            } catch (Exception ex) {
                logger.error("Error during syncTeams: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
            }

            logger.info("Finished running monitoring on startup for workspace id={}", workspace.getId());
        }

        // Start NATS consumer AFTER startup sync completes to avoid race conditions.
        // The startup sync ensures all entities exist before NATS starts processing
        // webhook events that might reference them.
        if (shouldUseNats(workspace)) {
            natsConsumerService.startConsumingWorkspace(workspace);
        }
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

    public Optional<Workspace> findByInstallationId(Long installationId) {
        return workspaceRepository.findByInstallationId(installationId);
    }

    public List<String> getRepositoriesToMonitor(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Getting repositories to monitor for workspace id={} (slug={})",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        return workspace.getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList();
    }

    public void addRepositoryToMonitor(String slug, String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Adding repository to monitor: {} for workspace id={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

        if (workspace.getRepositoriesToMonitor().stream().anyMatch(r -> r.getNameWithOwner().equals(nameWithOwner))) {
            logger.info("Repository is already being monitored");
            throw new RepositoryAlreadyMonitoredException(nameWithOwner);
        }

        var workspaceId = workspace.getId();

        // Validate that repository exists
        var repository = fetchRepositoryOrThrow(workspaceId, nameWithOwner);
        if (repository.isEmpty()) {
            logger.info("Repository does not exist");
            throw new EntityNotFoundException("Repository", nameWithOwner);
        }

        RepositoryToMonitor repositoryToMonitor = new RepositoryToMonitor();
        repositoryToMonitor.setNameWithOwner(nameWithOwner);
        repositoryToMonitor.setWorkspace(workspace);
        persistRepositoryMonitor(workspace, repositoryToMonitor);
    }

    public void removeRepositoryToMonitor(String slug, String nameWithOwner) throws EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Removing repository from monitor: {} for workspace id={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

        RepositoryToMonitor repositoryToMonitor = workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(r -> r.getNameWithOwner().equals(nameWithOwner))
            .findFirst()
            .orElse(null);

        if (repositoryToMonitor == null) {
            logger.info("Repository is not being monitored");
            throw new EntityNotFoundException("Repository", nameWithOwner);
        }

        deleteRepositoryMonitor(workspace, repositoryToMonitor);

        // Delete repository if present
        var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
        if (repository.isEmpty()) {
            return;
        }

        repository.get().getLabels().forEach(Label::removeAllTeams);
        repositoryRepository.delete(repository.get());
    }

    /**
     * Idempotently ensure a repository monitor exists for a given installation id without issuing extra GitHub fetches.
     */
    /**
     * Idempotently ensure a repository monitor exists for a given installation id without issuing extra GitHub fetches.
     */
    @Transactional
    public Optional<Workspace> ensureRepositoryMonitorForInstallation(long installationId, String nameWithOwner) {
        return ensureRepositoryMonitorForInstallation(installationId, nameWithOwner, false);
    }

    /**
     * Idempotently ensure a repository monitor exists for a given installation id.
     *
     * @param installationId the GitHub App installation ID
     * @param nameWithOwner the repository full name (e.g., "owner/repo")
     * @param deferSync if true, skip immediate sync (use during provisioning when activation will sync in bulk)
     */
    @Transactional
    public Optional<Workspace> ensureRepositoryMonitorForInstallation(
        long installationId,
        String nameWithOwner,
        boolean deferSync
    ) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || isBlank(nameWithOwner)) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        return ensureRepositoryMonitorInternal(workspace, nameWithOwner, deferSync);
    }

    /**
     * Remove a repository monitor for a given installation id if it exists. No-op if missing.
     */
    @Transactional
    public Optional<Workspace> removeRepositoryMonitorForInstallation(long installationId, String nameWithOwner) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || isBlank(nameWithOwner)) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        var monitorOpt = repositoryToMonitorRepository.findByWorkspaceIdAndNameWithOwner(
            workspace.getId(),
            nameWithOwner
        );
        if (monitorOpt.isEmpty()) {
            return workspaceOpt;
        }

        RepositoryToMonitor monitor = monitorOpt.get();
        return removeRepositoryMonitorInternal(workspace, monitor);
    }

    /**
     * Remove all repository monitors tied to an installation.
     */
    @Transactional
    public Optional<Workspace> removeAllRepositoryMonitorsForInstallation(long installationId) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        workspaceOpt.ifPresent(workspace -> {
            repositoryToMonitorRepository
                .findByWorkspaceId(workspace.getId())
                .forEach(monitor -> deleteRepositoryMonitor(workspace, monitor));
        });
        return workspaceOpt;
    }

    public List<UserTeamsDTO> getUsersWithTeams(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Getting users with teams for workspace id={} (slug={})",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        List<User> users = workspaceMembershipRepository.findHumanUsersWithTeamsByWorkspaceId(workspace.getId());
        return users.stream().map(UserTeamsDTO::fromUser).toList();
    }

    public Optional<TeamInfoDTO> addLabelToTeam(String slug, Long teamId, Long repositoryId, String label) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Adding label '{}' of repository with ID: {} to team with ID: {} (workspace id={})",
            LoggingUtils.sanitizeForLog(label),
            repositoryId,
            teamId,
            workspace.getId()
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

    public Optional<TeamInfoDTO> removeLabelFromTeam(String slug, Long teamId, Long labelId) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Removing label with ID: {} from team with ID: {} (workspace id={})",
            labelId,
            teamId,
            workspace.getId()
        );
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
        logger.info("Deleting team with ID: {}", teamId);
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
    public void resetAndRecalculateLeagues(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Resetting and recalculating league points for all users (requested by workspace id={})",
            workspace.getId()
        );
        resetAndRecalculateLeaguesInternal();
    }

    private void resetAndRecalculateLeaguesInternal() {
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
        // First check if an installation-backed workspace already exists for this installation ID
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElse(null);

        if (workspace == null && !isBlank(accountLogin)) {
            // Check if there's an existing workspace for this account
            Workspace existingByLogin = workspaceRepository.findByAccountLoginIgnoreCase(accountLogin).orElse(null);

            if (existingByLogin != null) {
                // IMPORTANT: Do NOT convert PAT workspaces to GitHub App workspaces!
                // PAT workspaces are explicitly configured by the admin and may have different
                // repository access than the GitHub App installation. Converting them would:
                // 1. Clear the PAT, breaking authentication
                // 2. Change which repos are accessible (PAT may access private repos the App cannot)
                if (existingByLogin.getGitProviderMode() == Workspace.GitProviderMode.PAT_ORG) {
                    logger.info(
                        "Workspace id={} for {} is a PAT workspace; skipping GitHub App installation {} linking. " +
                        "If you want to use the GitHub App instead, delete the PAT workspace first or set " +
                        "hephaestus.workspace.init-default=false.",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                    // Return the existing PAT workspace without modification
                    return existingByLogin;
                }

                // It's already a GitHub App workspace or has no mode set - safe to link
                workspace = existingByLogin;
                logger.info(
                    "Linking existing workspace id={} login={} to installation {}.",
                    workspace.getId(),
                    LoggingUtils.sanitizeForLog(accountLogin),
                    installationId
                );
            }
        }

        if (workspace == null) {
            if (isBlank(accountLogin)) {
                throw new IllegalArgumentException(
                    "Cannot create workspace from installation " + installationId + " without accountLogin."
                );
            }

            Long ownerUserId = syncGitHubUserForOwnership(installationId, accountLogin);

            if (ownerUserId == null) {
                // Cannot sync the owner user - likely an old/deleted installation
                // Log and return null to skip workspace creation
                logger.warn(
                    "Skipping workspace creation for installation {}: cannot sync owner user '{}' and user does not exist locally.",
                    installationId,
                    LoggingUtils.sanitizeForLog(accountLogin)
                );
                return null;
            }

            workspace = createWorkspace(accountLogin, accountLogin, accountLogin, AccountType.ORG, ownerUserId);
            logger.info(
                "Created new workspace '{}' for installation {} with owner userId={}.",
                LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                installationId,
                ownerUserId
            );
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

    /**
     * Stop NATS consumer for a workspace tied to an installation.
     * Used when an installation is deleted to clean up consumers before removing monitors.
     */
    public void stopNatsConsumerForInstallation(long installationId) {
        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresent(workspace -> {
                if (shouldUseNats(workspace)) {
                    natsConsumerService.stopConsumingWorkspace(workspace);
                }
            });
    }

    /**
     * Update workspace status for a given installation if the status differs.
     */
    @Transactional
    public Optional<Workspace> updateStatusForInstallation(long installationId, Workspace.WorkspaceStatus status) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || status == null) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        if (status != workspace.getStatus()) {
            workspace.setStatus(status);
            workspace = workspaceRepository.save(workspace);
        }

        return Optional.of(workspace);
    }

    /**
     * Update repository selection for a given installation if provided and different.
     */
    @Transactional
    public Optional<Workspace> updateRepositorySelection(long installationId, GHRepositorySelection selection) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || selection == null) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        if (workspace.getGithubRepositorySelection() != selection) {
            workspace.setGithubRepositorySelection(selection);
            workspace = workspaceRepository.save(workspace);
        }

        return Optional.of(workspace);
    }

    @Transactional
    public void handleInstallationTargetRename(long installationId, String previousLogin, String newLogin) {
        if (isBlank(newLogin)) {
            logger.warn("Ignoring installation_target event for {} without target login", installationId);
            return;
        }

        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresentOrElse(
                workspace -> {
                    String oldLogin = !isBlank(previousLogin) ? previousLogin : workspace.getAccountLogin();
                    if (!newLogin.equals(workspace.getAccountLogin())) {
                        workspace.setAccountLogin(newLogin);
                        workspaceRepository.save(workspace);
                    }
                    retargetRepositoryMonitors(workspace, oldLogin, newLogin);
                    renameTrackedRepositories(oldLogin, newLogin);
                    rotateOrganizationConsumer(workspace, oldLogin, newLogin);
                },
                () -> logger.warn("installation_target event for unknown installation {}", installationId)
            );
    }

    private void retargetRepositoryMonitors(Workspace workspace, String oldLogin, String newLogin) {
        if (workspace == null || isBlank(oldLogin) || isBlank(newLogin) || oldLogin.equalsIgnoreCase(newLogin)) {
            return;
        }

        String prefixLower = (oldLogin + "/").toLowerCase(Locale.ENGLISH);
        repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .forEach(monitor -> {
                String current = monitor.getNameWithOwner();
                if (current == null) {
                    return;
                }
                String normalized = current.toLowerCase(Locale.ENGLISH);
                if (!normalized.startsWith(prefixLower)) {
                    return;
                }
                int slashIndex = current.indexOf('/');
                if (slashIndex < 0) {
                    return;
                }
                String suffix = current.substring(slashIndex);
                monitor.setNameWithOwner(newLogin + suffix);
                repositoryToMonitorRepository.save(monitor);
            });

        // Update the workspace consumer with new subjects after all renames
        if (shouldUseNats(workspace)) {
            natsConsumerService.updateWorkspaceConsumer(workspace);
        }
    }

    private void renameTrackedRepositories(String oldLogin, String newLogin) {
        if (isBlank(oldLogin) || isBlank(newLogin) || oldLogin.equalsIgnoreCase(newLogin)) {
            return;
        }

        String prefix = oldLogin + "/";
        var repositories = repositoryRepository.findByNameWithOwnerStartingWithIgnoreCase(prefix);
        if (repositories.isEmpty()) {
            return;
        }

        repositories.forEach(repository -> {
            String current = repository.getNameWithOwner();
            if (current == null) {
                return;
            }
            int slashIndex = current.indexOf('/');
            if (slashIndex < 0) {
                return;
            }
            String suffix = current.substring(slashIndex);
            repository.setNameWithOwner(newLogin + suffix);
            repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        });
        repositoryRepository.saveAll(repositories);
    }

    private Optional<GHRepository> fetchRepositoryOrThrow(Long workspaceId, String nameWithOwner) {
        try {
            return repositorySyncService.syncRepository(workspaceId, nameWithOwner);
        } catch (RepositorySyncException syncException) {
            if (syncException.getReason() == RepositorySyncException.Reason.NOT_FOUND) {
                throw new EntityNotFoundException("Repository", nameWithOwner);
            }
            if (syncException.getReason() == RepositorySyncException.Reason.FORBIDDEN) {
                throw new RepositoryAccessForbiddenException(nameWithOwner);
            }
            throw syncException;
        }
    }

    private void rotateOrganizationConsumer(Workspace workspace, String oldLogin, String newLogin) {
        if (
            !isNatsEnabled ||
            workspace == null ||
            isBlank(oldLogin) ||
            isBlank(newLogin) ||
            oldLogin.equalsIgnoreCase(newLogin)
        ) {
            return;
        }

        // Update the workspace consumer - it will pick up the new org login from workspace
        natsConsumerService.updateWorkspaceConsumer(workspace);
    }

    private boolean shouldUseNats(Workspace workspace) {
        return isNatsEnabled && workspace != null;
    }

    private void persistRepositoryMonitor(Workspace workspace, RepositoryToMonitor monitor) {
        persistRepositoryMonitor(workspace, monitor, false);
    }

    /**
     * Persist a repository monitor and optionally trigger immediate sync.
     *
     * @param workspace the workspace to add the monitor to
     * @param monitor the repository monitor to persist
     * @param deferSync if true, skip immediate sync (useful during provisioning when
     *                  activation will sync all repositories in bulk)
     */
    private void persistRepositoryMonitor(Workspace workspace, RepositoryToMonitor monitor, boolean deferSync) {
        repositoryToMonitorRepository.save(monitor);
        workspace.getRepositoriesToMonitor().add(monitor);
        workspaceRepository.save(workspace);
        boolean repositoryAllowed = monitoringScopeFilter.isRepositoryAllowed(monitor);
        if (shouldUseNats(workspace) && repositoryAllowed) {
            // Update workspace consumer to include new repository subjects
            natsConsumerService.updateWorkspaceConsumer(workspace);
        }
        if (deferSync) {
            logger.debug("Repository {} persisted with deferred sync.", monitor.getNameWithOwner());
            return;
        }
        if (repositoryAllowed) {
            gitHubDataSyncService.syncRepositoryToMonitorAsync(monitor);
        } else {
            logger.debug("Repository {} persisted but monitoring disabled by filters.", monitor.getNameWithOwner());
        }
    }

    private void deleteRepositoryMonitor(Workspace workspace, RepositoryToMonitor monitor) {
        repositoryToMonitorRepository.delete(monitor);
        workspace.getRepositoriesToMonitor().remove(monitor);
        workspaceRepository.save(workspace);
        if (shouldUseNats(workspace)) {
            // Update workspace consumer to remove repository subjects
            natsConsumerService.updateWorkspaceConsumer(workspace);
        }
    }

    private Optional<Workspace> ensureRepositoryMonitorInternal(
        Workspace workspace,
        String nameWithOwner,
        boolean deferSync
    ) {
        if (workspace == null || isBlank(nameWithOwner)) {
            return Optional.ofNullable(workspace);
        }

        if (repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(workspace.getId(), nameWithOwner)) {
            return Optional.of(workspace);
        }

        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(nameWithOwner);
        monitor.setWorkspace(workspace);
        persistRepositoryMonitor(workspace, monitor, deferSync);
        return Optional.of(workspace);
    }

    private Optional<Workspace> removeRepositoryMonitorInternal(Workspace workspace, RepositoryToMonitor monitor) {
        if (workspace == null || monitor == null) {
            return Optional.ofNullable(workspace);
        }

        deleteRepositoryMonitor(workspace, monitor);
        return Optional.of(workspace);
    }

    /**
     * Enumerate all repositories available to the installation when repository selection is ALL and ensure monitors exist.
     */
    @Transactional
    public void ensureAllInstallationRepositoriesCovered(long installationId) {
        ensureAllInstallationRepositoriesCovered(installationId, Collections.emptySet(), false);
    }

    /**
     * Enumerate all repositories available to the installation when repository selection is ALL and ensure monitors exist.
     *
     * @param installationId the GitHub App installation ID
     * @param deferSync if true, skip immediate sync (use during provisioning when activation will sync in bulk)
     */
    @Transactional
    public void ensureAllInstallationRepositoriesCovered(long installationId, boolean deferSync) {
        ensureAllInstallationRepositoriesCovered(installationId, Collections.emptySet(), deferSync);
    }

    @Transactional
    public void ensureAllInstallationRepositoriesCovered(
        long installationId,
        Collection<String> protectedRepositories
    ) {
        ensureAllInstallationRepositoriesCovered(installationId, protectedRepositories, false);
    }

    @Transactional
    public void ensureAllInstallationRepositoriesCovered(
        long installationId,
        Collection<String> protectedRepositories,
        boolean deferSync
    ) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty()) {
            return;
        }

        Workspace workspace = workspaceOpt.get();
        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.GITHUB_APP_INSTALLATION) {
            return;
        }

        var snapshots = installationRepositoryEnumerator.enumerate(installationId);
        if (snapshots.isEmpty()) {
            logger.warn(
                "Installation {} (workspace={}) configured for ALL repositories but enumeration returned no data; monitors might be stale.",
                installationId,
                workspace.getWorkspaceSlug()
            );
            return;
        }

        Set<String> desiredRepositories = snapshots
            .stream()
            .map(snapshot -> snapshot.nameWithOwner())
            .filter(name -> !isBlank(name))
            .map(name -> name.toLowerCase(Locale.ENGLISH))
            .collect(Collectors.toSet());

        if (protectedRepositories != null) {
            protectedRepositories
                .stream()
                .filter(name -> !isBlank(name))
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .forEach(desiredRepositories::add);
        }

        snapshots.forEach(snapshot -> {
            repositorySyncService.upsertFromInstallationPayload(
                snapshot.id(),
                snapshot.nameWithOwner(),
                snapshot.name(),
                snapshot.isPrivate()
            );
            ensureRepositoryMonitorForInstallation(installationId, snapshot.nameWithOwner(), deferSync);
        });

        repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .filter(monitor -> !isBlank(monitor.getNameWithOwner()))
            .filter(monitor -> !desiredRepositories.contains(monitor.getNameWithOwner().toLowerCase(Locale.ENGLISH)))
            .forEach(monitor -> removeRepositoryMonitorForInstallation(installationId, monitor.getNameWithOwner()));
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
            logger.info(
                "Falling back to the only configured workspace id={} for {}.",
                all.getFirst().getId(),
                LoggingUtils.sanitizeForLog(context)
            );
            return Optional.of(all.getFirst());
        }
        logger.warn(
            "Unable to resolve workspace for {}. Available workspace count={}",
            LoggingUtils.sanitizeForLog(context),
            all.size()
        );
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
    public Workspace createWorkspace(
        String rawSlug,
        String displayName,
        String accountLogin,
        AccountType accountType,
        Long ownerUserId
    ) {
        String slug = normalizeSlug(rawSlug);
        validateSlug(slug);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName(displayName);
        workspace.setIsPubliclyViewable(DEFAULT_PUBLIC_VISIBILITY);
        workspace.setAccountLogin(accountLogin);
        workspace.setAccountType(accountType);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);

        try {
            Workspace saved = workspaceRepository.save(workspace);
            createOwnerRole(saved, ownerUserId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation on slug
            throw new WorkspaceSlugConflictException(slug);
        }
    }

    public Optional<Workspace> getWorkspaceBySlug(String slug) {
        return workspaceRepository.findByWorkspaceSlug(slug);
    }

    private Workspace requireWorkspace(String slug) {
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace slug must not be blank.");
        }
        return workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
    }

    @Transactional
    public Workspace updateSchedule(String slug, Integer day, String time) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        if (day != null) {
            if (day < 1 || day > 7) {
                throw new IllegalArgumentException("Day must be between 1 (Monday) and 7 (Sunday), got: " + day);
            }
            workspace.setLeaderboardScheduleDay(day);
        }

        if (time != null) {
            try {
                LocalTime parsed = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
                workspace.setLeaderboardScheduleTime(parsed.toString());
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Time must be in HH:mm format (00:00 to 23:59), got: " + time);
            }
        }

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateNotifications(String slug, Boolean enabled, String team, String channelId) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        if (enabled != null) {
            workspace.setLeaderboardNotificationEnabled(enabled);
        }

        if (team != null) {
            workspace.setLeaderboardNotificationTeam(team);
        }

        if (channelId != null) {
            String trimmedChannelId = channelId.trim();
            if (!SLACK_CHANNEL_ID_PATTERN.matcher(trimmedChannelId).matches()) {
                throw new IllegalArgumentException(
                    "Slack channel ID must start with 'C' (public), 'G' (private), or 'D' (DM) followed by at least 8 alphanumerics, got: " +
                    trimmedChannelId
                );
            }
            workspace.setLeaderboardNotificationChannelId(trimmedChannelId);
        }

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateToken(String slug, String personalAccessToken) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        // TODO: Validate token with GitHub API before storing
        // TODO: Consider encrypting the token at rest
        // TODO: Add audit log entry for security tracking
        workspace.setPersonalAccessToken(personalAccessToken);

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateSlackCredentials(String slug, String slackToken, String slackSigningSecret) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        // TODO: Validate Slack token by calling Slack API (auth.test)
        workspace.setSlackToken(slackToken);
        workspace.setSlackSigningSecret(slackSigningSecret);

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updatePublicVisibility(String slug, Boolean isPubliclyViewable) {
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        workspace.setIsPubliclyViewable(isPubliclyViewable);

        return workspaceRepository.save(workspace);
    }

    private void createOwnerRole(Workspace workspace, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Owner user id must not be null when creating a workspace.");
        }
        workspaceMembershipService.createMembership(workspace, ownerUserId, WorkspaceMembership.WorkspaceRole.OWNER);
    }

    private String normalizeSlug(String slug) {
        if (slug == null) {
            return null;
        }
        String normalized = slug.trim().toLowerCase();
        normalized = normalized
            .replace('_', '-')
            .replaceAll("\\s+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-|-$", "");
        return normalized;
    }

    private void validateSlug(String slug) {
        if (slug == null) {
            throw new InvalidWorkspaceSlugException("null");
        }
        if (!slug.matches("^[a-z0-9][a-z0-9-]{2,50}$")) {
            throw new InvalidWorkspaceSlugException(slug);
        }
    }

    /**
     * Syncs a GitHub user from an installation and returns their user ID for ownership assignment.
     * Falls back to checking existing users if GitHub sync fails.
     * Returns null if the user cannot be synced and doesn't exist locally.
     */
    private Long syncGitHubUserForOwnership(long installationId, String accountLogin) {
        try {
            org.kohsuke.github.GitHub github = gitHubAppTokenService.clientForInstallation(installationId);

            User user = gitHubUserSyncService.syncUser(github, accountLogin);

            if (user != null && user.getId() != null) {
                logger.info(
                    "Synced GitHub user '{}' (id={}) as workspace owner.",
                    LoggingUtils.sanitizeForLog(accountLogin),
                    user.getId()
                );
                return user.getId();
            }
        } catch (Exception e) {
            logger.warn(
                "Failed to sync GitHub user '{}' for installation {}: {}",
                LoggingUtils.sanitizeForLog(accountLogin),
                installationId,
                LoggingUtils.sanitizeForLog(e.getMessage())
            );
        }

        return userRepository.findByLogin(accountLogin).map(User::getId).orElse(null);
    }
}
