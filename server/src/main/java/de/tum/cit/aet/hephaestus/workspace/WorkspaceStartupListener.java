package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
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
 * <p>Phases 1-3 run on {@code applicationTaskExecutor} so {@link ApplicationReadyEvent} dispatch
 * returns immediately. Without the hop the main thread was blocked on synchronous GitHub-App
 * REST calls for ~7 s after Spring reported "Started Application", delaying readiness for any
 * downstream subscribers and the perceived dev-loop start time.
 *
 * <p>Ordering: {@link Order#value()} is intentionally {@link Ordered#LOWEST_PRECEDENCE}; the NATS
 * consumer's {@code @Order(1)} {@code init()} runs first to establish the connection before
 * {@link WorkspacesInitializedEvent} could fire from the worker thread.
 */
@Component
@Profile("!specs & !test")
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
public class WorkspaceStartupListener {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStartupListener.class);

    private final WorkspaceProvisioningService provisioningService;
    private final WorkspaceActivationService workspaceActivationService;
    private final WorkspaceRepository workspaceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AsyncTaskExecutor applicationTaskExecutor;

    public WorkspaceStartupListener(
        WorkspaceProvisioningService provisioningService,
        WorkspaceActivationService workspaceActivationService,
        WorkspaceRepository workspaceRepository,
        ApplicationEventPublisher eventPublisher,
        AsyncTaskExecutor applicationTaskExecutor
    ) {
        this.provisioningService = provisioningService;
        this.workspaceActivationService = workspaceActivationService;
        this.workspaceRepository = workspaceRepository;
        this.eventPublisher = eventPublisher;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        applicationTaskExecutor.execute(this::provisionAndActivate);
    }

    private void provisionAndActivate() {
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
