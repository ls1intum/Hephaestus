package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import org.kohsuke.github.GHRepositorySelection;

/**
 * Shared test fixtures for workspace-related integration tests.
 * <p>
 * These builders create unsaved entities. Call the appropriate repository's save()
 * method to persist them.
 */
public final class WorkspaceTestFixtures {

    private WorkspaceTestFixtures() {}

    /**
     * Creates an unsaved GitHub App installation workspace with standard defaults.
     */
    public static WorkspaceBuilder installationWorkspace(long installationId, String login) {
        return new WorkspaceBuilder(installationId, login);
    }

    /**
     * Creates an unsaved repository monitor.
     */
    public static RepositoryToMonitor repositoryMonitor(Workspace workspace, String nameWithOwner) {
        RepositoryToMonitor monitor = new RepositoryToMonitor();
        monitor.setWorkspace(workspace);
        monitor.setNameWithOwner(nameWithOwner);
        return monitor;
    }

    /**
     * Fluent builder for test workspaces.
     */
    public static class WorkspaceBuilder {

        private final Workspace workspace;

        private WorkspaceBuilder(long installationId, String login) {
            workspace = new Workspace();
            workspace.setWorkspaceSlug("ws-install-" + installationId);
            workspace.setDisplayName("Workspace " + installationId);
            workspace.setAccountLogin(login);
            workspace.setAccountType(AccountType.ORG);
            workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
            workspace.setInstallationId(installationId);
            workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
            workspace.setIsPubliclyViewable(false);
        }

        public WorkspaceBuilder withStatus(Workspace.WorkspaceStatus status) {
            workspace.setStatus(status);
            return this;
        }

        public WorkspaceBuilder withAccountType(AccountType type) {
            workspace.setAccountType(type);
            return this;
        }

        public WorkspaceBuilder withRepositorySelection(GHRepositorySelection selection) {
            workspace.setGithubRepositorySelection(selection);
            return this;
        }

        public WorkspaceBuilder withSlug(String slug) {
            workspace.setWorkspaceSlug(slug);
            return this;
        }

        public Workspace build() {
            return workspace;
        }
    }
}
