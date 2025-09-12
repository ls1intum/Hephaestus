package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Installation Repositories Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubInstallationRepositoriesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationRepositoriesMessageHandler handler;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("installation_repositories.added upserts repos")
    void shouldUpsertOnRepositoriesAdded(
        @GitHubPayload("installation_repositories.added") GHEventPayload.InstallationRepositories payload
    ) {
        assertThat(payload.getAction()).isEqualTo("added");
        assertThat(payload.getRepositoriesAdded()).isNotEmpty();

        var added = payload.getRepositoriesAdded();
        var names = added.stream().map(r -> r.getFullName()).toList();
        names.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isEmpty());

        // When
        handler.handleEvent(payload);

        // Then
        names.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isPresent());
    }

    @Test
    @DisplayName("installation_repositories.removed deletes repos")
    void shouldDeleteOnRepositoriesRemoved(
        @GitHubPayload("installation_repositories.removed") GHEventPayload.InstallationRepositories payload
    ) {
        assertThat(payload.getAction()).isEqualTo("removed");
        assertThat(payload.getRepositoriesRemoved()).isNotEmpty();

        // Seed repos first
        payload
            .getRepositoriesRemoved()
            .forEach(r ->
                repositorySyncService.upsertFromInstallationPayload(
                    r.getId(),
                    r.getFullName(),
                    r.getName(),
                    r.isPrivate()
                )
            );

        payload
            .getRepositoriesRemoved()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isPresent());

        // When
        handler.handleEvent(payload);

        // Then
        payload
            .getRepositoriesRemoved()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isEmpty());
    }
}
