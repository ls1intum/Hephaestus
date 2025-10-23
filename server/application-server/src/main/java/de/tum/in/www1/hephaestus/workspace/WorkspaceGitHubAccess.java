package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubClientProvider;
import java.io.IOException;
import java.util.Optional;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Helper for resolving a workspace-scoped Hub4J client along with the corresponding GitHub organization.
 */
@Component
public class WorkspaceGitHubAccess {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceGitHubAccess.class);

    private final WorkspaceRepository workspaceRepository;
    private final GitHubClientProvider gitHubClientProvider;

    public WorkspaceGitHubAccess(WorkspaceRepository workspaceRepository, GitHubClientProvider gitHubClientProvider) {
        this.workspaceRepository = workspaceRepository;
        this.gitHubClientProvider = gitHubClientProvider;
    }

    public Optional<Context> resolve(Workspace workspace) {
        if (workspace == null) {
            return Optional.empty();
        }

        if (workspace.getId() == null) {
            logger.warn("Cannot resolve GitHub context for transient workspace without id");
            return Optional.empty();
        }

        Workspace persisted = workspaceRepository.findById(workspace.getId()).orElse(workspace);

        String login = persisted.getAccountLogin();
        if (login == null || login.isBlank()) {
            logger.warn("Workspace {} has no GitHub account login configured", persisted.getId());
            return Optional.empty();
        }

        try {
            GitHub gitHub = gitHubClientProvider.forWorkspace(persisted.getId());
            // TODO: Support personal accounts when PAT workspaces target user-owned repositories.
            GHOrganization ghOrganization = gitHub.getOrganization(login);
            return Optional.of(new Context(persisted, gitHub, ghOrganization));
        } catch (IOException e) {
            logger.warn(
                "Failed to build GitHub context for workspace {} using login {}: {}",
                persisted.getId(),
                login,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    public record Context(Workspace workspace, GitHub gitHub, GHOrganization ghOrganization) {}
}
