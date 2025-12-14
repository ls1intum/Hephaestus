package de.tum.in.www1.hephaestus.gitprovider.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.testconfig.DatabaseTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Live integration guard to ensure we can still enumerate installation repositories when repository_selection=all.
 */
class GitHubLiveInstallationRepositorySelectionIntegrationTest extends BaseGitHubLiveIntegrationTest {

    @Autowired
    private GitHubAppTokenService gitHubAppTokenService;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private DatabaseTestUtils databaseTestUtils;

    @BeforeEach
    void clean() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("List repositories for installation (ALL selection) and upsert minimal snapshot")
    void shouldListRepositoriesForInstallationWithAllSelection() throws Exception {
        long installationId = githubInstallationId();
        // Use the installation client (with installation token) to list repositories.
        // The App JWT alone cannot list installation repositories - you need the installation token.
        var installationClient = gitHubAppTokenService.clientForInstallation(installationId);
        var repositories = installationClient.getInstallation().listRepositories().withPageSize(10).toList();

        assertThat(repositories).isNotEmpty();

        var repo = repositories.getFirst();
        var persisted = repositorySyncService.upsertFromInstallationPayload(
            repo.getId(),
            repo.getFullName(),
            repo.getName(),
            repo.isPrivate()
        );

        assertThat(persisted.getNameWithOwner()).isEqualTo(repo.getFullName());
        assertThat(persisted.getVisibility()).isEqualTo(
            repo.isPrivate() ? Repository.Visibility.PRIVATE : Repository.Visibility.PUBLIC
        );
    }
}
