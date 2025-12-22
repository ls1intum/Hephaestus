package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
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
 * Integration tests for GitHubTeamMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Team Message Handler")
@Transactional
class GitHubTeamMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubTeamMessageHandler handler;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        testOrganization = organizationRepository.save(testOrganization);

        // Create repository (needed for team-repo events)
        Repository repo = new Repository();
        repo.setId(1000663383L);
        repo.setName("TestRepository");
        repo.setNameWithOwner("HephaestusTest/TestRepository");
        repo.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
        repo.setVisibility(Repository.Visibility.PUBLIC);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(testOrganization);
        repositoryRepository.save(repo);

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
        assertThat(handler.getEventKey()).isEqualTo("team");
    }

    @Test
    @DisplayName("Should create team on created event")
    void shouldCreateTeamOnCreatedEvent() throws Exception {
        // Given
        GitHubTeamEventDTO event = loadPayload("team.org.created");

        // Verify team doesn't exist initially
        assertThat(teamRepository.findById(event.team().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(teamRepository.findById(event.team().id()))
            .isPresent()
            .get()
            .satisfies(team -> {
                assertThat(team.getId()).isEqualTo(event.team().id());
                assertThat(team.getName()).isEqualTo(event.team().name());
                assertThat(team.getDescription()).isEqualTo(event.team().description());
            });
    }

    @Test
    @DisplayName("Should update team on edited event")
    void shouldUpdateTeamOnEditedEvent() throws Exception {
        // Given - first create the team
        GitHubTeamEventDTO createEvent = loadPayload("team.org.created");
        handler.handleEvent(createEvent);

        // Load edited event
        GitHubTeamEventDTO editEvent = loadPayload("team.org.edited");

        // When
        handler.handleEvent(editEvent);

        // Then
        assertThat(teamRepository.findById(editEvent.team().id()))
            .isPresent()
            .get()
            .satisfies(team -> {
                assertThat(team.getName()).isEqualTo(editEvent.team().name());
                assertThat(team.getDescription()).isEqualTo(editEvent.team().description());
            });
    }

    @Test
    @DisplayName("Should delete team on deleted event")
    void shouldDeleteTeamOnDeletedEvent() throws Exception {
        // Given - first create the team
        GitHubTeamEventDTO createEvent = loadPayload("team.org.created");
        handler.handleEvent(createEvent);

        // Verify it exists
        assertThat(teamRepository.findById(createEvent.team().id())).isPresent();

        // Load deleted event
        GitHubTeamEventDTO deleteEvent = loadPayload("team.org.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(teamRepository.findById(deleteEvent.team().id())).isEmpty();
    }

    private GitHubTeamEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubTeamEventDTO.class);
    }
}
