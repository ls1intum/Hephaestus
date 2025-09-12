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

@DisplayName("GitHub Installation Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubInstallationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationMessageHandler handler;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should parse installation.created payload")
    void shouldParseInstallationCreatedPayload(
        @GitHubPayload("installation.created") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("created");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();
        assertThat(payload.getRawRepositories().get(0).getFullName()).contains("/");

        // Precondition
        var fullNames = payload.getRawRepositories().stream().map(r -> r.getFullName()).toList();
        fullNames.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isEmpty());

        // When
        handler.handleEvent(payload);

        // Then
        fullNames.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isPresent());
    }

    @Test
    @DisplayName("Should parse installation.deleted payload")
    void shouldParseInstallationDeletedPayload(
        @GitHubPayload("installation.deleted") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("deleted");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();

        // Seed: simulate repos existing from prior installation without REST calls
        payload
            .getRawRepositories()
            .forEach(r ->
                repositorySyncService.upsertFromInstallationPayload(
                    r.getId(),
                    r.getFullName(),
                    r.getName(),
                    r.isPrivate()
                )
            );
        payload
            .getRawRepositories()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isPresent());

        // When
        handler.handleEvent(payload);

        // Then: repositories should be removed now
        payload
            .getRawRepositories()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isEmpty());
    }

    @Test
    @DisplayName("Should parse installation.suspend payload")
    void shouldParseInstallationSuspendPayload(
        @GitHubPayload("installation.suspend") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("suspend");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getSender()).isNotNull();
    }

    @Test
    @DisplayName("Should parse installation.unsuspend payload")
    void shouldParseInstallationUnsuspendPayload(
        @GitHubPayload("installation.unsuspend") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("unsuspend");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getSender()).isNotNull();
    }
}
