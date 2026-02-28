package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
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
 * Integration tests for GitHubProjectMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs using JSON fixtures for complete isolation.
 * Verifies persistence, field-level correctness, and domain event publishing.
 */
@DisplayName("GitHub Project Message Handler")
@Import(GitHubProjectMessageHandlerIntegrationTest.TestProjectEventListener.class)
class GitHubProjectMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Fixture values from projects_v2.created.json
    private static final Long FIXTURE_PROJECT_ID = 18615912L;
    private static final String FIXTURE_PROJECT_NODE_ID = "PVT_kwDODNYmp84BHA5o";
    private static final String FIXTURE_PROJECT_TITLE = "Payload Fixture Project 2025-11-01";
    private static final int FIXTURE_PROJECT_NUMBER = 1;
    private static final Instant FIXTURE_CREATED_AT = Instant.parse("2025-11-01T23:53:46Z");

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

    @Autowired
    private TestProjectEventListener eventListener;

    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization matching the fixture data
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setProviderId(215361191L);
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
    @DisplayName("Should handle project created event with all fields and publish ProjectCreated event")
    void shouldHandleProjectCreatedEvent() throws Exception {
        // Given
        GitHubProjectEventDTO event = loadPayload("projects_v2.created");

        // When
        handler.handleEvent(event);

        // Then - project should be created with correct field values
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
                assertThat(project.getId()).isEqualTo(FIXTURE_PROJECT_ID);
                assertThat(project.getNodeId()).isEqualTo(FIXTURE_PROJECT_NODE_ID);
                assertThat(project.getTitle()).isEqualTo(FIXTURE_PROJECT_TITLE);
                assertThat(project.getNumber()).isEqualTo(FIXTURE_PROJECT_NUMBER);
                assertThat(project.getOwnerType()).isEqualTo(Project.OwnerType.ORGANIZATION);
                assertThat(project.getOwnerId()).isEqualTo(testOrganization.getId());
                assertThat(project.isClosed()).isFalse();
                assertThat(project.isPublic()).isFalse();
                assertThat(project.getShortDescription()).isNull();
                assertThat(project.getCreatedAt()).isEqualTo(FIXTURE_CREATED_AT);
            });

        // Verify domain event
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getCreatedEvents().getFirst().project().id()).isEqualTo(FIXTURE_PROJECT_ID);
    }

    @Test
    @DisplayName("Should handle project edited event and verify shortDescription is updated")
    void shouldHandleProjectEditedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);
        eventListener.clear();

        // Load and process edited event
        GitHubProjectEventDTO editEvent = loadPayload("projects_v2.edited");

        // When
        handler.handleEvent(editEvent);

        // Then - project should be updated with the edited short description
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                editEvent.project().number()
            )
        )
            .isPresent()
            .get()
            .satisfies(project -> {
                assertThat(project.getShortDescription()).isEqualTo("Fixtures for webhook payloads");
                assertThat(project.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-01T23:54:01Z"));
            });

        // Verify ProjectUpdated domain event (not ProjectCreated, since project already existed)
        assertThat(eventListener.getCreatedEvents()).isEmpty();
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle project closed event and publish ProjectClosed event")
    void shouldHandleProjectClosedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);
        eventListener.clear();

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

        // Verify ProjectClosed domain event
        assertThat(eventListener.getClosedEvents()).hasSize(1);
        assertThat(eventListener.getClosedEvents().getFirst().project().id()).isEqualTo(FIXTURE_PROJECT_ID);
    }

    @Test
    @DisplayName("Should handle project reopened event and publish ProjectReopened event")
    void shouldHandleProjectReopenedEvent() throws Exception {
        // Given - first create and close a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);

        GitHubProjectEventDTO closedEvent = loadPayload("projects_v2.closed");
        handler.handleEvent(closedEvent);
        eventListener.clear();

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

        // Verify ProjectReopened domain event
        assertThat(eventListener.getReopenedEvents()).hasSize(1);
        assertThat(eventListener.getReopenedEvents().getFirst().project().id()).isEqualTo(FIXTURE_PROJECT_ID);
    }

    @Test
    @DisplayName("Should handle project deleted event and publish ProjectDeleted event")
    void shouldHandleProjectDeletedEvent() throws Exception {
        // Given - first create a project
        GitHubProjectEventDTO createEvent = loadPayload("projects_v2.created");
        handler.handleEvent(createEvent);
        eventListener.clear();

        // Verify project exists
        Long projectId = createEvent.project().getDatabaseId();
        assertThat(projectRepository.findById(projectId)).isPresent();

        // Load and process deleted event
        GitHubProjectEventDTO deletedEvent = loadPayload("projects_v2.deleted");

        // When
        handler.handleEvent(deletedEvent);

        // Then - project should be deleted
        assertThat(projectRepository.findById(projectId)).isEmpty();

        // Verify ProjectDeleted domain event
        assertThat(eventListener.getDeletedEvents()).hasSize(1);
        assertThat(eventListener.getDeletedEvents().getFirst().projectId()).isEqualTo(FIXTURE_PROJECT_ID);
        assertThat(eventListener.getDeletedEvents().getFirst().projectTitle()).isEqualTo(FIXTURE_PROJECT_TITLE);
    }

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database and don't set up test data
        databaseTestUtils.cleanDatabase();
        eventListener.clear();

        GitHubProjectEventDTO event = loadPayload("projects_v2.created");

        // When
        handler.handleEvent(event);

        // Then - no project should be created and no events published
        assertThat(projectRepository.count()).isZero();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should handle duplicate created events idempotently")
    void shouldHandleDuplicateCreatedEventsIdempotently() throws Exception {
        // Given
        GitHubProjectEventDTO event = loadPayload("projects_v2.created");

        // When — process the same event twice
        handler.handleEvent(event);
        handler.handleEvent(event);

        // Then — only one project should exist (upsert is idempotent)
        assertThat(
            projectRepository.findByOwnerTypeAndOwnerIdAndNumber(
                Project.OwnerType.ORGANIZATION,
                testOrganization.getId(),
                event.project().number()
            )
        ).isPresent();
        assertThat(projectRepository.count()).isOne();

        // First call publishes ProjectCreated, second publishes ProjectUpdated
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
    }

    private GitHubProjectEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectEventDTO.class);
    }

    /**
     * Test event listener that captures project domain events for assertion.
     */
    @Component
    static class TestProjectEventListener {

        private final List<DomainEvent.ProjectCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.ProjectCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.ProjectUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.ProjectClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.ProjectReopened event) {
            reopenedEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.ProjectDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.ProjectCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.ProjectUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.ProjectClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.ProjectReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public List<DomainEvent.ProjectDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
            deletedEvents.clear();
        }
    }
}
