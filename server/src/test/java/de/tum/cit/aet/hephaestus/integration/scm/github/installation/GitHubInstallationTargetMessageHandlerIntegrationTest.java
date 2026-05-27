package de.tum.cit.aet.hephaestus.integration.scm.github.installation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.github.installation.dto.GitHubInstallationTargetEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
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
 * Integration tests for GitHubInstallationTargetMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Installation Target Message Handler")
class GitHubInstallationTargetMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationTargetMessageHandler handler;

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
        assertThat(handler.key().eventType()).isEqualTo("installation.installation_target");
    }

    @Test
    @DisplayName("Should handle renamed event")
    void shouldHandleRenamedEvent() throws Exception {
        // Given - use installation_target.json which has action: "renamed"
        GitHubInstallationTargetEventDTO event = loadPayload("installation_target");
        setupTestWorkspace(event.installation().id(), "OldName");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("renamed");
    }

    @Test
    @DisplayName("Should handle renamed event for user account")
    void shouldHandleRenamedEventForUser() throws Exception {
        // Given - use installation_target_user.json for user account rename
        GitHubInstallationTargetEventDTO event = loadPayload("installation_target_user");
        setupTestWorkspace(event.installation().id(), "SoloMaintainer");

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("renamed");
        assertThat(event.targetType()).isEqualTo("User");
    }

    @Test
    @DisplayName("Should handle null account gracefully")
    void shouldHandleNullAccountGracefully() {
        // Given - event with null account
        GitHubInstallationTargetEventDTO event = new GitHubInstallationTargetEventDTO(
            "renamed",
            null, // installation
            null, // account
            "Organization",
            null, // changes
            null // sender
        );

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs warning but doesn't crash
    }

    @Test
    @DisplayName("Should handle unknown action gracefully")
    void shouldHandleUnknownActionGracefully() throws Exception {
        // Given - load a valid event and create with unknown action
        GitHubInstallationTargetEventDTO baseEvent = loadPayload("installation_target");
        GitHubInstallationTargetEventDTO event = new GitHubInstallationTargetEventDTO(
            "unknown_action",
            baseEvent.installation(),
            baseEvent.account(),
            baseEvent.targetType(),
            baseEvent.changes(),
            baseEvent.sender()
        );
        setupTestWorkspace(baseEvent.installation().id(), "OldName");

        // When - should not throw
        handler.handleEvent(event);
        // Then - handler logs debug message for unhandled action
    }

    private GitHubInstallationTargetEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubInstallationTargetEventDTO.class);
    }
}
