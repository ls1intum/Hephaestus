package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.event.WorkspacesInitializedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Spring lifecycle hook that kicks off workspace provisioning and monitoring once the application is ready.
 * Disabled during tests and OpenAPI spec generation to prevent GitHub API calls.
 *
 * <h2>Startup Sequence</h2>
 * <ol>
 *   <li>Provision workspaces (create from config or load from database)</li>
 *   <li>Publish {@link WorkspacesInitializedEvent} - signals installation consumer can start</li>
 *   <li>Activate workspaces (run full GraphQL sync for repos, issues, PRs)</li>
 * </ol>
 *
 * <p>The installation consumer needs only workspace entities to exist, not the full sync.
 * By publishing {@link WorkspacesInitializedEvent} before activation, we allow installation
 * events to be processed immediately after startup rather than waiting for a potentially
 * long sync operation.</p>
 */
@Component
@Profile("!specs & !test")
public class WorkspaceStartupListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStartupListener.class);

    private final WorkspaceProvisioningService provisioningService;
    private final WorkspaceActivationService workspaceActivationService;
    private final WorkspaceRepository workspaceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkspaceStartupListener(
        WorkspaceProvisioningService provisioningService,
        WorkspaceActivationService workspaceActivationService,
        WorkspaceRepository workspaceRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.provisioningService = provisioningService;
        this.workspaceActivationService = workspaceActivationService;
        this.workspaceRepository = workspaceRepository;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Phase 1: Provision workspaces (creates/loads workspace entities)
        // Each provider bootstrap is isolated so a failure in one doesn't block others
        log.info("Starting workspace provisioning");
        try {
            provisioningService.bootstrapDefaultPatWorkspace();
        } catch (Exception e) {
            log.error("GitHub PAT workspace bootstrap failed, continuing with other providers", e);
        }
        try {
            provisioningService.bootstrapDefaultGitLabPatWorkspace();
        } catch (Exception e) {
            log.error("GitLab PAT workspace bootstrap failed, continuing with other providers", e);
        }
        try {
            provisioningService.ensureGitHubAppInstallations();
        } catch (Exception e) {
            log.error("GitHub App installation bootstrap failed", e);
        }

        // Phase 2: Signal that workspaces are initialized
        // Installation consumer can now start - it only needs workspaces to exist
        int workspaceCount = (int) workspaceRepository.count();
        log.info("Workspaces initialized: count={}", workspaceCount);
        eventPublisher.publishEvent(new WorkspacesInitializedEvent(workspaceCount));

        // Phase 3: Activate workspaces (run full sync - this can take a while)
        workspaceActivationService.activateAllWorkspaces();
    }
}
