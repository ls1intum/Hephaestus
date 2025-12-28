package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
import de.tum.in.www1.hephaestus.workspace.RepositorySelection;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceProvisioningAdapter implements WorkspaceProvisioningListener {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceProvisioningAdapter.class);

    private final WorkspaceService workspaceService;

    public WorkspaceProvisioningAdapter(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public void onInstallationCreated(InstallationData installation) {
        if (installation == null) {
            logger.warn("Received null installation data, skipping workspace provisioning");
            return;
        }

        RepositorySelection selection = RepositorySelection.SELECTED; // Default selection
        Workspace workspace = workspaceService.ensureForInstallation(
            installation.installationId(),
            installation.accountLogin(),
            selection
        );

        if (workspace == null) {
            logger.info(
                "Skipping installation event for {}: workspace could not be ensured",
                installation.installationId()
            );
        }
    }

    @Override
    public void onInstallationDeleted(Long installationId) {
        if (installationId == null) {
            logger.warn("Received null installation ID for deletion, skipping");
            return;
        }

        // Stop NATS consumer and mark workspace as appropriate
        workspaceService.stopNatsConsumerForInstallation(installationId);
        workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        logger.info("Handled installation deletion for installation {}", installationId);
    }

    @Override
    public void onRepositoriesAdded(Long installationId, List<String> repositoryNames) {
        if (installationId == null || repositoryNames == null || repositoryNames.isEmpty()) {
            logger.debug(
                "Skipping onRepositoriesAdded: installationId={}, repositoryNames={}",
                installationId,
                repositoryNames
            );
            return;
        }

        // Repository additions are typically handled by repository-specific event handlers
        // This hook allows for future expansion where workspace-level actions are needed
        logger.debug("Repositories added to installation {}: {}", installationId, repositoryNames);
    }

    @Override
    public void onRepositoriesRemoved(Long installationId, List<String> repositoryNames) {
        if (installationId == null || repositoryNames == null || repositoryNames.isEmpty()) {
            logger.debug(
                "Skipping onRepositoriesRemoved: installationId={}, repositoryNames={}",
                installationId,
                repositoryNames
            );
            return;
        }

        // Repository removals are typically handled by repository-specific event handlers
        // This hook allows for future expansion where workspace-level actions are needed
        logger.debug("Repositories removed from installation {}: {}", installationId, repositoryNames);
    }

    @Override
    public void onAccountRenamed(Long installationId, String oldLogin, String newLogin) {
        if (installationId == null || newLogin == null || newLogin.isBlank()) {
            logger.warn(
                "Invalid account rename data: installationId={}, oldLogin={}, newLogin={}",
                installationId,
                oldLogin,
                newLogin
            );
            return;
        }

        workspaceService.handleInstallationTargetRename(installationId, oldLogin, newLogin);
    }

    @Override
    public void onInstallationSuspended(Long installationId) {
        if (installationId == null) {
            logger.warn("Received null installation ID for suspension, skipping");
            return;
        }

        workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.SUSPENDED);
        logger.debug("Installation {} suspended", installationId);
    }

    @Override
    public void onInstallationActivated(Long installationId) {
        if (installationId == null) {
            logger.warn("Received null installation ID for activation, skipping");
            return;
        }

        workspaceService.updateStatusForInstallation(installationId, Workspace.WorkspaceStatus.ACTIVE);
        logger.debug("Installation {} activated", installationId);
    }

    @Override
    public void onRepositorySelectionChanged(Long installationId, String selection) {
        if (installationId == null) {
            logger.warn("Received null installation ID for repository selection change, skipping");
            return;
        }

        RepositorySelection repoSelection = parseRepositorySelection(selection);
        workspaceService.updateRepositorySelection(installationId, repoSelection);
        logger.debug("Updated repository selection for installation {} to {}", installationId, repoSelection);
    }

    private RepositorySelection parseRepositorySelection(String selection) {
        if ("all".equalsIgnoreCase(selection)) {
            return RepositorySelection.ALL;
        }
        return RepositorySelection.SELECTED;
    }
}
