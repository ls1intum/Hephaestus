package de.tum.in.www1.hephaestus.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Spring lifecycle hook that kicks off workspace provisioning and monitoring once the application is ready.
 * Disabled during tests and OpenAPI spec generation to prevent GitHub API calls.
 */
@Component
@Profile("!specs & !test")
public class WorkspaceStartupListener {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceStartupListener.class);

    private final WorkspaceProvisioningService provisioningService;
    private final WorkspaceActivationService workspaceActivationService;

    public WorkspaceStartupListener(
        WorkspaceProvisioningService provisioningService,
        WorkspaceActivationService workspaceActivationService
    ) {
        this.provisioningService = provisioningService;
        this.workspaceActivationService = workspaceActivationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Starting workspace provisioning.");
        provisioningService.bootstrapDefaultPatWorkspace();
        provisioningService.ensureGitHubAppInstallations();
        workspaceActivationService.activateAllWorkspaces();
    }
}
