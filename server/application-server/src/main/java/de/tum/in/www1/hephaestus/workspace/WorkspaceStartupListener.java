package de.tum.in.www1.hephaestus.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Spring lifecycle hook that kicks off workspace provisioning and monitoring once the application is ready.
 */
@Component
public class WorkspaceStartupListener {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceStartupListener.class);

    private final WorkspaceProvisioningService provisioningService;
    private final WorkspaceService workspaceService;

    public WorkspaceStartupListener(
        WorkspaceProvisioningService provisioningService,
        WorkspaceService workspaceService
    ) {
        this.provisioningService = provisioningService;
        this.workspaceService = workspaceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Starting workspace provisioning.");
        provisioningService.bootstrapDefaultPatWorkspace();
        provisioningService.ensureGitHubAppInstallations();
        workspaceService.initializeAllWorkspaces();
    }
}
