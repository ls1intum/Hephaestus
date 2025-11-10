package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
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
import de.tum.in.www1.hephaestus.workspace.exception.*;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.kohsuke.github.GHRepositorySelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private static final boolean DEFAULT_PUBLIC_VISIBILITY = false;

    private static final Pattern SLACK_CHANNEL_ID_PATTERN = Pattern.compile("^[CGD][A-Z0-9]{8,}$");

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

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

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
                logger.error("Error during syncUsers: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
                return null;
            });

        CompletableFuture<Void> teamsFuture = usersFuture
            .thenRunAsync(() -> {
                logger.info("Users synced, now syncing teams for workspace id={}", workspace.getId());
                gitHubDataSyncService.syncTeams(workspace);
            })
            .exceptionally(ex -> {
                logger.error("Error during syncTeams: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
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
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        logger.info("Adding repository to monitor: {}", LoggingUtils.sanitizeForLog(nameWithOwner));
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
            throw new EntityNotFoundException("Repository", nameWithOwner);
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

    public void removeRepositoryToMonitor(String nameWithOwner) throws EntityNotFoundException {
        logger.info("Removing repository from monitor: {}", LoggingUtils.sanitizeForLog(nameWithOwner));
        Workspace workspace = getWorkspaceByRepositoryOwner(nameWithOwner);

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
            "Adding label '{}' of repository with ID: {} to team with ID: {}",
            LoggingUtils.sanitizeForLog(label),
            repositoryId,
            teamId
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
        logger.info("Removing label with ID: {} from team with ID: {}", labelId, teamId);
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

            workspace = createWorkspace(accountLogin, accountLogin, accountLogin, AccountType.ORG, ownerUserId);
            logger.info(
                "Created new workspace '{}' for installation {} with owner userId={}.",
                LoggingUtils.sanitizeForLog(workspace.getSlug()),
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

        if (workspaceRepository.existsBySlug(slug)) {
            throw new WorkspaceSlugConflictException(slug);
        }

        Workspace workspace = new Workspace();
        workspace.setSlug(slug);
        workspace.setDisplayName(displayName);
        workspace.setIsPubliclyViewable(DEFAULT_PUBLIC_VISIBILITY);
        workspace.setAccountLogin(accountLogin);
        workspace.setAccountType(accountType);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        Workspace saved = workspaceRepository.save(workspace);
        createOwnerRole(saved, ownerUserId);

        return saved;
    }

    public Optional<Workspace> getWorkspaceBySlug(String slug) {
        return workspaceRepository.findBySlug(slug);
    }

    @Transactional
    public Workspace updateSchedule(String slug, Integer day, String time) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
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
            .findBySlug(slug)
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
                    channelId
                );
            }
            workspace.setLeaderboardNotificationChannelId(trimmedChannelId);
        }

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updateToken(String slug, String personalAccessToken) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
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
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));

        // TODO: Validate Slack token by calling Slack API (auth.test)
        workspace.setSlackToken(slackToken);
        workspace.setSlackSigningSecret(slackSigningSecret);

        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace updatePublicVisibility(String slug, Boolean isPubliclyViewable) {
        Workspace workspace = workspaceRepository
            .findBySlug(slug)
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

        return userRepository
            .findByLogin(accountLogin)
            .map(User::getId)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Cannot assign owner for workspace: GitHub user '" +
                    accountLogin +
                    "' could not be synced and does not exist locally. " +
                    "Ensure the user exists in the system before creating the workspace."
                )
            );
    }
}
