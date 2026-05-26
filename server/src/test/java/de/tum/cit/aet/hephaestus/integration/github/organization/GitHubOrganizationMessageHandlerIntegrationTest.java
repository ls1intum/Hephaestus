package de.tum.cit.aet.hephaestus.integration.github.organization;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.github.common.GitHubEventType;
import de.tum.cit.aet.hephaestus.integration.github.organization.dto.GitHubOrganizationEventDTO;
import de.tum.cit.aet.hephaestus.integration.scm.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
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
 * Integration tests for GitHubOrganizationMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Organization Message Handler")
class GitHubOrganizationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubOrganizationMessageHandler handler;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization testOrganization;
    private GitProvider githubProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitHub provider (required by the handler)
        githubProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization
        testOrganization = new Organization();
        testOrganization.setNativeId(215361191L);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        testOrganization.setHtmlUrl("https://github.com/HephaestusTest");
        testOrganization.setProvider(githubProvider);
        testOrganization = organizationRepository.save(testOrganization);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(testOrganization);
        workspace.setAccountLogin("HephaestusTest");
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.key().eventType()).isEqualTo("organization.organization");
    }

    @Test
    @DisplayName("Should handle member added event")
    void shouldHandleMemberAddedEvent() throws Exception {
        // Given
        GitHubOrganizationEventDTO event = loadPayload("organization.member_added");

        // When
        handler.handleEvent(event);

        // Then - organization should still exist
        assertThat(organizationRepository.findById(testOrganization.getId())).isPresent();
        // User should be created if membership contains user info
        if (event.membership() != null && event.membership().user() != null) {
            assertThat(
                userRepository.findByNativeIdAndProviderId(event.membership().user().id(), githubProvider.getId())
            ).isPresent();
        }
    }

    @Test
    @DisplayName("Should handle member removed event")
    void shouldHandleMemberRemovedEvent() throws Exception {
        // Given - first add a member
        GitHubOrganizationEventDTO addEvent = loadPayload("organization.member_added");
        if (addEvent.membership() != null && addEvent.membership().user() != null) {
            User member = new User();
            member.setNativeId(addEvent.membership().user().id());
            member.setProvider(githubProvider);
            member.setLogin(addEvent.membership().user().login());
            member.setAvatarUrl(addEvent.membership().user().avatarUrl());
            member.setCreatedAt(Instant.now());
            member.setUpdatedAt(Instant.now());
            userRepository.save(member);
        }

        // Load remove event
        GitHubOrganizationEventDTO removeEvent = loadPayload("organization.member_removed");

        // When
        handler.handleEvent(removeEvent);

        // Then - handler processes without error
        assertThat(organizationRepository.findById(testOrganization.getId())).isPresent();
    }

    @Test
    @DisplayName("Should handle organization renamed event")
    void shouldHandleRenamedEvent() throws Exception {
        // Given
        GitHubOrganizationEventDTO event = loadPayload("organization.renamed");

        // When
        handler.handleEvent(event);

        // Then - organization should reflect new name
        assertThat(organizationRepository.findById(testOrganization.getId()))
            .isPresent()
            .get()
            .satisfies(org -> {
                // The handler should update the organization login
                assertThat(org.getLogin()).isEqualTo(event.organization().login());
            });
    }

    private GitHubOrganizationEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubOrganizationEventDTO.class);
    }
}
