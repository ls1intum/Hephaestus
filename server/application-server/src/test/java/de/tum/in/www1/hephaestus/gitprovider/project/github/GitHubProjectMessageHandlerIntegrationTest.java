package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectEventDTO;
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
 * Integration tests for GitHubProjectMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 */
@DisplayName("GitHub Project Message Handler")
class GitHubProjectMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubProjectMessageHandler handler;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

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
        // Create organization matching the fixture data
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setGithubId(215361191L);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        testOrganization = organizationRepository.save(testOrganization);

        // Create workspace for scope resolution
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
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PROJECTS_V2);
    }

    @Test
    @DisplayName("Should handle project created event")
    void shouldHandleProjectCreatedEvent() throws Exception {
        // Given
        GitHubProjectEventDTO event = loadPayload("projects_v2.created");

        // When
        handler.handleEvent(event);

        // Then - project should be created
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                event.project().number()
            )
        )
            .isPresent()
            .get()
            .satisfies(project -> {
                assertThat(project.getTitle()).isEqualTo(event.project().title());
                assertThat(project.getNodeId()).isEqualTo(event.project().nodeId());
                assertThat(project.isClosed()).isFalse();
            });
    }

    @Test
    @DisplayName("Should handle project edited event")
    void shouldHandleProjectEditedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);

        // Load and process edited event
        GitHubProjectEventDTO editEvent = loadPayload("projects_v2.edited");

        // When
        handler.handleEvent(editEvent);

        // Then - project should be updated
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                editEvent.project().number()
            )
        ).isPresent();
    }

    @Test
    @DisplayName("Should handle project closed event")
    void shouldHandleProjectClosedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);

        // Load and process closed event
        GitHubProjectEventDTO closedEvent = loadPayload("projects_v2.closed");

        // When
        handler.handleEvent(closedEvent);

        // Then - project should be marked as closed
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                closedEvent.project().number()
            )
        )
            .isPresent()
            .get()
            .satisfies(project -> assertThat(project.isClosed()).isTrue());
    }

    @Test
    @DisplayName("Should handle project reopened event")
    void shouldHandleProjectReopenedEvent() throws Exception {
        // Given - first create and close a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);

        GitHubProjectEventDTO closedEvent = loadPayload("projects_v2.closed");
        handler.handleEvent(closedEvent);

        // Load and process reopened event
        GitHubProjectEventDTO reopenedEvent = loadPayload("projects_v2.reopened");

        // When
        handler.handleEvent(reopenedEvent);

        // Then - project should be reopened
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                reopenedEvent.project().number()
            )
        )
            .isPresent()
            .get()
            .satisfies(project -> assertThat(project.isClosed()).isFalse());
    }

    @Test
    @DisplayName("Should handle project deleted event")
    void shouldHandleProjectDeletedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);

        // Verify project exists
        Long projectId = createEvent.project().getDatabaseId();
        assertThat(projectRepository.findById(projectId)).isPresent();

        // Load and process deleted event
        GitHubProjectEventDTO deletedEvent = loadPayload("projects_v2.deleted");

        // When
        handler.handleEvent(deletedEvent);

        // Then - project should be deleted
        assertThat(projectRepository.findById(projectId)).isEmpty();
    }

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database and don't set up test data
        databaseTestUtils.cleanDatabase();

        GitHubProjectEventDTO event = loadPayload("projects_v2.created");

        // When
        handler.handleEvent(event);

        // Then - no project should be created
        assertThat(projectRepository.count()).isZero();
    }

    private GitHubProjectEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectEventDTO.class);
    }
}
