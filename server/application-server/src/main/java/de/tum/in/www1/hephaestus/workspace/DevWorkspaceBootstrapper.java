package de.tum.in.www1.hephaestus.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-bootstraps a dev workspace on fresh database for local development convenience.
 * Only active in dev/local profiles. Creates a single workspace with minimal configuration
 * if the database has zero workspaces and the feature flag is enabled.
 */
@Component
@Profile({ "dev", "local" })
public class DevWorkspaceBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(DevWorkspaceBootstrapper.class);

    private final WorkspaceProperties workspaceProperties;
    private final WorkspaceRepository workspaceRepository;

    public DevWorkspaceBootstrapper(WorkspaceProperties workspaceProperties, WorkspaceRepository workspaceRepository) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Runs after migrations complete but before schedulers start.
     * Checks if bootstrap should run and creates the dev workspace if needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        if (!workspaceProperties.getDev().isAutoBootstrapFirstWorkspace()) {
            log.info(
                "Dev workspace auto-bootstrap is disabled (hephaestus.workspace.dev.auto-bootstrap-first-workspace=false)"
            );
            return;
        }

        long workspaceCount = workspaceRepository.count();
        if (workspaceCount > 0) {
            log.info(
                "Dev workspace auto-bootstrap skipped: {} workspace(s) already exist (idempotent)",
                workspaceCount
            );
            return;
        }

        log.info("Creating dev workspace (fresh database detected, auto-bootstrap enabled)");

        Workspace devWorkspace = new Workspace();
        devWorkspace.setSlug("dev-local");
        devWorkspace.setDisplayName("Dev Workspace");
        devWorkspace.setAccountLogin("dev-local");
        devWorkspace.setAccountType(AccountType.USER);
        devWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        devWorkspace.setIsPubliclyViewable(false);
        devWorkspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);

        workspaceRepository.save(devWorkspace);

        log.info(
            "Dev workspace created successfully: {{\"slug\": \"{}\", \"displayName\": \"{}\", \"accountType\": \"{}\", \"status\": \"{}\"}}",
            devWorkspace.getSlug(),
            devWorkspace.getDisplayName(),
            devWorkspace.getAccountType(),
            devWorkspace.getStatus()
        );
    }
}
