package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.workspace.RepositorySelection;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceInstallationService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepositoryMonitorService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceProvisioningAdapter implements WorkspaceProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioningAdapter.class);

    private final WorkspaceInstallationService workspaceInstallationService;
    private final WorkspaceRepositoryMonitorService repositoryMonitorService;
    private final OrganizationService organizationService;

    public WorkspaceProvisioningAdapter(
        WorkspaceInstallationService workspaceInstallationService,
        WorkspaceRepositoryMonitorService repositoryMonitorService,
        OrganizationService organizationService
    ) {
        this.workspaceInstallationService = workspaceInstallationService;
        this.repositoryMonitorService = repositoryMonitorService;
        this.organizationService = organizationService;
    }

    @Override
    public void onInstallationCreated(InstallationData installation) {
        if (installation == null) {
            log.warn("Received null installation data, skipping workspace provisioning");
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
            log.info(
                "Skipping installation event for {}: workspace could not be ensured",
                installation.installationId()
            );
        }
    }

    @Override
    public void onInstallationDeleted(Long installationId) {
        if (installationId == null) {
            log.warn("Received null installation ID for deletion, skipping");
            return;
        }

        // 1. Stop NATS consumer for the workspace first (before removing monitors)
        workspaceInstallationService.stopNatsForInstallation(installationId);

        // 2. Remove all repository monitors for this installation
        repositoryMonitorService.removeAllRepositoriesFromMonitor(installationId);

        // 3. Detach organization from installation (set installationId to null)
        organizationService.detachInstallation(installationId);

        // 4. Mark workspace as PURGED (not SUSPENDED - deleted is permanent)
        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.PURGED);

        log.info("Completed cleanup for deleted installation {}", installationId);
    }

    @Override
    public void onRepositoriesAdded(Long installationId, List<String> repositoryNames) {
        if (installationId == null || repositoryNames == null || repositoryNames.isEmpty()) {
            log.debug(
                "Skipping onRepositoriesAdded: installationId={}, repositoryNames={}",
                installationId,
                repositoryNames
            );
            return;
        }

        // Repository additions are typically handled by repository-specific event handlers
        // This hook allows for future expansion where workspace-level actions are needed
        log.debug("Repositories added to installation {}: {}", installationId, repositoryNames);
    }

    @Override
    public void onRepositoriesRemoved(Long installationId, List<String> repositoryNames) {
        if (installationId == null || repositoryNames == null || repositoryNames.isEmpty()) {
            log.debug(
                "Skipping onRepositoriesRemoved: installationId={}, repositoryNames={}",
                installationId,
                repositoryNames
            );
            return;
        }

        // Repository removals are typically handled by repository-specific event handlers
        // This hook allows for future expansion where workspace-level actions are needed
        log.debug("Repositories removed from installation {}: {}", installationId, repositoryNames);
    }

    @Override
    public void onAccountRenamed(Long installationId, String oldLogin, String newLogin) {
        if (installationId == null || newLogin == null || newLogin.isBlank()) {
            log.warn(
                "Invalid account rename data: installationId={}, oldLogin={}, newLogin={}",
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
            log.warn("Received null installation ID for suspension, skipping");
            return;
        }

        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        log.debug("Installation {} suspended", installationId);
    }

    @Override
    public void onInstallationActivated(Long installationId) {
        if (installationId == null) {
            log.warn("Received null installation ID for activation, skipping");
            return;
        }

        workspaceInstallationService.updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.ACTIVE);
        log.debug("Installation {} activated", installationId);
    }

    @Override
    public void onRepositorySelectionChanged(Long installationId, String selection) {
        if (installationId == null) {
            log.warn("Received null installation ID for repository selection change, skipping");
            return;
        }

        RepositorySelection repoSelection = parseRepositorySelection(selection);
        workspaceInstallationService.updateRepositorySelection(installationId, repoSelection);
        log.debug("Updated repository selection for installation {} to {}", installationId, repoSelection);
    }

    private RepositorySelection parseRepositorySelection(String selection) {
        if ("all".equalsIgnoreCase(selection)) {
            return RepositorySelection.ALL;
        }
        return RepositorySelection.SELECTED;
    }
}
