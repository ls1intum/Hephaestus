package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Integration tests for GitHubProjectItemMessageHandler.
 * <p>
 * Tests verify that the handler correctly processes project item events
 * (create, edit, archive, restore, convert, reorder, delete) using JSON fixtures
 * parsed directly into DTOs for complete isolation.
 */
@DisplayName("GitHub Project Item Message Handler")
@Import(GitHubProjectItemMessageHandlerIntegrationTest.TestProjectItemEventListener.class)
class GitHubProjectItemMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Fixture values from projects_v2_item.created.json
    private static final Long FIXTURE_ITEM_ID = 136779200L;
    private static final String FIXTURE_ITEM_NODE_ID = "PVTI_lADODNYmp84BHA5ozggnFcA";
    private static final Instant FIXTURE_ITEM_CREATED_AT = Instant.parse("2025-11-01T23:54:51Z");
    private static final Instant FIXTURE_ITEM_UPDATED_AT = Instant.parse("2025-11-01T23:54:51Z");

    // Fixture values from projects_v2_item.archived/restored/reordered/deleted.json (second item)
    private static final Long FIXTURE_ITEM2_ID = 136779223L;
    private static final String FIXTURE_ITEM2_NODE_ID = "PVTI_lADODNYmp84BHA5ozggnFdc";

    @Autowired
    private GitHubProjectItemMessageHandler handler;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectItemRepository projectItemRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestProjectItemEventListener eventListener;

    private GitProvider testGitProvider;
    private Organization testOrganization;
    private Project testProject;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create GitProvider for GitHub
        testGitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization matching the fixture data
        testOrganization = new Organization();
        testOrganization.setNativeId(215361191L);
        testOrganization.setProvider(testGitProvider);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        testOrganization.setHtmlUrl("https://github.com/HephaestusTest");
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
        testProject.setNativeId(18615912L);
        testProject.setProvider(testGitProvider);
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
        item.setNativeId(id);
        item.setProvider(testGitProvider);
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
            FIXTURE_ITEM2_ID,
            FIXTURE_ITEM2_NODE_ID,
            ProjectItem.ContentType.DRAFT_ISSUE,
            false
        );
        assertThat(projectItemRepository.findById(item.getId())).isPresent();
        eventListener.clear();

        GitHubProjectItemEventDTO deletedEvent = loadPayload("projects_v2_item.deleted");

        // When
        handler.handleEvent(deletedEvent);

        // Then
        assertThat(projectItemRepository.findById(item.getId())).isEmpty();

        // Verify ProjectItemDeleted domain event
        assertThat(eventListener.getDeletedEvents()).hasSize(1);
        assertThat(eventListener.getDeletedEvents().getFirst().itemId()).isEqualTo(item.getId());
    }

    @Test
    @DisplayName("Should process project item archived event")
    void shouldHandleItemArchivedEvent() throws Exception {
        // Given
        createAndSaveTestItem(FIXTURE_ITEM2_ID, FIXTURE_ITEM2_NODE_ID, ProjectItem.ContentType.DRAFT_ISSUE, false);
        eventListener.clear();

        GitHubProjectItemEventDTO archivedEvent = loadPayload("projects_v2_item.archived");

        // When
        handler.handleEvent(archivedEvent);

        // Then — processArchived() forces archived=true on the DTO before upserting,
        // compensating for GitHub's webhook payload using "archived_at" instead of "archived".
        var result = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM2_NODE_ID);
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM2_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isTrue();
            });

        // Verify domain events: processArchived() calls process() first (Updated since item exists),
        // then publishes ProjectItemArchived
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getArchivedEvents()).hasSize(1);
        assertThat(eventListener.getArchivedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    // Edge case / guard-clause tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database so no org exists
        databaseTestUtils.cleanDatabase();
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then - no item should be created and no events published
        assertThat(projectItemRepository.count()).isZero();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should skip event when project is not found")
    void shouldSkipEventWhenProjectNotFound() throws Exception {
        // Given — org/workspace exist but project does not
        projectItemRepository.deleteAll();
        projectRepository.deleteAll();
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then — no item should be created and no events published
        assertThat(projectItemRepository.count()).isZero();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // Create / Edit / Convert / Restore / Reorder tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create project item from created event and publish ProjectItemCreated event")
    void shouldHandleItemCreatedEvent() throws Exception {
        // Given
        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.created");

        // When
        handler.handleEvent(event);

        // Then
        var item = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM_NODE_ID);
        assertThat(item)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM_ID);
                assertThat(i.getNodeId()).isEqualTo(FIXTURE_ITEM_NODE_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
                assertThat(i.getCreatedAt()).isEqualTo(FIXTURE_ITEM_CREATED_AT);
                assertThat(i.getUpdatedAt()).isEqualTo(FIXTURE_ITEM_UPDATED_AT);
            });

        // Verify ProjectItemCreated domain event
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getCreatedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
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

        // First call publishes ProjectItemCreated, second publishes ProjectItemUpdated
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should update project item from edited event and publish ProjectItemUpdated event")
    void shouldHandleItemEditedEvent() throws Exception {
        // Given — pre-create the item
        createAndSaveTestItem(FIXTURE_ITEM_ID, FIXTURE_ITEM_NODE_ID, ProjectItem.ContentType.DRAFT_ISSUE, false);
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.edited");

        // When
        handler.handleEvent(event);

        // Then — updated_at should reflect the edited fixture timestamp;
        // created_at is NOT overwritten on conflict (upsert only updates updated_at).
        var item = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM_NODE_ID);
        assertThat(item)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM_ID);
                assertThat(i.getNodeId()).isEqualTo(FIXTURE_ITEM_NODE_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
                assertThat(i.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-01T23:55:09Z"));
            });

        // Verify ProjectItemUpdated domain event (not Created, since item already existed)
        assertThat(eventListener.getCreatedEvents()).isEmpty();
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getUpdatedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    @Test
    @DisplayName("Should process project item restored event and publish ProjectItemRestored event")
    void shouldHandleItemRestoredEvent() throws Exception {
        // Given — pre-create item as archived
        createAndSaveTestItem(FIXTURE_ITEM2_ID, FIXTURE_ITEM2_NODE_ID, ProjectItem.ContentType.DRAFT_ISSUE, true);
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.restored");

        // When
        handler.handleEvent(event);

        // Then
        var result = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM2_NODE_ID);
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM2_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });

        // Verify domain events: processRestored() calls process() first (Updated since item exists),
        // then publishes ProjectItemRestored
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getRestoredEvents()).hasSize(1);
        assertThat(eventListener.getRestoredEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    @Test
    @DisplayName("Should update content type when item is converted and publish ProjectItemConverted event")
    void shouldHandleItemConvertedEvent() throws Exception {
        // Given — pre-create item as DRAFT_ISSUE
        createAndSaveTestItem(FIXTURE_ITEM_ID, FIXTURE_ITEM_NODE_ID, ProjectItem.ContentType.DRAFT_ISSUE, false);
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.converted");

        // When
        handler.handleEvent(event);

        // Then — content type should be updated to ISSUE
        var result = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM_NODE_ID);
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });

        // Verify domain events: processConverted() calls process() first (Updated since item exists),
        // then publishes ProjectItemConverted
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getConvertedEvents()).hasSize(1);
        assertThat(eventListener.getConvertedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    @Test
    @DisplayName("Should process project item reordered event and publish ProjectItemReordered event")
    void shouldHandleItemReorderedEvent() throws Exception {
        // Given — pre-create the item
        createAndSaveTestItem(FIXTURE_ITEM2_ID, FIXTURE_ITEM2_NODE_ID, ProjectItem.ContentType.DRAFT_ISSUE, false);
        eventListener.clear();

        GitHubProjectItemEventDTO event = loadPayload("projects_v2_item.reordered");

        // When
        handler.handleEvent(event);

        // Then
        var result = projectItemRepository.findByProjectIdAndNodeId(testProject.getId(), FIXTURE_ITEM2_NODE_ID);
        assertThat(result)
            .isPresent()
            .get()
            .satisfies(i -> {
                assertThat(i.getNativeId()).isEqualTo(FIXTURE_ITEM2_ID);
                assertThat(i.getContentType()).isEqualTo(ProjectItem.ContentType.DRAFT_ISSUE);
                assertThat(i.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(i.isArchived()).isFalse();
            });

        // Verify domain events: processReordered() calls process() first (Updated since item exists),
        // then publishes ProjectItemReordered
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getReorderedEvents()).hasSize(1);
        assertThat(eventListener.getReorderedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    private GitHubProjectItemEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectItemEventDTO.class);
    }

    /**
     * Test event listener that captures project item domain events for assertion.
     */
    @Component
    static class TestProjectItemEventListener {

        private final List<DomainEvent.ProjectItemCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemArchived> archivedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemRestored> restoredEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemDeleted> deletedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemConverted> convertedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectItemReordered> reorderedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.ProjectItemCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.ProjectItemUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onArchived(DomainEvent.ProjectItemArchived event) {
            archivedEvents.add(event);
        }

        @EventListener
        public void onRestored(DomainEvent.ProjectItemRestored event) {
            restoredEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.ProjectItemDeleted event) {
            deletedEvents.add(event);
        }

        @EventListener
        public void onConverted(DomainEvent.ProjectItemConverted event) {
            convertedEvents.add(event);
        }

        @EventListener
        public void onReordered(DomainEvent.ProjectItemReordered event) {
            reorderedEvents.add(event);
        }

        public List<DomainEvent.ProjectItemCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.ProjectItemUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.ProjectItemArchived> getArchivedEvents() {
            return new ArrayList<>(archivedEvents);
        }

        public List<DomainEvent.ProjectItemRestored> getRestoredEvents() {
            return new ArrayList<>(restoredEvents);
        }

        public List<DomainEvent.ProjectItemDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public List<DomainEvent.ProjectItemConverted> getConvertedEvents() {
            return new ArrayList<>(convertedEvents);
        }

        public List<DomainEvent.ProjectItemReordered> getReorderedEvents() {
            return new ArrayList<>(reorderedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            archivedEvents.clear();
            restoredEvents.clear();
            deletedEvents.clear();
            convertedEvents.clear();
            reorderedEvents.clear();
        }
    }
}
