package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.github.GitHubIssueTypeSyncService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.subissue.github.GitHubSubIssueSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import de.tum.in.www1.hephaestus.workspace.exception.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central service for workspace management operations.
 * Handles workspace creation, configuration, activation, and synchronization
 * coordination.
 */
@Service
public class WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

    private static final boolean DEFAULT_PUBLIC_VISIBILITY = false;

    // Configuration
    private final boolean isNatsEnabled;
    private final boolean runMonitoringOnStartup;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final RepositoryRepository repositoryRepository;
    private final LabelRepository labelRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;

    // Services
    private final WorkspaceSlugService workspaceSlugService;
    private final WorkspaceSettingsService workspaceSettingsService;
    private final NatsConsumerService natsConsumerService;
    private final GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final TeamInfoDTOConverter teamInfoDTOConverter;
    private final LeaguePointsRecalculator leaguePointsRecalculator;
    private final OrganizationService organizationService;
    private final WorkspaceMembershipService workspaceMembershipService;

    // Lazy-loaded dependencies (to break circular references)
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;
    private final ObjectProvider<GitHubIssueTypeSyncService> issueTypeSyncServiceProvider;
    private final ObjectProvider<GitHubSubIssueSyncService> subIssueSyncServiceProvider;

    // Infrastructure
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        @Value("${monitoring.run-on-startup}") boolean runMonitoringOnStartup,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        UserRepository userRepository,
        TeamRepository teamRepository,
        RepositoryRepository repositoryRepository,
        LabelRepository labelRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceSlugService workspaceSlugService,
        WorkspaceSettingsService workspaceSettingsService,
        NatsConsumerService natsConsumerService,
        GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator,
        WorkspaceScopeFilter workspaceScopeFilter,
        TeamInfoDTOConverter teamInfoDTOConverter,
        LeaguePointsRecalculator leaguePointsRecalculator,
        OrganizationService organizationService,
        WorkspaceMembershipService workspaceMembershipService,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider,
        ObjectProvider<GitHubIssueTypeSyncService> issueTypeSyncServiceProvider,
        ObjectProvider<GitHubSubIssueSyncService> subIssueSyncServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.runMonitoringOnStartup = runMonitoringOnStartup;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelRepository = labelRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceSlugService = workspaceSlugService;
        this.workspaceSettingsService = workspaceSettingsService;
        this.natsConsumerService = natsConsumerService;
        this.installationRepositoryEnumerator = installationRepositoryEnumerator;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.teamInfoDTOConverter = teamInfoDTOConverter;
        this.leaguePointsRecalculator = leaguePointsRecalculator;
        this.organizationService = organizationService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.issueTypeSyncServiceProvider = issueTypeSyncServiceProvider;
        this.subIssueSyncServiceProvider = subIssueSyncServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /** Lazy accessor for GitHubDataSyncService to break circular dependency. */
    private GitHubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    /** Lazy accessor for GitHubIssueTypeSyncService. */
    private GitHubIssueTypeSyncService getIssueTypeSyncService() {
        return issueTypeSyncServiceProvider.getObject();
    }

    /** Lazy accessor for GitHubSubIssueSyncService. */
    private GitHubSubIssueSyncService getSubIssueSyncService() {
        return subIssueSyncServiceProvider.getObject();
    }

    /**
     * Converts a RepositoryToMonitor to a SyncTarget for the sync service.
     */
    private SyncTarget toSyncTarget(Workspace workspace, RepositoryToMonitor rtm) {
        SyncTargetProvider.AuthMode authMode = workspace.getGitProviderMode() ==
            Workspace.GitProviderMode.GITHUB_APP_INSTALLATION
            ? SyncTargetProvider.AuthMode.GITHUB_APP
            : SyncTargetProvider.AuthMode.PAT;

        return new SyncTarget(
            rtm.getId(),
            workspace.getId(),
            workspace.getInstallationId(),
            workspace.getPersonalAccessToken(),
            authMode,
            rtm.getNameWithOwner(),
            rtm.getLabelsSyncedAt(),
            rtm.getMilestonesSyncedAt(),
            rtm.getIssuesAndPullRequestsSyncedAt(),
            rtm.getCollaboratorsSyncedAt(),
            rtm.getRepositorySyncedAt(),
            rtm.getBackfillHighWaterMark(),
            rtm.getBackfillCheckpoint(),
            rtm.getBackfillLastRunAt()
        );
    }

    /**
     * Prepare every workspace and start monitoring/sync routines for those that are
     * ready.
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
        // Each workspace's monitoring runs independently and can sync repos
        // concurrently.
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
        if (!workspaceScopeFilter.isWorkspaceAllowed(workspace)) {
            logger.info("Workspace id={} skipped: workspace scope filters active.", workspace.getId());
            return;
        }

        // Load fresh RepositoryToMonitor entities from database to ensure sync
        // timestamps
        // are up-to-date. This is critical for respecting cooldown periods across
        // restarts.
        List<RepositoryToMonitor> repositoriesToMonitor = repositoryToMonitorRepository.findByWorkspaceId(
            workspace.getId()
        );
        var eligibleRepositories = repositoriesToMonitor
            .stream()
            .filter(workspaceScopeFilter::isRepositoryAllowed)
            .toList();

        if (runMonitoringOnStartup) {
            logger.info("Running monitoring on startup for workspace id={}", workspace.getId());

            // Set workspace context for the sync operations (enables proper logging via
            // MDC)
            WorkspaceContext workspaceContext = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(workspaceContext);
            try {
                // Sync repositories SEQUENTIALLY within each workspace.
                // This avoids race conditions for shared entities (Organization, Users)
                // and respects GitHub API rate limits per installation/PAT.
                // Workspaces themselves run in parallel using virtual threads.
                for (var repo : eligibleRepositories) {
                    try {
                        getGitHubDataSyncService().syncSyncTarget(toSyncTarget(workspace, repo));
                    } catch (Exception ex) {
                        logger.error(
                            "Error syncing repository {}: {}",
                            repo.getNameWithOwner(),
                            LoggingUtils.sanitizeForLog(ex.getMessage()),
                            ex
                        );
                    }
                }

                // TODO: User and team sync via GraphQL not yet implemented
                // Users and teams sync sequentially after all repos
                logger.info(
                    "All repositories synced for workspace id={} (user/team sync pending GraphQL migration)",
                    workspace.getId()
                );

                // Sync issue types via GraphQL (organization-level data)
                try {
                    logger.info("Teams synced, now syncing issue types for workspace id={}", workspace.getId());
                    getIssueTypeSyncService().syncIssueTypesForWorkspace(workspace.getId());
                } catch (Exception ex) {
                    logger.error("Error during syncIssueTypes: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
                }

                // Sync sub-issue relationships via GraphQL
                try {
                    logger.info("Issue types synced, now syncing sub-issues for workspace id={}", workspace.getId());
                    getSubIssueSyncService().syncSubIssuesForWorkspace(workspace.getId());
                } catch (Exception ex) {
                    logger.error("Error during syncSubIssues: {}", LoggingUtils.sanitizeForLog(ex.getMessage()), ex);
                }

                logger.info("Finished running monitoring on startup for workspace id={}", workspace.getId());
            } finally {
                // Clear context after sync operations complete
                WorkspaceContextHolder.clearContext();
            }
        }

        // Start NATS consumer AFTER startup sync completes to avoid race conditions.
        // The startup sync ensures all entities exist before NATS starts processing
        // webhook events that might reference them.
        if (shouldUseNats(workspace)) {
            natsConsumerService.startConsumingWorkspace(workspace.getId());
        }
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspaceByRepositoryOwner(String nameWithOwner) {
        return workspaceRepository
            .findByRepositoriesToMonitor_NameWithOwner(nameWithOwner)
            .or(() -> resolveFallbackWorkspace("repository " + nameWithOwner))
            .orElseThrow(() -> new IllegalStateException("No workspace found for repository: " + nameWithOwner));
    }

    /**
     * List all non-purged workspaces.
     */
    @Transactional(readOnly = true)
    public List<Workspace> listAllWorkspaces() {
        return workspaceRepository.findByStatusNot(Workspace.WorkspaceStatus.PURGED);
    }

    /**
     * Returns workspaces the current user can see: memberships + publicly viewable
     * workspaces.
     * If no user is authenticated, only publicly viewable workspaces are returned.
     */
    public List<Workspace> listAccessibleWorkspacesForCurrentUser() {
        return listAccessibleWorkspaces(userRepository.getCurrentUser());
    }

    List<Workspace> listAccessibleWorkspaces(Optional<User> currentUser) {
        // Always include public, non-purged workspaces
        List<Workspace> publicWorkspaces = workspaceRepository.findByStatusNotAndIsPubliclyViewableTrue(
            Workspace.WorkspaceStatus.PURGED
        );

        if (currentUser.isEmpty()) {
            return publicWorkspaces;
        }

        // Fetch memberships for the current user and load workspaces by ID
        var memberships = workspaceMembershipRepository.findByUser_Id(currentUser.get().getId());
        var workspaceIds = memberships.stream().map(WorkspaceMembership::getWorkspace).map(Workspace::getId).toList();

        List<Workspace> memberWorkspaces = workspaceIds.isEmpty()
            ? List.of()
            : workspaceRepository.findAllById(workspaceIds);

        // Merge and de-duplicate by ID to avoid duplicate entities with different
        // instances
        return Stream.concat(publicWorkspaces.stream(), memberWorkspaces.stream())
            .collect(
                Collectors.toMap(Workspace::getId, w -> w, (existing, replacement) -> existing, LinkedHashMap::new)
            )
            .values()
            .stream()
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Workspace> findByInstallationId(Long installationId) {
        return workspaceRepository.findByInstallationId(installationId);
    }

    @Transactional(readOnly = true)
    public List<String> getRepositoriesToMonitor(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Getting repositories to monitor for workspace id={} (slug={})",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        return workspace.getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList();
    }

    public List<String> getRepositoriesToMonitor(WorkspaceContext workspaceContext) {
        return getRepositoriesToMonitor(requireSlug(workspaceContext));
    }

    public void addRepositoryToMonitor(String slug, String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);

        // Block repository management for GitHub App Installation workspaces
        if (Workspace.GitProviderMode.GITHUB_APP_INSTALLATION.equals(workspace.getGitProviderMode())) {
            throw new RepositoryManagementNotAllowedException(slug);
        }

        logger.info(
            "Adding repository to monitor: {} for workspace id={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

        // Removed unused workspaceContext-based block
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

    public void addRepositoryToMonitor(WorkspaceContext workspaceContext, String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        addRepositoryToMonitor(requireSlug(workspaceContext), nameWithOwner);
    }

    public void removeRepositoryToMonitor(String slug, String nameWithOwner) throws EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);

        // Block repository management for GitHub App Installation workspaces
        if (Workspace.GitProviderMode.GITHUB_APP_INSTALLATION.equals(workspace.getGitProviderMode())) {
            throw new RepositoryManagementNotAllowedException(slug);
        }

        logger.info(
            "Removing repository from monitor: {} for workspace id={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

        // Removed unused workspaceContext-based block
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
     * Idempotently ensure a repository monitor exists for a given installation id
     * without issuing extra GitHub fetches.
     */
    @Transactional
    public Optional<Workspace> ensureRepositoryMonitorForInstallation(long installationId, String nameWithOwner) {
        return ensureRepositoryMonitorForInstallation(installationId, nameWithOwner, false);
    }

    /**
     * Idempotently ensure a repository monitor exists for a given installation id.
     *
     * @param installationId the GitHub App installation ID
     * @param nameWithOwner  the repository full name (e.g., "owner/repo")
     * @param deferSync      if true, skip immediate sync (use during provisioning
     *                       when activation will sync in bulk)
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
     * Remove a repository monitor for a given installation id if it exists. No-op
     * if missing.
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

    public void removeRepositoryToMonitor(WorkspaceContext workspaceContext, String nameWithOwner)
        throws EntityNotFoundException {
        removeRepositoryToMonitor(requireSlug(workspaceContext), nameWithOwner);
    }

    /**
     * Resolve the workspace slug responsible for a given repository.
     * Priority:
     * 1) Explicit repository monitor (authoritative)
     * 2) Workspace account login matching repository owner (one-to-one enforced by
     * business model)
     * Returns empty if no unique mapping can be established.
     */
    public Optional<String> resolveWorkspaceSlugForRepository(Repository repository) {
        if (repository == null || isBlank(repository.getNameWithOwner())) {
            return Optional.empty();
        }

        var nameWithOwner = repository.getNameWithOwner();
        var monitor = repositoryToMonitorRepository.findByNameWithOwner(nameWithOwner);
        if (monitor.isPresent()) {
            Workspace workspace = monitor.get().getWorkspace();
            return workspace != null ? Optional.ofNullable(workspace.getWorkspaceSlug()) : Optional.empty();
        }

        // Fallback: org owner lookup (accountLogin is unique)
        String owner = nameWithOwner.contains("/") ? nameWithOwner.substring(0, nameWithOwner.indexOf("/")) : null;
        if (owner != null) {
            return workspaceRepository.findByAccountLoginIgnoreCase(owner).map(Workspace::getWorkspaceSlug);
        }

        return Optional.empty();
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

    public List<UserTeamsDTO> getUsersWithTeams(WorkspaceContext workspaceContext) {
        return getUsersWithTeams(requireSlug(workspaceContext));
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
        // Fetch with collections to avoid LazyInitializationException when calling
        // addLabel()
        Optional<Team> optionalTeam = teamRepository.findWithCollectionsById(teamId);
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
        return Optional.of(teamInfoDTOConverter.convert(team));
    }

    public Optional<TeamInfoDTO> addLabelToTeam(
        WorkspaceContext workspaceContext,
        Long teamId,
        Long repositoryId,
        String label
    ) {
        return addLabelToTeam(requireSlug(workspaceContext), teamId, repositoryId, label);
    }

    public Optional<TeamInfoDTO> removeLabelFromTeam(String slug, Long teamId, Long labelId) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Removing label with ID: {} from team with ID: {} (workspace id={})",
            labelId,
            teamId,
            workspace.getId()
        );
        // Fetch with collections to avoid LazyInitializationException when calling
        // removeLabel()
        Optional<Team> optionalTeam = teamRepository.findWithCollectionsById(teamId);
        if (optionalTeam.isEmpty()) {
            logger.warn("Team not found with ID: {}", teamId);
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        Optional<Label> labelEntity = labelRepository.findById(labelId);
        if (labelEntity.isEmpty()) {
            logger.warn("Label not found with ID: {}", labelId);
            return Optional.empty();
        }
        Label label = labelEntity.get();
        int labelCountBefore = team.getLabels().size();
        logger.info(
            "Team {} has {} labels before removal. Looking for label id={}, name={}",
            team.getName(),
            labelCountBefore,
            label.getId(),
            label.getName()
        );
        boolean removed = team.getLabels().remove(label);
        int labelCountAfter = team.getLabels().size();
        logger.info(
            "Label removal result: removed={}, labelCountBefore={}, labelCountAfter={}",
            removed,
            labelCountBefore,
            labelCountAfter
        );
        teamRepository.save(team);
        return Optional.of(teamInfoDTOConverter.convert(team));
    }

    public Optional<TeamInfoDTO> removeLabelFromTeam(WorkspaceContext workspaceContext, Long teamId, Long labelId) {
        return removeLabelFromTeam(requireSlug(workspaceContext), teamId, labelId);
    }

    /**
     * Reset and recalculate league points for all users by replaying their
     * contributions
     * from the first recorded activity until now.
     */
    @Transactional
    public void resetAndRecalculateLeagues(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Resetting and recalculating league points for workspace id={}, slug={}",
            workspace.getId(),
            workspace.getWorkspaceSlug()
        );
        resetAndRecalculateLeaguesInternal(workspace.getId());
    }

    public void resetAndRecalculateLeagues(WorkspaceContext workspaceContext) {
        Workspace workspace = requireWorkspace(requireSlug(workspaceContext));
        resetAndRecalculateLeaguesInternal(workspace.getId());
    }

    private void resetAndRecalculateLeaguesInternal(Long workspaceId) {
        logger.info("Resetting and recalculating league points for workspace id={}", workspaceId);

        if (workspaceId == null) {
            logger.warn("Skipping league recalculation because no workspace is configured.");
            return;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace {} no longer exists; skipping recalculation", workspaceId);
            return;
        }

        leaguePointsRecalculator.recalculate(workspace);
    }

    @Transactional
    public Workspace ensureForInstallation(
        long installationId,
        String accountLogin,
        RepositorySelection repositorySelection
    ) {
        // First check if an installation-backed workspace already exists for this
        // installation ID
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElse(null);

        if (workspace == null && !isBlank(accountLogin)) {
            // Check if there's an existing workspace for this account
            Workspace existingByLogin = workspaceRepository.findByAccountLoginIgnoreCase(accountLogin).orElse(null);

            if (existingByLogin != null) {
                boolean isPatWorkspace = existingByLogin.getGitProviderMode() == Workspace.GitProviderMode.PAT_ORG;
                boolean hasPatToken = !isBlank(existingByLogin.getPersonalAccessToken());

                if (isPatWorkspace && hasPatToken) {
                    logger.info(
                        "Workspace id={} for {} is a PAT workspace with a stored token; skipping GitHub App installation {} linking. " +
                        "If you want to use the GitHub App instead, delete the PAT workspace first or set " +
                        "hephaestus.workspace.init-default=false.",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                    return existingByLogin;
                }

                if (isPatWorkspace) {
                    logger.info(
                        "Promoting PAT workspace id={} for {} to GitHub App installation {} because no PAT token is stored.",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                } else {
                    logger.info(
                        "Linking existing workspace id={} login={} to installation {}.",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                }

                workspace = existingByLogin;
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

            String desiredSlug = workspaceSlugService.normalize(accountLogin);
            String availableSlug = workspaceSlugService.allocate(
                desiredSlug,
                "install-" + installationId + "-" + accountLogin
            );

            // We intentionally do NOT create a redirect from the desired slug to the
            // allocated slug here,
            // because the desired slug may already belong to another workspace. Redirecting
            // would leak or
            // hijack that workspace. Instead, callers must surface the allocated slug to
            // the user.
            workspace = createWorkspace(availableSlug, accountLogin, accountLogin, AccountType.ORG, ownerUserId);
            logger.info(
                "Created new workspace '{}' for installation {} with owner userId={} (requested slug='{}').",
                LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                installationId,
                ownerUserId,
                LoggingUtils.sanitizeForLog(desiredSlug)
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
     * Used when an installation is deleted to clean up consumers before removing
     * monitors.
     */
    public void stopNatsConsumerForInstallation(long installationId) {
        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresent(workspace -> {
                if (shouldUseNats(workspace)) {
                    natsConsumerService.stopConsumingWorkspace(workspace.getId());
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
     * Update repository selection for a given installation if provided and
     * different.
     */
    @Transactional
    public Optional<Workspace> updateRepositorySelection(long installationId, RepositorySelection selection) {
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
            natsConsumerService.updateWorkspaceConsumer(workspace.getId());
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

    /**
     * Checks if a repository exists (placeholder until GraphQL repository sync is available).
     * TODO: Implement proper repository validation via GraphQL API.
     */
    private Optional<Repository> fetchRepositoryOrThrow(Long workspaceId, String nameWithOwner) {
        // For now, check if repository exists in database
        // Full validation will be implemented when GraphQL repository sync is available
        return repositoryRepository.findByNameWithOwner(nameWithOwner);
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

        // Update the workspace consumer - it will pick up the new org login from
        // workspace
        natsConsumerService.updateWorkspaceConsumer(workspace.getId());
    }

    /**
     * Creates or updates a Repository entity from an installation repository snapshot.
     * This ensures the repository exists in the database with basic metadata from the installation payload.
     *
     * @param snapshot the installation repository snapshot
     * @param workspace the workspace this repository belongs to
     */
    private void ensureRepositoryFromSnapshot(
        GitHubInstallationRepositoryEnumerationService.InstallationRepositorySnapshot snapshot,
        Workspace workspace
    ) {
        if (snapshot == null || isBlank(snapshot.nameWithOwner())) {
            return;
        }

        var existingRepo = repositoryRepository.findByNameWithOwner(snapshot.nameWithOwner());
        if (existingRepo.isPresent()) {
            // Repository already exists, update basic fields if needed
            Repository repo = existingRepo.get();
            boolean changed = false;

            if (repo.getName() == null || !repo.getName().equals(snapshot.name())) {
                repo.setName(snapshot.name());
                changed = true;
            }
            if (repo.isPrivate() != snapshot.isPrivate()) {
                repo.setPrivate(snapshot.isPrivate());
                changed = true;
            }

            if (changed) {
                repositoryRepository.save(repo);
            }
        } else {
            // Create new repository with basic metadata from installation payload
            Repository repo = new Repository();
            repo.setId(snapshot.id());
            repo.setNameWithOwner(snapshot.nameWithOwner());
            repo.setName(snapshot.name());
            repo.setPrivate(snapshot.isPrivate());
            repo.setDefaultBranch("main"); // Will be updated by GraphQL sync
            repo.setHtmlUrl("https://github.com/" + snapshot.nameWithOwner());
            repo.setVisibility(snapshot.isPrivate() ? Repository.Visibility.PRIVATE : Repository.Visibility.PUBLIC);
            repo.setPushedAt(Instant.now()); // Placeholder, will be updated by sync

            repositoryRepository.save(repo);
            logger.debug("Created repository {} from installation snapshot", snapshot.nameWithOwner());
        }
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
     * @param monitor   the repository monitor to persist
     * @param deferSync if true, skip immediate sync (useful during provisioning
     *                  when
     *                  activation will sync all repositories in bulk)
     */
    private void persistRepositoryMonitor(Workspace workspace, RepositoryToMonitor monitor, boolean deferSync) {
        repositoryToMonitorRepository.save(monitor);
        workspace.getRepositoriesToMonitor().add(monitor);
        workspaceRepository.save(workspace);
        boolean repositoryAllowed = workspaceScopeFilter.isRepositoryAllowed(monitor);
        if (shouldUseNats(workspace) && repositoryAllowed) {
            // Update workspace consumer to include new repository subjects
            natsConsumerService.updateWorkspaceConsumer(workspace.getId());
        }
        if (deferSync) {
            logger.debug("Repository {} persisted with deferred sync.", monitor.getNameWithOwner());
            return;
        }
        if (repositoryAllowed) {
            getGitHubDataSyncService().syncSyncTargetAsync(toSyncTarget(workspace, monitor));
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
            natsConsumerService.updateWorkspaceConsumer(workspace.getId());
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
     * Enumerate all repositories available to the installation when repository
     * selection is ALL and ensure monitors exist.
     */
    @Transactional
    public void ensureAllInstallationRepositoriesCovered(long installationId) {
        ensureAllInstallationRepositoriesCovered(installationId, Collections.emptySet(), false);
    }

    /**
     * Enumerate all repositories available to the installation when repository
     * selection is ALL and ensure monitors exist.
     *
     * @param installationId the GitHub App installation ID
     * @param deferSync      if true, skip immediate sync (use during provisioning
     *                       when activation will sync in bulk)
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
            // Create or update Repository entity from installation payload
            ensureRepositoryFromSnapshot(snapshot, workspace);
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
        String slug = workspaceSlugService.normalize(rawSlug);
        workspaceSlugService.validate(slug);

        if (!workspaceSlugService.isAvailable(slug)) {
            throw new WorkspaceSlugConflictException(slug);
        }

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

    private String requireSlug(WorkspaceContext workspaceContext) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");
        String slug = workspaceContext.slug();
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace context slug must not be blank.");
        }
        return slug;
    }

    public Workspace updateSchedule(String slug, Integer day, String time) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateSchedule(workspace.getId(), day, time);
    }

    public Workspace updateSchedule(WorkspaceContext workspaceContext, Integer day, String time) {
        return updateSchedule(requireSlug(workspaceContext), day, time);
    }

    public Workspace updateNotifications(String slug, Boolean enabled, String team, String channelId) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateNotifications(workspace.getId(), enabled, team, channelId);
    }

    public Workspace updateNotifications(
        WorkspaceContext workspaceContext,
        Boolean enabled,
        String team,
        String channelId
    ) {
        return updateNotifications(requireSlug(workspaceContext), enabled, team, channelId);
    }

    public Workspace updateToken(String slug, String personalAccessToken) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateToken(workspace.getId(), personalAccessToken);
    }

    public Workspace updateToken(WorkspaceContext workspaceContext, String personalAccessToken) {
        return updateToken(requireSlug(workspaceContext), personalAccessToken);
    }

    public Workspace updateSlackCredentials(String slug, String slackToken, String slackSigningSecret) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updateSlackCredentials(workspace.getId(), slackToken, slackSigningSecret);
    }

    public Workspace updateSlackCredentials(
        WorkspaceContext workspaceContext,
        String slackToken,
        String slackSigningSecret
    ) {
        return updateSlackCredentials(requireSlug(workspaceContext), slackToken, slackSigningSecret);
    }

    public Workspace updatePublicVisibility(String slug, Boolean isPubliclyViewable) {
        Workspace workspace = requireWorkspace(slug);
        return workspaceSettingsService.updatePublicVisibility(workspace.getId(), isPubliclyViewable);
    }

    public Workspace updatePublicVisibility(WorkspaceContext workspaceContext, Boolean isPubliclyViewable) {
        return updatePublicVisibility(requireSlug(workspaceContext), isPubliclyViewable);
    }

    @Transactional
    public Workspace renameSlug(WorkspaceContext workspaceContext, String newSlug) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");

        Long workspaceId = workspaceContext.id();
        if (workspaceId == null) {
            throw new EntityNotFoundException("Workspace", "context");
        }

        return renameSlug(workspaceId, newSlug);
    }

    @Transactional
    public Workspace renameSlug(Long workspaceId, String newSlug) {
        workspaceSlugService.validate(newSlug);

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        String currentSlug = workspace.getWorkspaceSlug();

        if (currentSlug.equals(newSlug)) {
            logger.info(
                "Workspace id={} rename to '{}' is no-op (already current slug)",
                workspaceId,
                LoggingUtils.sanitizeForLog(newSlug)
            );
            return workspace;
        }

        if (workspaceRepository.existsByWorkspaceSlug(newSlug)) {
            throw new WorkspaceSlugConflictException(newSlug);
        }

        if (!workspaceSlugService.isAvailable(newSlug)) {
            throw new WorkspaceSlugConflictException(newSlug);
        }

        workspaceSlugService.recordRename(workspace, currentSlug);

        workspace.setWorkspaceSlug(newSlug);
        Workspace saved = workspaceRepository.save(workspace);

        logger.info(
            "Workspace id={} renamed from '{}' to '{}' (permanent redirect created)",
            workspaceId,
            LoggingUtils.sanitizeForLog(currentSlug),
            LoggingUtils.sanitizeForLog(newSlug)
        );

        return saved;
    }

    private void createOwnerRole(Workspace workspace, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("Owner user id must not be null when creating a workspace.");
        }
        workspaceMembershipService.createMembership(workspace, ownerUserId, WorkspaceMembership.WorkspaceRole.OWNER);
    }

    /**
     * Looks up or creates a user for workspace ownership assignment.
     * TODO: Implement user sync via GraphQL API when available.
     * Currently falls back to checking existing users in the database.
     * Returns null if the user doesn't exist locally.
     */
    private Long syncGitHubUserForOwnership(long installationId, String accountLogin) {
        // TODO: User sync via GraphQL not yet implemented
        // For now, just look up existing users in the database
        var existingUser = userRepository.findByLogin(accountLogin);
        if (existingUser.isPresent()) {
            logger.info(
                "Found existing user '{}' (id={}) for workspace ownership.",
                LoggingUtils.sanitizeForLog(accountLogin),
                existingUser.get().getId()
            );
            return existingUser.get().getId();
        }

        logger.warn(
            "User '{}' not found in database for installation {}. User sync via GraphQL pending implementation.",
            LoggingUtils.sanitizeForLog(accountLogin),
            installationId
        );
        return null;
    }
}
