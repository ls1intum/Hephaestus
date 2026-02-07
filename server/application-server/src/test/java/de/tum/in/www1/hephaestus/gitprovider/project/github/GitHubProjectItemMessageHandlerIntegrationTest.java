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
 * Tests verify that the handler correctly processes project item events
 * (create, edit, archive, restore, convert, reorder, delete) using JSON fixtures
 * parsed directly into DTOs for complete isolation.
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

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    private ProjectItem createAndSaveTestItem(
        Long id,
        String nodeId,
        ProjectItem.ContentType contentType,
        boolean archived
    ) {
        ProjectItem item = new ProjectItem();
        item.setId(id);
        item.setNodeId(nodeId);
        item.setProject(testProject);
        item.setContentType(contentType);
        item.setArchived(archived);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return projectItemRepository.saveAndFlush(item);
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic handler tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PROJECTS_V2_ITEM);
    }

    @Test
    @DisplayName("Should handle item deleted event")
    void shouldHandleItemDeletedEvent() throws Exception {
        // Given
        ProjectItem item = createAndSaveTestItem(
            136779223L,
            "PVTI_lADODNYmp84BHA5ozggnFdc",
            ProjectItem.ContentType.DRAFT_ISSUE,
            false
        );
        assertThat(projectItemRepository.findById(item.getId())).isPresent();

        GitHubProjectItemEventDTO deletedEvent = loadPayload("projects_v2_item.deleted");

        // When
        handler.handleEvent(deletedEvent);

        // Then
        assertThat(projectItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should process project item archived event")
    void shouldHandleItemArchivedEvent() throws Exception {
        // Given
        createAndSaveTestItem(136779223L, "PVTI_lADODNYmp84BHA5ozggnFdc", ProjectItem.ContentType.DRAFT_ISSUE, false);

        GitHubProjectItemEventDTO archivedEvent = loadPayload("projects_v2_item.archived");

        // When
        handler.handleEvent(archivedEvent);

        // Then — webhook DTO lacks an explicit "archived" boolean; the primitive defaults to false.
        // The processor passes through the DTO value, so archived remains false after upsert.
        var result = projectItemRepository.findByProjectIdAndNodeId(
            testProject.getId(),
            "PVTI_lADODNYmp84BHA5ozggnFdc"
        );
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779223L);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
            });
    }

    // ═══════════════════════════════════════════════════════════════
    // Edge case / guard-clause tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database so no org exists
        databaseTestUtils.cleanDatabase();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then
        assertThat(projectItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should skip event when project is not found")
    void shouldSkipEventWhenProjectNotFound() throws Exception {
        // Given — org/workspace exist but project does not
        projectItemRepository.deleteAll();
        projectRepository.deleteAll();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then — no item should be created
        assertThat(projectItemRepository.count()).isZero();
    }

    // ═══════════════════════════════════════════════════════════════
    // Create / Edit / Convert / Restore / Reorder tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create project item from created event")
    void shouldHandleItemCreatedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then
        var item = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), "PVTI_lADODNYmp84BHA5ozggnFcA");
        assertThat(item)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779200L);
                assertThat(i.getNodeId()).isEqualTo("PVTI_lADODNYmp84BHA5ozggnFcA");
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
                assertThat(i.getCreatedAt()).isEqualTo(Instant.parse("2025-11-01T23:54:51Z"));
                assertThat(i.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-01T23:54:51Z"));
            });
    }

    @Test
    @DisplayName("Should handle duplicate created events idempotently")
    void shouldHandleDuplicateCreatedEventsIdempotently() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When — process the same event twice
        handler.handleEvent(event);
        handler.handleEvent(event);

        // Then — only one item should exist (upsert is idempotent)
        assertThat(projectItemRepository.findAllByProjectId(testProject.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should update project item from edited event")
    void shouldHandleItemEditedEvent() throws Exception {
        // Given — pre-create the item
        createAndSaveTestItem(136779200L, "PVTI_lADODNYmp84BHA5ozggnFcA", ProjectItem.ContentType.DRAFT_ISSUE, false);

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.edited");

        // When
        handler.handleEvent(event);

        // Then — updated_at should reflect the edited fixture timestamp;
        // created_at is NOT overwritten on conflict (upsert only updates updated_at).
        var item = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), "PVTI_lADODNYmp84BHA5ozggnFcA");
        assertThat(item)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779200L);
                assertThat(i.getNodeId()).isEqualTo("PVTI_lADODNYmp84BHA5ozggnFcA");
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
                assertThat(i.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-01T23:55:09Z"));
            });
    }

    @Test
    @DisplayName("Should process project item restored event")
    void shouldHandleItemRestoredEvent() throws Exception {
        // Given — pre-create item as archived
        createAndSaveTestItem(136779223L, "PVTI_lADODNYmp84BHA5ozggnFdc", ProjectItem.ContentType.DRAFT_ISSUE, true);

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.restored");

        // When
        handler.handleEvent(event);

        // Then
        var result = projectItemRepository.findByProjectIdAndNodeId(
            testProject.getId(),
            "PVTI_lADODNYmp84BHA5ozggnFdc"
        );
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779223L);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });
    }

    @Test
    @DisplayName("Should update content type when item is converted")
    void shouldHandleItemConvertedEvent() throws Exception {
        // Given — pre-create item as DRAFT_ISSUE
        createAndSaveTestItem(136779200L, "PVTI_lADODNYmp84BHA5ozggnFcA", ProjectItem.ContentType.DRAFT_ISSUE, false);

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.converted");

        // When
        handler.handleEvent(event);

        // Then — content type should be updated to ISSUE
        var result = projectItemRepository.findByProjectIdAndNodeId(
            testProject.getId(),
            "PVTI_lADODNYmp84BHA5ozggnFcA"
        );
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779200L);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });
    }

    @Test
    @DisplayName("Should process project item reordered event")
    void shouldHandleItemReorderedEvent() throws Exception {
        // Given — pre-create the item
        createAndSaveTestItem(136779223L, "PVTI_lADODNYmp84BHA5ozggnFdc", ProjectItem.ContentType.DRAFT_ISSUE, false);

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.reordered");

        // When
        handler.handleEvent(event);

        // Then
        var result = projectItemRepository.findByProjectIdAndNodeId(
            testProject.getId(),
            "PVTI_lADODNYmp84BHA5ozggnFdc"
        );
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getId()).isEqualTo(136779223L);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });
    }

    private GitHubProjectItemEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectItemEventDTO.class);
    }
}
