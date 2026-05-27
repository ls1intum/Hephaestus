package de.tum.cit.aet.hephaestus.integration.github.installation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.github.installation.dto.GitHubInstallationRepositoriesEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for GitHubInstallationRepositoriesMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Installation Repositories Message Handler")
class GitHubInstallationRepositoriesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationRepositoriesMessageHandler handler;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    private void setupTestWorkspace(Long installationId, String login) {
        // Create GitHub provider
        GitProvider gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization
        Organization org = new Organization();
        org.setNativeId(215361191L);
        org.setLogin(login);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org.setHtmlUrl("https://github.com/" + login);
        org.setProvider(gitProvider);
        org = organizationRepository.save(org);

        // Create workspace + matching ACTIVE GitHub App Connection in one shot.
        // Provider classification + installation id come from the Connection registry,
        // not from legacy Workspace columns.
        WorkspaceTestFixtures.WorkspaceBuilder builder = WorkspaceTestFixtures.installationWorkspace(
            installationId,
            login
        )
            .withSlug(login.toLowerCase())
            .withAccountType(AccountType.ORG);
        Workspace saved = WorkspaceTestFixtures.persistInstallationWorkspace(
            workspaceRepository,
            connectionRepository,
            builder,
            installationId
        );
        saved.setIsPubliclyViewable(true);
        saved.setOrganization(org);
        workspaceRepository.save(saved);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.key().eventType()).isEqualTo("installation.installation_repositories");
    }

    @Test
    @DisplayName("Should handle added repositories event")
    void shouldHandleAddedEvent() throws Exception {
        // Given
        GitHubInstallationRepositoriesEventDTO event = loadPayload("installation_repositories.added");
        setupTestWorkspace(event.installation().id(), "HephaestusTest");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("added");
    }

    @Test
    @DisplayName("Should handle removed repositories event")
    void shouldHandleRemovedEvent() throws Exception {
        // Given
        GitHubInstallationRepositoriesEventDTO event = loadPayload("installation_repositories.removed");
        setupTestWorkspace(event.installation().id(), "HephaestusTest");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("removed");
    }

    @Test
    @DisplayName("Should handle null installation gracefully")
    void shouldHandleNullInstallationGracefully() {
        // Given - event with null installation
        GitHubInstallationRepositoriesEventDTO event = new GitHubInstallationRepositoriesEventDTO(
            "added",
            null,
            null,
            null,
            null
        );

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs warning but doesn't crash
    }

    @Test
    @DisplayName("Should handle null repository lists gracefully")
    void shouldHandleNullRepositoryListsGracefully() throws Exception {
        // Given - load a valid event and create with null lists
        GitHubInstallationRepositoriesEventDTO baseEvent = loadPayload("installation_repositories.added");
        GitHubInstallationRepositoriesEventDTO event = new GitHubInstallationRepositoriesEventDTO(
            "added",
            baseEvent.installation(),
            null, // repositoriesAdded null
            null, // repositoriesRemoved null
            baseEvent.sender()
        );
        setupTestWorkspace(baseEvent.installation().id(), "HephaestusTest");

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler handles null lists gracefully
    }

    private GitHubInstallationRepositoriesEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationRepositoriesEventDTO.class);
    }
}
