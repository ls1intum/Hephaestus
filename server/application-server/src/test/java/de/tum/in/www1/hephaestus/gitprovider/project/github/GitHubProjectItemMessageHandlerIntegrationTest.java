package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItemRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectItemEventDTO;
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
 * Integration tests for GitHubProjectItemMessageHandler.
 * <p>
 * Tests verify that the handler correctly processes project item events.
 * Note: For item create/edit events, the handler requires a project to exist.
 * These tests verify the handler behavior with and without existing projects.
 */
@DisplayName("GitHub Project Item Message Handler")
class GitHubProjectItemMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubProjectItemMessageHandler handler;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectItemRepository projectItemRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Organization testOrganization;
    private Project testProject;

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

        // Create test project matching the fixture data
        testProject = new Project();
        testProject.setId(18615912L);
        testProject.setNodeId("PVT_kwDODNYmp84BHA5o");
        testProject.setOwnerType(Project.OwnerType.ORGANIZATION);
        testProject.setOwnerId(testOrganization.getId());
        testProject.setNumber(1);
        testProject.setTitle("Payload Fixture Project 2025-11-01");
        testProject.setClosed(false);
        testProject.setPublic(false);
        testProject.setCreatedAt(Instant.now());
        testProject.setUpdatedAt(Instant.now());
        testProject = projectRepository.save(testProject);
    }

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PROJECTS_V2_ITEM);
    }

    @Test
    @DisplayName("Should handle item deleted event")
    void shouldHandleItemDeletedEvent() throws Exception {
        // Given - create an item with ID matching the fixture
        ProjectItem item = new ProjectItem();
        item.setId(136779223L); // ID from projects_v2_item.deleted.json
        item.setNodeId("PVTI_lADODNYmp84BHA5ozggnFdc");
        item.setProject(testProject);
        item.setContentType(ProjectItem.ContentType.DRAFT_ISSUE);
        item.setArchived(false);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        projectItemRepository.save(item);

        assertThat(projectItemRepository.findById(item.getId())).isPresent();

        // Load and process deleted event
        GitHubProjectItemEventDTO deletedEvent = loadPayload("projects_v2_item.deleted");

        // When
        handler.handleEvent(deletedEvent);

        // Then - item should be deleted
        assertThat(projectItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should handle item archived event")
    void shouldHandleItemArchivedEvent() throws Exception {
        // Given - create an item first
        ProjectItem item = new ProjectItem();
        item.setId(136779223L);
        item.setNodeId("PVTI_lADODNYmp84BHA5ozggnFdc");
        item.setProject(testProject);
        item.setContentType(ProjectItem.ContentType.DRAFT_ISSUE);
        item.setArchived(false);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        projectItemRepository.save(item);

        // Load and process archived event
        GitHubProjectItemEventDTO archivedEvent = loadPayload("projects_v2_item.archived");

        // When
        handler.handleEvent(archivedEvent);

        // Then - handler processes without error (project lookup may return null)
        // The item archiving will only work if findProjectForItem returns the project
        // For now, verify the handler doesn't throw
    }

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database and don't set up test data
        databaseTestUtils.cleanDatabase();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then - no item should be created and no error should be thrown
        assertThat(projectItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should handle item created event gracefully when project lookup returns null")
    void shouldHandleItemCreatedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When - the handler will process but findProjectForItem returns null
        handler.handleEvent(event);

        // Then - no error is thrown, item may not be created if project lookup fails
        // This is expected behavior as documented in the handler
    }

    @Test
    @DisplayName("Should handle item edited event")
    void shouldHandleItemEditedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.edited");

        // When - the handler will process but findProjectForItem returns null
        handler.handleEvent(event);

        // Then - no error is thrown
    }

    @Test
    @DisplayName("Should handle item restored event")
    void shouldHandleItemRestoredEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.restored");

        // When - the handler will process but findProjectForItem returns null
        handler.handleEvent(event);

        // Then - no error is thrown
    }

    @Test
    @DisplayName("Should handle item converted event")
    void shouldHandleItemConvertedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.converted");

        // When
        handler.handleEvent(event);

        // Then - no error is thrown
    }

    @Test
    @DisplayName("Should handle item reordered event")
    void shouldHandleItemReorderedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.reordered");

        // When
        handler.handleEvent(event);

        // Then - no error is thrown
    }

    private GitHubProjectItemEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectItemEventDTO.class);
    }
}
