package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.integration.spi.ProvisioningListener;
import de.tum.cit.aet.hephaestus.integration.github.lifecycle.GithubLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.github.sync.GithubDataSyncService;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.workspace.RepositorySelection;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepositoryMonitorService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceScopeFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class WorkspaceProvisioningAdapter implements ProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioningAdapter.class);

    private final GithubLifecycleListener githubLifecycleListener;
    private final WorkspaceRepositoryMonitorService repositoryMonitorService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final WorkspaceRepository workspaceRepository;

    // Lazy-loaded to break circular reference with GithubDataSyncService
    private final ObjectProvider<GithubDataSyncService> gitHubDataSyncServiceProvider;
    private final AsyncTaskExecutor monitoringExecutor;

    public WorkspaceProvisioningAdapter(
        GithubLifecycleListener githubLifecycleListener,
        WorkspaceRepositoryMonitorService repositoryMonitorService,
        WorkspaceScopeFilter workspaceScopeFilter,
        WorkspaceRepository workspaceRepository,
        ObjectProvider<GithubDataSyncService> gitHubDataSyncServiceProvider,
        @Qualifier("monitoringExecutor") AsyncTaskExecutor monitoringExecutor
    ) {
        this.githubLifecycleListener = githubLifecycleListener;
        this.repositoryMonitorService = repositoryMonitorService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.workspaceRepository = workspaceRepository;
        this.gitHubDataSyncServiceProvider = gitHubDataSyncServiceProvider;
        this.monitoringExecutor = monitoringExecutor;
    }

    /** Lazy accessor for GithubDataSyncService to break circular dependency. */
    private GithubDataSyncService getGitHubDataSyncService() {
        return gitHubDataSyncServiceProvider.getObject();
    }

    @Override
    public void onInstallationCreated(InstallationData installation) {
        if (installation == null) {
            log.warn("Skipped workspace provisioning: reason=nullInstallationData");
            return;
        }

        // Check organization filter BEFORE creating workspace
        if (!workspaceScopeFilter.isOrganizationAllowed(installation.accountLogin())) {
            log.debug(
                "Skipped workspace provisioning: reason=organizationFiltered, accountLogin={}, installationId={}",
                installation.accountLogin(),
                installation.installationId()
            );
            return;
        }

        RepositorySelection selection = RepositorySelection.SELECTED; // Default selection
        Workspace workspace = githubLifecycleListener.createOrUpdateFromInstallation(
            installation.installationId(),
            installation.accountId(),
            installation.accountLogin(),
            mapAccountKind(installation.accountType()),
            installation.avatarUrl(),
            selection
        );

        if (workspace == null) {
            log.warn(
                "Skipped installation event: reason=workspaceCreationFailed, installationId={}",
                installation.installationId()
            );
            return;
        }

        // Add initial repositories from the installation event
        // These are provided for "created" events with "selected" repository selection
        // Create Repository entities AND monitors from the webhook metadata
        if (installation.repositories() != null && !installation.repositories().isEmpty()) {
            // Filter repositories based on workspace scope configuration before creating entities
            List<RepositorySnapshot> allowedRepos = installation
                .repositories()
                .stream()
                .filter(r -> workspaceScopeFilter.isRepositoryAllowed(r.nameWithOwner()))
                .toList();

            if (allowedRepos.size() < installation.repositories().size()) {
                log.debug(
                    "Filtered initial repositories by scope: installationId={}, allowed={}, total={}",
                    installation.installationId(),
                    allowedRepos.size(),
                    installation.repositories().size()
                );
            }

            if (!allowedRepos.isEmpty()) {
                log.info(
                    "Adding initial repositories: installationId={}, repoCount={}",
                    installation.installationId(),
                    allowedRepos.size()
                );
                for (RepositorySnapshot snapshot : allowedRepos) {
                    repositoryMonitorService.ensureRepositoryAndMonitorFromSnapshot(
                        installation.installationId(),
                        snapshot
                    );
                }
            }
        }
    }

    @Override
    public void onInstallationDeleted(Long installationId) {
        if (installationId == null) {
            log.warn("Skipped installation deletion: reason=nullInstallationId");
            return;
        }

        // 1. Stop NATS consumer for the workspace first (before removing monitors)
        githubLifecycleListener.stopNatsForInstallation(installationId);

        // 2. Remove all repository monitors AND delete repositories for this installation
        // When installation is deleted, we clean up all associated data
        repositoryMonitorService.removeAllRepositoriesFromMonitor(installationId, true);

        // 3. Mark workspace as PURGED (not SUSPENDED - deleted is permanent)
        githubLifecycleListener.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.PURGED);

        log.info("Completed installation cleanup: installationId={}", installationId);
    }

    @Override
    public void onRepositoriesAdded(Long installationId, List<RepositorySnapshot> repositories) {
        if (installationId == null || repositories == null || repositories.isEmpty()) {
            log.debug(
                "Skipped repositories added event: reason=missingData, installationId={}, hasRepositories={}",
                installationId,
                repositories != null && !repositories.isEmpty()
            );
            return;
        }

        // Filter repositories based on workspace scope configuration before creating entities
        List<RepositorySnapshot> allowedRepos = repositories
            .stream()
            .filter(r -> workspaceScopeFilter.isRepositoryAllowed(r.nameWithOwner()))
            .toList();

        if (allowedRepos.size() < repositories.size()) {
            log.debug(
                "Filtered repositories by scope: installationId={}, allowed={}, total={}",
                installationId,
                allowedRepos.size(),
                repositories.size()
            );
        }

        if (allowedRepos.isEmpty()) {
            log.debug("No repositories to add after filtering: installationId={}", installationId);
            return;
        }

        for (RepositorySnapshot snapshot : allowedRepos) {
            repositoryMonitorService.ensureRepositoryAndMonitorFromSnapshot(installationId, snapshot);
        }
        log.info("Added repositories to monitor: installationId={}, repoCount={}", installationId, allowedRepos.size());
    }

    @Override
    public void onRepositoriesRemoved(Long installationId, List<String> repositoryNames) {
        if (installationId == null || repositoryNames == null || repositoryNames.isEmpty()) {
            log.debug(
                "Skipped repositories removed event: reason=missingData, installationId={}, hasRepositories={}",
                installationId,
                repositoryNames != null && !repositoryNames.isEmpty()
            );
            return;
        }

        for (String nameWithOwner : repositoryNames) {
            repositoryMonitorService.removeRepositoryMonitorForInstallation(installationId, nameWithOwner);
        }
        log.info(
            "Removed repositories from monitor: installationId={}, repoCount={}",
            installationId,
            repositoryNames.size()
        );
    }

    @Override
    public void onAccountRenamed(Long installationId, String oldLogin, String newLogin) {
        if (installationId == null || newLogin == null || newLogin.isBlank()) {
            log.warn(
                "Skipped account rename: reason=invalidData, installationId={}, oldLogin={}, newLogin={}",
                installationId,
                oldLogin,
                newLogin
            );
            return;
        }

        githubLifecycleListener.handleAccountRename(installationId, oldLogin, newLogin);
    }

    @Override
    public void onInstallationSuspended(Long installationId) {
        if (installationId == null) {
            log.warn("Skipped installation suspension: reason=nullInstallationId");
            return;
        }

        // Stop NATS consumer first to stop processing webhook events
        githubLifecycleListener.stopNatsForInstallation(installationId);
        githubLifecycleListener.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        log.info("Suspended installation: installationId={}", installationId);
    }

    @Override
    public void onInstallationActivated(Long installationId) {
        if (installationId == null) {
            log.warn("Skipped installation activation: reason=nullInstallationId");
            return;
        }

        // Update status first
        githubLifecycleListener.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.ACTIVE);

        // Sync after the outer transaction commits so RepositoryToMonitor rows are visible.
        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresent(workspace -> {
                Long workspaceId = workspace.getId();

                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                triggerInitialSync(installationId, workspaceId);
                            }
                        }
                    );
                } else {
                    triggerInitialSync(installationId, workspaceId);
                }
            });

        // Start NATS consumer to resume webhook processing
        githubLifecycleListener.startNatsForInstallation(installationId);
        log.info("Activated installation: installationId={}", installationId);
    }

    @Override
    public void onRepositorySelectionChanged(Long installationId, String selection) {
        if (installationId == null) {
            log.warn("Skipped repository selection change: reason=nullInstallationId");
            return;
        }

        RepositorySelection repoSelection = parseRepositorySelection(selection);
        githubLifecycleListener.updateRepositorySelection(installationId, repoSelection);
        log.debug("Updated repository selection: installationId={}, selection={}", installationId, repoSelection);
    }

    private void triggerInitialSync(Long installationId, Long workspaceId) {
        log.info(
            "Starting initial sync for activated installation: installationId={}, workspaceId={}",
            installationId,
            workspaceId
        );

        monitoringExecutor.execute(() -> {
            try {
                getGitHubDataSyncService().syncAllRepositories(workspaceId);
                log.info(
                    "Completed initial sync for activated installation: installationId={}, workspaceId={}",
                    installationId,
                    workspaceId
                );
            } catch (Exception e) {
                log.error(
                    "Failed initial sync for activated installation: installationId={}, workspaceId={}",
                    installationId,
                    workspaceId,
                    e
                );
            }
        });
    }

    private RepositorySelection parseRepositorySelection(String selection) {
        if ("all".equalsIgnoreCase(selection)) {
            return RepositorySelection.ALL;
        }
        return RepositorySelection.SELECTED;
    }

    /**
     * Bridges the legacy {@link ProvisioningListener.AccountType} enum (GitHub-shaped, 2 values)
     * to the framework-wide {@link IntegrationLifecycleListener.AccountKind} (3 values, includes
     * {@code TEAM_WORKSPACE} for Slack/Outline). The third value is unreachable from the GitHub
     * SPI surface, hence the simple binary mapping.
     */
    private static IntegrationLifecycleListener.AccountKind mapAccountKind(ProvisioningListener.AccountType type) {
        if (type == null) {
            return IntegrationLifecycleListener.AccountKind.ORGANIZATION;
        }
        return switch (type) {
            case ORGANIZATION -> IntegrationLifecycleListener.AccountKind.ORGANIZATION;
            case USER -> IntegrationLifecycleListener.AccountKind.USER;
        };
    }
}
