package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceInitializationHook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * GitLab-side {@link WorkspaceInitializationHook} — kicks off async group discovery
 * + webhook registration via {@link GitLabWorkspaceInitializationService} when a new
 * GitLab PAT workspace is created. {@link ConditionalOnBean} gates this hook on the
 * GitLab init service existing.
 */
@Component
@ConditionalOnBean(GitLabWorkspaceInitializationService.class)
public class GitLabWorkspaceInitializationHook implements WorkspaceInitializationHook {

    private final GitLabWorkspaceInitializationService gitLabInit;

    public GitLabWorkspaceInitializationHook(GitLabWorkspaceInitializationService gitLabInit) {
        this.gitLabInit = gitLabInit;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void initializeAsync(long workspaceId) {
        gitLabInit.initializeAsync(workspaceId);
    }
}
