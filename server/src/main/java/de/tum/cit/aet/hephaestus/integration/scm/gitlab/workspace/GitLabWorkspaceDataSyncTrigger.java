package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceDataSyncTrigger;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * GitLab-side adapter for {@link WorkspaceDataSyncTrigger}.
 *
 * <p>{@code syncAllRepositories} delegates to
 * {@link GitLabWorkspaceInitializationService#initialize(Workspace)} followed by
 * {@link GitLabWorkspaceInitializationService#syncFullData(Workspace)} — both phases are
 * required for a complete startup activation. The single-target path is a no-op today
 * because GitLab sync is workspace-scoped, not repository-scoped (a single
 * {@code RepositoryToMonitor} addition is covered by webhook-driven sync).
 *
 * <p>Only registered when the GitLab init service is on the classpath — under the
 * webhook-only runtime role the GitLab stack is gated off, so the trigger simply doesn't
 * exist.
 */
@Component
@ConditionalOnBean(GitLabWorkspaceInitializationService.class)
public class GitLabWorkspaceDataSyncTrigger implements WorkspaceDataSyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(GitLabWorkspaceDataSyncTrigger.class);

    private final GitLabWorkspaceInitializationService gitLabInitService;
    private final WorkspaceRepository workspaceRepository;

    public GitLabWorkspaceDataSyncTrigger(
        GitLabWorkspaceInitializationService gitLabInitService,
        WorkspaceRepository workspaceRepository
    ) {
        this.gitLabInitService = gitLabInitService;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void syncAllRepositories(long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            log.warn("Skipped GitLab data sync: reason=workspaceNotFound, workspaceId={}", workspaceId);
            return;
        }
        gitLabInitService.initialize(workspace);
        gitLabInitService.syncFullData(workspace);
    }

    @Override
    public void syncSingleSyncTarget(long syncTargetId) {
        // GitLab sync is workspace-scoped; per-target sync is unused. Webhook-driven sync
        // keeps incremental data fresh. Intentional no-op rather than a misleading partial.
        log.debug("Skipped GitLab single-target sync: reason=notSupported, syncTargetId={}", syncTargetId);
    }
}
