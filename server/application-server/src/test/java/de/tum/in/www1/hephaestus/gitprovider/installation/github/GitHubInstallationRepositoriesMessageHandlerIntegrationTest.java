package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler.GitHubMessageDomain;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.dto.GitHubInstallationRepositoriesEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubInstallationRepositoriesMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Installation Repositories Message Handler")
@Transactional
class GitHubInstallationRepositoriesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationRepositoriesMessageHandler handler;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    private void setupTestWorkspace(Long installationId, String login) {
        // Create organization
        Organization org = new Organization();
        org.setId(215361191L);
        org.setGithubId(215361191L);
        org.setLogin(login);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(login.toLowerCase());
        workspace.setDisplayName(login);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin(login);
        workspace.setAccountType(AccountType.ORG);
        workspace.setInstallationId(installationId);
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("installation_repositories");
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

    @Test
    @DisplayName("Should return INSTALLATION domain")
    void shouldReturnInstallationDomain() {
        assertThat(handler.getDomain()).isEqualTo(GitHubMessageDomain.INSTALLATION);
    }

    private GitHubInstallationRepositoriesEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationRepositoriesEventDTO.class);
    }
}
