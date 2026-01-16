package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.workspace.RepositorySelection;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceInstallationService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepositoryMonitorService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceProvisioningAdapter implements ProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioningAdapter.class);

    private final WorkspaceInstallationService workspaceInstallationService;
    private final WorkspaceRepositoryMonitorService repositoryMonitorService;

    public WorkspaceProvisioningAdapter(
        WorkspaceInstallationService workspaceInstallationService,
        WorkspaceRepositoryMonitorService repositoryMonitorService
    ) {
        this.workspaceInstallationService = workspaceInstallationService;
        this.repositoryMonitorService = repositoryMonitorService;
    }

    @Override
    public void onInstallationCreated(InstallationData installation) {
        if (installation == null) {
            log.warn("Skipped workspace provisioning: reason=nullInstallationData");
            return;
        }

        RepositorySelection selection = RepositorySelection.SELECTED; // Default selection
        Workspace workspace = workspaceInstallationService.createOrUpdateFromInstallation(
            installation.installationId(),
            installation.accountId(),
            installation.accountLogin(),
            installation.accountType(),
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
            log.info(
                "Adding initial repositories: installationId={}, repoCount={}",
                installation.installationId(),
                installation.repositories().size()
            );
            for (RepositorySnapshot snapshot : installation.repositories()) {
                repositoryMonitorService.ensureRepositoryAndMonitorFromSnapshot(
                    installation.installationId(),
                    snapshot
                );
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
        workspaceInstallationService.stopNatsForInstallation(installationId);

        // 2. Remove all repository monitors AND delete repositories for this installation
        // When installation is deleted, we clean up all associated data
        repositoryMonitorService.removeAllRepositoriesFromMonitor(installationId, true);

        // 3. Mark workspace as PURGED (not SUSPENDED - deleted is permanent)
        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.PURGED);

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

        for (RepositorySnapshot snapshot : repositories) {
            repositoryMonitorService.ensureRepositoryAndMonitorFromSnapshot(installationId, snapshot);
        }
        log.info("Added repositories to monitor: installationId={}, repoCount={}", installationId, repositories.size());
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

        workspaceInstallationService.handleAccountRename(installationId, oldLogin, newLogin);
    }

    @Override
    public void onInstallationSuspended(Long installationId) {
        if (installationId == null) {
            log.warn("Skipped installation suspension: reason=nullInstallationId");
            return;
        }

        // Stop NATS consumer first to stop processing webhook events
        workspaceInstallationService.stopNatsForInstallation(installationId);
        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        log.info("Suspended installation: installationId={}", installationId);
    }

    @Override
    public void onInstallationActivated(Long installationId) {
        if (installationId == null) {
            log.warn("Skipped installation activation: reason=nullInstallationId");
            return;
        }

        // Update status first, then start NATS consumer to resume webhook processing
        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.ACTIVE);
        workspaceInstallationService.startNatsForInstallation(installationId);
        log.info("Activated installation: installationId={}", installationId);
    }

    @Override
    public void onRepositorySelectionChanged(Long installationId, String selection) {
        if (installationId == null) {
            log.warn("Skipped repository selection change: reason=nullInstallationId");
            return;
        }

        RepositorySelection repoSelection = parseRepositorySelection(selection);
        workspaceInstallationService.updateRepositorySelection(installationId, repoSelection);
        log.debug("Updated repository selection: installationId={}, selection={}", installationId, repoSelection);
    }

    private RepositorySelection parseRepositorySelection(String selection) {
        if ("all".equalsIgnoreCase(selection)) {
            return RepositorySelection.ALL;
        }
        return RepositorySelection.SELECTED;
    }
}
