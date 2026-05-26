package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositorySelection;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

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
     * Persist a GitHub App installation workspace AND its backing {@code Connection} row.
     * The legacy {@code Workspace.installation_id} column is still populated for
     * compatibility with code paths that haven't migrated yet; the runtime sources
     * provider classification + installation id from the Connection.
     *
     * <p>Returns the saved Workspace; use the repositories the caller passed in to
     * find the Connection if needed.
     */
    public static Workspace persistInstallationWorkspace(WorkspaceRepository workspaceRepository,
                                                         ConnectionRepository connectionRepository,
                                                         WorkspaceBuilder builder,
                                                         long installationId) {
        Workspace saved = workspaceRepository.save(builder.build());
        ConnectionConfig.GitHubAppConfig cfg = new ConnectionConfig.GitHubAppConfig(
            installationId, saved.getAccountLogin(), /* serverUrl */ null, Set.of());
        Connection connection = new Connection(saved, IntegrationKind.GITHUB, Long.toString(installationId), cfg);
        connection.setDisplayName(saved.getAccountLogin());
        ReflectionTestUtils.setField(connection, "state", IntegrationState.ACTIVE);
        connectionRepository.save(connection);
        return saved;
    }

    /**
     * Persist a GitLab PAT workspace AND its backing {@code Connection} row. PAT
     * itself is NOT persisted on the Connection (skipped in unit-test fixtures); use
     * {@code ConnectionService.rotateBearerToken} in tests that need a real token blob.
     */
    public static Workspace persistGitLabWorkspace(WorkspaceRepository workspaceRepository,
                                                   ConnectionRepository connectionRepository,
                                                   GitLabWorkspaceBuilder builder,
                                                   String serverUrl) {
        Workspace saved = workspaceRepository.save(builder.build());
        ConnectionConfig.GitLabConfig cfg = new ConnectionConfig.GitLabConfig(
            serverUrl, null, null,
            ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT, Set.of());
        Connection connection = new Connection(saved, IntegrationKind.GITLAB, serverUrl, cfg);
        connection.setDisplayName(saved.getAccountLogin());
        ReflectionTestUtils.setField(connection, "state", IntegrationState.ACTIVE);
        connectionRepository.save(connection);
        return saved;
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
     * Creates an unsaved GitLab PAT workspace with standard defaults.
     */
    public static GitLabWorkspaceBuilder gitLabPatWorkspace(String groupPath) {
        return new GitLabWorkspaceBuilder(groupPath);
    }

    /**
     * Fluent builder for GitHub App installation test workspaces.
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

        public WorkspaceBuilder withRepositorySelection(RepositorySelection selection) {
            workspace.setRepositorySelection(selection);
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

    /**
     * Fluent builder for GitLab PAT test workspaces.
     */
    public static class GitLabWorkspaceBuilder {

        private final Workspace workspace;

        private GitLabWorkspaceBuilder(String groupPath) {
            workspace = new Workspace();
            workspace.setWorkspaceSlug("ws-gitlab-" + groupPath.replace("/", "-"));
            workspace.setDisplayName("GitLab " + groupPath);
            workspace.setAccountLogin(groupPath);
            workspace.setAccountType(AccountType.ORG);
            workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
            workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
            workspace.setIsPubliclyViewable(false);
        }

        public GitLabWorkspaceBuilder withServerUrl(String url) {
            workspace.setServerUrl(url);
            return this;
        }

        public GitLabWorkspaceBuilder withToken(String token) {
            workspace.setPersonalAccessToken(token);
            return this;
        }

        public GitLabWorkspaceBuilder withSlug(String slug) {
            workspace.setWorkspaceSlug(slug);
            return this;
        }

        public GitLabWorkspaceBuilder withStatus(Workspace.WorkspaceStatus status) {
            workspace.setStatus(status);
            return this;
        }

        public Workspace build() {
            return workspace;
        }
    }
}
