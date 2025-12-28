package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncTarget;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.exception.RepositoryAlreadyMonitoredException;
import de.tum.in.www1.hephaestus.workspace.exception.RepositoryManagementNotAllowedException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing repository monitoring within workspaces.
 * Handles adding, removing, and listing monitored repositories.
 */
@Service
public class WorkspaceRepositoryMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceRepositoryMonitorService.class);

    // Configuration
    private final boolean isNatsEnabled;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;

    // Services
    private final NatsConsumerService natsConsumerService;
    private final GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator;
    private final WorkspaceScopeFilter workspaceScopeFilter;

    // Lazy-loaded dependencies (to break circular references)
    private final ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;

    public WorkspaceRepositoryMonitorService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        NatsConsumerService natsConsumerService,
        GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator,
        WorkspaceScopeFilter workspaceScopeFilter,
        ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.natsConsumerService = natsConsumerService;
        this.installationRepositoryEnumerator = installationRepositoryEnumerator;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
    }

    /** Lazy accessor for GitHubDataSyncService to break circular dependency. */
    private GitHubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    // ========================================================================
    // Public API: Get Monitored Repositories
    // ========================================================================

    @Transactional(readOnly = true)
    public List<String> getMonitoredRepositories(String slug) {
        Workspace workspace = requireWorkspace(slug);
        logger.info(
            "Getting repositories to monitor for workspace id={} (slug={})",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        return workspace.getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList();
    }

    public List<String> getMonitoredRepositories(WorkspaceContext workspaceContext) {
        return getMonitoredRepositories(requireSlug(workspaceContext));
    }

    // ========================================================================
    // Public API: Add Repository to Monitor
    // ========================================================================

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

    /**
     * Batch add multiple repositories to monitor for a workspace.
     *
     * @param slug            the workspace slug
     * @param namesWithOwners the repository full names (e.g., "owner/repo")
     */
    public void addRepositoriesToMonitor(String slug, Collection<String> namesWithOwners)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        for (String nameWithOwner : namesWithOwners) {
            addRepositoryToMonitor(slug, nameWithOwner);
        }
    }

    public void addRepositoriesToMonitor(WorkspaceContext workspaceContext, Collection<String> namesWithOwners)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        addRepositoriesToMonitor(requireSlug(workspaceContext), namesWithOwners);
    }

    // ========================================================================
    // Public API: Remove Repository from Monitor
    // ========================================================================

    public void removeRepositoryFromMonitor(String slug, String nameWithOwner) throws EntityNotFoundException {
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

    public void removeRepositoryFromMonitor(WorkspaceContext workspaceContext, String nameWithOwner)
        throws EntityNotFoundException {
        removeRepositoryFromMonitor(requireSlug(workspaceContext), nameWithOwner);
    }

    // ========================================================================
    // Public API: Installation-based Repository Monitor Management
    // ========================================================================

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
    public Optional<Workspace> removeAllRepositoriesFromMonitor(long installationId) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        workspaceOpt.ifPresent(workspace -> {
            repositoryToMonitorRepository
                .findByWorkspaceId(workspace.getId())
                .forEach(monitor -> deleteRepositoryMonitor(workspace, monitor));
        });
        return workspaceOpt;
    }

    // ========================================================================
    // Public API: Ensure All Installation Repositories Covered
    // ========================================================================

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

    // ========================================================================
    // Internal Helper Methods
    // ========================================================================

    /**
     * Converts a RepositoryToMonitor to a SyncTarget for the sync service.
     */
    SyncTarget toSyncTarget(Workspace workspace, RepositoryToMonitor rtm) {
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
     * Checks if a repository exists (placeholder until GraphQL repository sync is available).
     * TODO: Implement proper repository validation via GraphQL API.
     */
    private Optional<Repository> fetchRepositoryOrThrow(Long workspaceId, String nameWithOwner) {
        // For now, check if repository exists in database
        // Full validation will be implemented when GraphQL repository sync is available
        return repositoryRepository.findByNameWithOwner(nameWithOwner);
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
     *                  when activation will sync all repositories in bulk)
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
     * Creates or updates a Repository entity from an installation repository snapshot.
     * This ensures the repository exists in the database with basic metadata from the installation payload.
     *
     * @param snapshot  the installation repository snapshot
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
