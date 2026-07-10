package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationRepositoryEnumerator;
import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationSuspensionTracker;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ProvisioningListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.events.RepositoryAboutToBeDeletedEvent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.exception.RepositoryAlreadyMonitoredException;
import de.tum.cit.aet.hephaestus.workspace.exception.RepositoryManagementNotAllowedException;
import io.micrometer.common.util.StringUtils;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing repository monitoring within workspaces.
 * Handles adding, removing, and listing monitored repositories.
 *
 * <p>Workspace-agnostic: This service manages the workspace-repository mapping
 * configuration itself. All methods take workspace slug/context as parameters
 * to identify which workspace to operate on.
 */
@Service
@WorkspaceAgnostic("Manages workspace-repository mapping configuration")
public class WorkspaceRepositoryMonitorService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRepositoryMonitorService.class);

    // Configuration
    private final NatsConnectionProperties natsProperties;

    // Core repositories
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final IdentityProviderRepository gitProviderRepository;

    // Services — natsConsumerService absent under webhook profile.
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumerService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final GitRepositoryManager gitRepositoryManager;

    /**
     * Per-kind ports. The workspace module never imports a vendor-specific service —
     * installation-bound operations dispatch by the workspace's bound kind. A missing
     * impl for a given kind is treated as "this kind doesn't support installation-bound
     * operations" (e.g. GitLab PAT) rather than an error.
     */
    private final Map<IntegrationKind, InstallationSuspensionTracker> suspensionTrackers;
    private final Map<IntegrationKind, InstallationRepositoryEnumerator> installationEnumerators;
    private final Map<IntegrationKind, WorkspaceDataSyncTrigger> dataSyncTriggers;
    private final ApplicationEventPublisher eventPublisher;

    // Authoritative source for provider mode and credentials
    private final ConnectionService connectionService;

    public WorkspaceRepositoryMonitorService(
        NatsConnectionProperties natsProperties,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        IdentityProviderRepository gitProviderRepository,
        ObjectProvider<IntegrationNatsConsumer> natsConsumerService,
        WorkspaceScopeFilter workspaceScopeFilter,
        GitRepositoryManager gitRepositoryManager,
        ConnectionService connectionService,
        List<InstallationSuspensionTracker> suspensionTrackerList,
        List<InstallationRepositoryEnumerator> installationEnumeratorList,
        List<WorkspaceDataSyncTrigger> dataSyncTriggerList,
        ApplicationEventPublisher eventPublisher
    ) {
        this.natsProperties = natsProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.natsConsumerService = natsConsumerService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitRepositoryManager = gitRepositoryManager;
        this.connectionService = connectionService;
        this.eventPublisher = eventPublisher;

        Map<IntegrationKind, InstallationSuspensionTracker> suspensionMap = new EnumMap<>(IntegrationKind.class);
        for (InstallationSuspensionTracker t : suspensionTrackerList) {
            suspensionMap.put(t.kind(), t);
        }
        this.suspensionTrackers = suspensionMap;

        Map<IntegrationKind, InstallationRepositoryEnumerator> enumeratorMap = new EnumMap<>(IntegrationKind.class);
        for (InstallationRepositoryEnumerator e : installationEnumeratorList) {
            enumeratorMap.put(e.kind(), e);
        }
        this.installationEnumerators = enumeratorMap;

        Map<IntegrationKind, WorkspaceDataSyncTrigger> triggerMap = new EnumMap<>(IntegrationKind.class);
        for (WorkspaceDataSyncTrigger t : dataSyncTriggerList) {
            triggerMap.put(t.kind(), t);
        }
        this.dataSyncTriggers = triggerMap;
    }

    // Public API: Get Monitored Repositories

    @Transactional(readOnly = true)
    public List<String> getMonitoredRepositories(String slug) {
        Workspace workspace = requireWorkspace(slug);
        log.debug(
            "Retrieved monitored repositories: workspaceId={}, workspaceSlug={}",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        return workspace.getRepositoriesToMonitor().stream().map(RepositoryToMonitor::getNameWithOwner).toList();
    }

    public List<String> getMonitoredRepositories(WorkspaceContext workspaceContext) {
        return getMonitoredRepositories(requireSlug(workspaceContext));
    }

    // Public API: Add Repository to Monitor

    public void addRepositoryToMonitor(String slug, String nameWithOwner)
        throws RepositoryAlreadyMonitoredException, EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);

        // Block repository management for GitHub App Installation workspaces
        if (isGitHubAppWorkspace(workspace)) {
            throw new RepositoryManagementNotAllowedException(slug);
        }

        if (
            workspace
                .getRepositoriesToMonitor()
                .stream()
                .anyMatch(r -> r.getNameWithOwner().equals(nameWithOwner))
        ) {
            log.debug(
                "Skipped repository monitor addition: reason=alreadyMonitored, nameWithOwner={}, workspaceId={}",
                LoggingUtils.sanitizeForLog(nameWithOwner),
                workspace.getId()
            );
            throw new RepositoryAlreadyMonitoredException(nameWithOwner);
        }

        // For GitLab PAT workspaces, the repo may not be synced yet — allow adding by name.
        // For other modes, validate that the repository exists in the database.
        if (!isGitLabWorkspace(workspace)) {
            var repository = findRepository(nameWithOwner);
            if (repository.isEmpty()) {
                log.debug(
                    "Skipped repository monitor addition: reason=repositoryNotFound, nameWithOwner={}",
                    LoggingUtils.sanitizeForLog(nameWithOwner)
                );
                throw new EntityNotFoundException("Repository", nameWithOwner);
            }
        }

        log.info(
            "Added repository to monitor: nameWithOwner={}, workspaceId={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

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

    // Public API: Remove Repository from Monitor

    public void removeRepositoryFromMonitor(String slug, String nameWithOwner) throws EntityNotFoundException {
        Workspace workspace = requireWorkspace(slug);

        // Block repository management for GitHub App Installation workspaces
        if (isGitHubAppWorkspace(workspace)) {
            throw new RepositoryManagementNotAllowedException(slug);
        }

        RepositoryToMonitor repositoryToMonitor = workspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(r -> r.getNameWithOwner().equals(nameWithOwner))
            .findFirst()
            .orElse(null);

        if (repositoryToMonitor == null) {
            log.debug(
                "Skipped repository monitor removal: reason=notMonitored, nameWithOwner={}, workspaceId={}",
                LoggingUtils.sanitizeForLog(nameWithOwner),
                workspace.getId()
            );
            throw new EntityNotFoundException("Repository", nameWithOwner);
        }

        log.info(
            "Removed repository from monitor: nameWithOwner={}, workspaceId={}",
            LoggingUtils.sanitizeForLog(nameWithOwner),
            workspace.getId()
        );

        deleteRepositoryMonitor(workspace, repositoryToMonitor);

        // Only delete repository if no other workspace is monitoring it
        deleteRepositoryIfOrphaned(nameWithOwner);
    }

    public void removeRepositoryFromMonitor(WorkspaceContext workspaceContext, String nameWithOwner)
        throws EntityNotFoundException {
        removeRepositoryFromMonitor(requireSlug(workspaceContext), nameWithOwner);
    }

    // Public API: Installation-based Repository Monitor Management

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
        // Check if installation is suspended BEFORE adding repo monitor.
        // This prevents NATS replay from adding repos to suspended installations.
        // Installation-bound suspension is a GitHub-App concept today; the GitLab kind
        // does not register a tracker and the check short-circuits to "not suspended".
        if (isInstallationSuspended(installationId)) {
            log.debug(
                "Skipped repository monitor: reason=installationSuspended, installationId={}, repoName={}",
                installationId,
                LoggingUtils.sanitizeForLog(nameWithOwner)
            );
            return Optional.empty();
        }

        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || StringUtils.isBlank(nameWithOwner)) {
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
        if (workspaceOpt.isEmpty() || StringUtils.isBlank(nameWithOwner)) {
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
        var result = removeRepositoryMonitorInternal(workspace, monitor);

        // Also delete the Repository entity if no other workspace is monitoring it
        // This prevents orphan repos that cause permanent sync errors
        deleteRepositoryIfOrphaned(nameWithOwner);

        return result;
    }

    /**
     * Remove all repository monitors tied to an installation and optionally delete orphaned repositories.
     *
     * @param installationId     the GitHub App installation ID
     * @param deleteRepositories if true, also delete Repository entities that are no longer monitored by any workspace
     */
    @Transactional
    public Optional<Workspace> removeAllRepositoriesFromMonitor(long installationId, boolean deleteRepositories) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        workspaceOpt.ifPresent(workspace -> {
            List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
            for (RepositoryToMonitor monitor : monitors) {
                String nameWithOwner = monitor.getNameWithOwner();
                deleteRepositoryMonitor(workspace, monitor);

                // Delete the Repository entity only if no other workspace is monitoring it
                if (deleteRepositories && nameWithOwner != null) {
                    deleteRepositoryIfOrphaned(nameWithOwner);
                }
            }
        });
        return workspaceOpt;
    }

    /**
     * Remove all repository monitors tied to an installation.
     * Does not delete the Repository entities.
     */
    @Transactional
    public Optional<Workspace> removeAllRepositoriesFromMonitor(long installationId) {
        return removeAllRepositoriesFromMonitor(installationId, false);
    }

    /**
     * Creates a Repository entity and corresponding RepositoryToMonitor from a provisioning snapshot.
     * This is used during installation provisioning to create repositories from webhook metadata.
     *
     * <p>Idempotent: If the repository already exists, it will be updated with the snapshot data.
     * If the monitor already exists, no duplicate will be created.
     *
     * @param installationId the GitHub App installation ID
     * @param snapshot       the repository snapshot from the webhook payload
     */
    @Transactional
    public void ensureRepositoryAndMonitorFromSnapshot(
        long installationId,
        ProvisioningListener.RepositorySnapshot snapshot
    ) {
        if (snapshot == null || StringUtils.isBlank(snapshot.nameWithOwner())) {
            return;
        }

        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty()) {
            return;
        }

        Workspace workspace = workspaceOpt.get();

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseThrow(() ->
                new IllegalStateException("IdentityProvider not found for type=GITHUB, serverUrl=https://github.com")
            );

        // Create or update the Repository entity with organization linking
        ensureRepositoryFromSnapshot(
            workspace,
            provider,
            snapshot.id(),
            snapshot.nameWithOwner(),
            snapshot.name(),
            snapshot.isPrivate()
        );

        // Create the RepositoryToMonitor if it doesn't exist.
        // Defer sync: provisioning creates monitors in bulk; the activation phase
        // will trigger a full sync for all repositories in the workspace.
        ensureRepositoryMonitorForInstallation(installationId, snapshot.nameWithOwner(), true);
    }

    // Public API: Ensure All Installation Repositories Covered

    /**
     * Enumerate all repositories available to the installation when repository
     * selection is ALL and ensure monitors exist.
     *
     * @param installationId       the GitHub App installation ID
     * @param protectedRepositories repositories to keep even if not in installation (can be null)
     * @param deferSync            if true, skip immediate sync (use during provisioning
     *                             when activation will sync in bulk)
     */
    @Transactional
    public void ensureAllInstallationRepositoriesCovered(
        long installationId,
        Collection<String> protectedRepositories,
        boolean deferSync
    ) {
        // Check if installation is suspended BEFORE adding repos and triggering syncs.
        // This prevents NATS replay of old "created" events from triggering hundreds of failed syncs.
        if (isInstallationSuspended(installationId)) {
            log.info("Skipped repository enumeration: reason=installationSuspended, installationId={}", installationId);
            return;
        }

        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty()) {
            return;
        }

        Workspace workspace = workspaceOpt.get();
        if (!isGitHubAppWorkspace(workspace)) {
            return;
        }

        InstallationRepositoryEnumerator enumerator = installationEnumerators.get(IntegrationKind.GITHUB);
        if (enumerator == null) {
            log.debug(
                "Skipped repository enumeration: reason=noEnumeratorForKind, installationId={}, kind={}",
                installationId,
                IntegrationKind.GITHUB
            );
            return;
        }
        var snapshots = enumerator.enumerate(installationId);
        if (snapshots.isEmpty()) {
            log.warn(
                "Skipped repository enumeration: reason=noDataReturned, installationId={}, workspaceSlug={}",
                installationId,
                LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug())
            );
            return;
        }

        // Filter snapshots BEFORE processing to avoid creating Repository entities for filtered repos
        List<InstallationRepositoryEnumerator.InstallationRepository> allowedSnapshots = snapshots
            .stream()
            .filter(s -> workspaceScopeFilter.isRepositoryAllowed(s.nameWithOwner()))
            .toList();

        if (allowedSnapshots.size() < snapshots.size()) {
            log.debug(
                "Filtered repositories during enumeration: allowed={}, total={}, installationId={}",
                allowedSnapshots.size(),
                snapshots.size(),
                installationId
            );
        }

        Set<String> desiredRepositories = allowedSnapshots
            .stream()
            .map(snapshot -> snapshot.nameWithOwner())
            .filter(name -> !StringUtils.isBlank(name))
            .map(name -> name.toLowerCase(Locale.ENGLISH))
            .collect(Collectors.toSet());

        if (protectedRepositories != null) {
            protectedRepositories
                .stream()
                .filter(name -> !StringUtils.isBlank(name))
                .filter(name -> workspaceScopeFilter.isRepositoryAllowed(name))
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .forEach(desiredRepositories::add);
        }

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseThrow(() ->
                new IllegalStateException("IdentityProvider not found for type=GITHUB, serverUrl=https://github.com")
            );

        allowedSnapshots.forEach(snapshot -> {
            // Create or update Repository entity with organization linking
            ensureRepositoryFromSnapshot(
                workspace,
                provider,
                snapshot.id(),
                snapshot.nameWithOwner(),
                snapshot.name(),
                snapshot.isPrivate()
            );
            // Create the RepositoryToMonitor if it doesn't exist
            ensureRepositoryMonitorForInstallation(installationId, snapshot.nameWithOwner(), deferSync);
        });

        repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .filter(monitor -> !StringUtils.isBlank(monitor.getNameWithOwner()))
            .filter(monitor -> !desiredRepositories.contains(monitor.getNameWithOwner().toLowerCase(Locale.ENGLISH)))
            .forEach(monitor -> removeRepositoryMonitorForInstallation(installationId, monitor.getNameWithOwner()));
    }

    // Internal Helper Methods

    /**
     * Finds a repository by its full name if it exists in the database.
     * Repositories are global entities (integration.scm is workspace-agnostic),
     * so this lookup is intentionally not scoped to a workspace.
     */
    private Optional<Repository> findRepository(String nameWithOwner) {
        return repositoryRepository.findByNameWithOwner(nameWithOwner);
    }

    private boolean shouldUseNats(Workspace workspace) {
        return natsProperties.enabled() && workspace != null;
    }

    /**
     * Deletes a repository only if no workspace is monitoring it.
     * This preserves repositories that are shared across multiple workspaces.
     * <p>
     * Also cascades deletion to any projects owned by this repository to maintain
     * referential integrity for the polymorphic project ownership model.
     *
     * @param nameWithOwner the repository full name (e.g., "owner/repo")
     */
    private void deleteRepositoryIfOrphaned(String nameWithOwner) {
        if (StringUtils.isBlank(nameWithOwner)) {
            return;
        }

        // Check if any workspace is still monitoring this repository
        long monitorCount = repositoryToMonitorRepository.countByNameWithOwner(nameWithOwner);
        if (monitorCount > 0) {
            log.debug(
                "Skipped repository deletion: reason=stillMonitored, repoName={}, monitorCount={}",
                LoggingUtils.sanitizeForLog(nameWithOwner),
                monitorCount
            );
            return;
        }

        // No workspace is monitoring this repository, safe to delete
        repositoryRepository
            .findByNameWithOwner(nameWithOwner)
            .ifPresent(repository -> {
                Long repoId = repository.getId();

                // Clean up local git clone before deleting the DB entity
                gitRepositoryManager.deleteClone(repoId);

                // Synchronous publish — listeners run in this transaction so any vendor-
                // owned dependents (GitHub Projects V2 polymorphic ownership rows) are gone
                // before the repository delete fires.
                eventPublisher.publishEvent(new RepositoryAboutToBeDeletedEvent(repoId));

                repositoryRepository.delete(repository);
                log.debug("Deleted orphaned repository: repoName={}", LoggingUtils.sanitizeForLog(nameWithOwner));
            });
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
        // Use saveAndFlush to force immediate constraint check so callers can catch DataIntegrityViolationException
        repositoryToMonitorRepository.saveAndFlush(monitor);
        workspace.getRepositoriesToMonitor().add(monitor);
        workspaceRepository.save(workspace);
        boolean repositoryAllowed = workspaceScopeFilter.isRepositoryAllowed(monitor);
        if (shouldUseNats(workspace) && repositoryAllowed) {
            // Update workspace consumer to include new repository subjects
            natsConsumerService.ifAvailable(svc -> svc.updateScopeConsumer(workspace.getId()));
        }
        if (deferSync) {
            log.debug(
                "Persisted repository with deferred sync: repoName={}",
                LoggingUtils.sanitizeForLog(monitor.getNameWithOwner())
            );
            return;
        }
        if (repositoryAllowed) {
            // Sync the just-persisted target through the bound kind's trigger. We pass
            // the freshly-saved monitor's id; the trigger looks it up via the SyncTargetProvider
            // and dispatches asynchronously. Falls back to a debug log when no trigger is
            // registered for the active kind (e.g. PAT-only deployments without sync wiring).
            connectionService
                .findActiveProviderKind(workspace.getId())
                .ifPresent(kind -> {
                    WorkspaceDataSyncTrigger trigger = dataSyncTriggers.get(kind);
                    if (trigger != null) {
                        trigger.syncSingleSyncTarget(monitor.getId());
                    } else {
                        log.debug(
                            "Skipped single-target sync: reason=noTriggerForKind, repoName={}, kind={}",
                            LoggingUtils.sanitizeForLog(monitor.getNameWithOwner()),
                            kind
                        );
                    }
                });
        } else {
            log.debug(
                "Persisted repository without sync: reason=filteredByScope, repoName={}",
                LoggingUtils.sanitizeForLog(monitor.getNameWithOwner())
            );
        }
    }

    private void deleteRepositoryMonitor(Workspace workspace, RepositoryToMonitor monitor) {
        // Remove from collection first - orphanRemoval=true will handle deletion
        workspace.getRepositoriesToMonitor().remove(monitor);
        workspaceRepository.save(workspace);
        if (shouldUseNats(workspace)) {
            // Update workspace consumer to remove repository subjects
            natsConsumerService.ifAvailable(svc -> svc.updateScopeConsumer(workspace.getId()));
        }
    }

    /**
     * Idempotently ensures a repository monitor exists for a workspace.
     *
     * <p>Handles race conditions from NATS message replay by catching duplicate key
     * violations. If another thread/message already created the monitor, we simply
     * return success - that's the idempotent behavior we want.
     */
    private Optional<Workspace> ensureRepositoryMonitorInternal(
        Workspace workspace,
        String nameWithOwner,
        boolean deferSync
    ) {
        if (workspace == null || StringUtils.isBlank(nameWithOwner)) {
            return Optional.ofNullable(workspace);
        }

        // Fast path: check if already exists (covers most replay cases)
        if (repositoryToMonitorRepository.existsByWorkspaceIdAndNameWithOwner(workspace.getId(), nameWithOwner)) {
            return Optional.of(workspace);
        }

        // Slow path: create new monitor with idempotent handling for race conditions
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setNameWithOwner(nameWithOwner);
        monitor.setWorkspace(workspace);

        try {
            persistRepositoryMonitor(workspace, monitor, deferSync);
        } catch (DataIntegrityViolationException e) {
            // Another message already created this monitor - we're idempotent, this is fine
            log.debug(
                "Repository monitor already exists (idempotent): repoName={}, workspaceId={}",
                LoggingUtils.sanitizeForLog(nameWithOwner),
                workspace.getId()
            );
        }

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
     * Creates or updates a Repository entity from a snapshot (installation enumeration or provisioning).
     * This ensures the repository exists in the database with basic metadata from the payload.
     *
     * <p>Note: Repositories are global entities (integration.scm is workspace-agnostic).
     * The workspace-repository association is managed through RepositoryToMonitor.
     *
     * <p>The organization is obtained from the workspace to ensure repositories from
     * organization installations have the proper organization_id set.
     *
     * <p>Uses a native SQL upsert ({@code INSERT ... ON CONFLICT DO UPDATE}) to atomically
     * handle concurrent inserts from NATS event processing and GraphQL sync, eliminating
     * optimistic locking errors. Visibility and HTML URL are derived inside the SQL from
     * {@code isPrivate} and {@code nameWithOwner} respectively.
     *
     * @param workspace     the workspace (used to get the organization)
     * @param provider      the resolved IdentityProvider instance
     * @param nativeId      the provider's original numeric ID for the repository
     * @param nameWithOwner the full name (e.g., "owner/repo")
     * @param name          the short repository name
     * @param isPrivate     whether the repository is private
     */
    private void ensureRepositoryFromSnapshot(
        Workspace workspace,
        IdentityProvider provider,
        long nativeId,
        String nameWithOwner,
        String name,
        boolean isPrivate
    ) {
        if (StringUtils.isBlank(nameWithOwner)) {
            return;
        }

        repositoryRepository.upsertFromSnapshot(
            nativeId,
            provider.getId(),
            nameWithOwner,
            name,
            isPrivate,
            workspace.getOrganization() != null ? workspace.getOrganization().getId() : null
        );
    }

    private Workspace requireWorkspace(String slug) {
        if (StringUtils.isBlank(slug)) {
            throw new IllegalArgumentException("Workspace slug must not be blank.");
        }
        return workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
    }

    private String requireSlug(WorkspaceContext workspaceContext) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");
        String slug = workspaceContext.slug();
        if (StringUtils.isBlank(slug)) {
            throw new IllegalArgumentException("Workspace context slug must not be blank.");
        }
        return slug;
    }

    /**
     * Dispatches the installation-suspension check across kind-specific trackers.
     *
     * <p>Today only the GitHub adapter contributes an impl. If a future kind also exposes
     * an installation-suspension concept, its tracker will participate without changes
     * here — short-circuit on the first {@code true} result.
     */
    private boolean isInstallationSuspended(long installationId) {
        for (InstallationSuspensionTracker t : suspensionTrackers.values()) {
            if (t.isInstallationMarkedSuspended(installationId)) {
                return true;
            }
        }
        return false;
    }

    /** Workspace is bound to a GitHub App installation (vs GitHub PAT or GitLab PAT). */
    private boolean isGitHubAppWorkspace(Workspace workspace) {
        return (
            connectionService
                .findActiveProviderKind(workspace.getId())
                .map(k -> k == IntegrationKind.GITHUB)
                .orElse(false) &&
            connectionService.findActiveGitHubAppConfig(workspace.getId()).isPresent()
        );
    }

    /** Workspace is bound to a GitLab PAT (vs GitHub of either flavour). */
    private boolean isGitLabWorkspace(Workspace workspace) {
        return connectionService
            .findActiveProviderKind(workspace.getId())
            .map(k -> k == IntegrationKind.GITLAB)
            .orElse(false);
    }
}
