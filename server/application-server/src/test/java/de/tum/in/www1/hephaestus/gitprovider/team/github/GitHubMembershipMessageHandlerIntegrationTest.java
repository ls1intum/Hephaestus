package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubMembershipEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
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

/**
 * Integration tests for GitHubMembershipMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Membership Message Handler")
class GitHubMembershipMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubMembershipMessageHandler handler;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization testOrganization;
    private Team testTeam;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setGithubId(215361191L);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
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

    private void createTestTeam(Long teamId, String name) {
        testTeam = new Team();
        testTeam.setId(teamId);
        testTeam.setName(name);
        testTeam.setOrganization(testOrganization.getLogin());
        testTeam = teamRepository.save(testTeam);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.MEMBERSHIP);
    }

    @Test
    @DisplayName("Should handle member added event")
    void shouldHandleMemberAddedEvent() throws Exception {
        // Given
        GitHubMembershipEventDTO event = loadPayload("membership.added");
        createTestTeam(event.team().id(), event.team().name());

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("added");
    }

    @Test
    @DisplayName("Should handle member removed event")
    void shouldHandleMemberRemovedEvent() throws Exception {
        // Given
        GitHubMembershipEventDTO event = loadPayload("membership.removed");
        createTestTeam(event.team().id(), event.team().name());

        // Add a user to remove
        if (event.member() != null) {
            User member = new User();
            member.setId(event.member().id());
            member.setLogin(event.member().login());
            member.setAvatarUrl(event.member().avatarUrl());
            member.setCreatedAt(Instant.now());
            member.setUpdatedAt(Instant.now());
            userRepository.save(member);
        }

        // When
        handler.handleEvent(event);

        // Then - handler processes without error
        assertThat(event.action()).isEqualTo("removed");
    }

    private GitHubMembershipEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubMembershipEventDTO.class);
    }
}
