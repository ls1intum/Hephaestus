package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdate;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdateRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateEventDTO;
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
 * Integration tests for GitHubProjectStatusUpdateMessageHandler.
 * <p>
 * Verifies persistence, field-level correctness, domain event publishing,
 * and edge case handling for status update webhook events.
 */
@DisplayName("GitHub Project Status Update Message Handler")
@Import(GitHubProjectStatusUpdateMessageHandlerIntegrationTest.TestStatusUpdateEventListener.class)
class GitHubProjectStatusUpdateMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // Fixture values from projects_v2_status_update.created.json
    private static final Long FIXTURE_STATUS_UPDATE_ID = 160033L;
    private static final String FIXTURE_STATUS_UPDATE_NODE_ID = "PVTSU_lADODNYmp84BHA5ozgACcSE";
    private static final String FIXTURE_PROJECT_NODE_ID = "PVT_kwDODNYmp84BHA5o";
    private static final String FIXTURE_CREATED_BODY = "Initial status update for payload fixtures";
    private static final String FIXTURE_EDITED_BODY = "Updated status highlighting risks";

    @Autowired
    private GitHubProjectStatusUpdateMessageHandler handler;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectStatusUpdateRepository statusUpdateRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStatusUpdateEventListener eventListener;

    private Organization testOrganization;
    private Project testProject;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PROJECTS_V2_STATUS_UPDATE);
    }

    @Test
    @DisplayName("Should handle status update created event with all fields and publish domain event")
    void shouldHandleStatusUpdateCreatedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");

        handler.handleEvent(createdEvent);

        assertThat(statusUpdateRepository.findByNodeId(createdEvent.statusUpdate().nodeId()))
            .isPresent()
            .get()
            .satisfies(update -> {
                assertThat(update.getId()).isEqualTo(FIXTURE_STATUS_UPDATE_ID);
                assertThat(update.getNodeId()).isEqualTo(FIXTURE_STATUS_UPDATE_NODE_ID);
                assertThat(update.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(update.getBody()).isEqualTo(FIXTURE_CREATED_BODY);
                assertThat(update.getStatus()).isEqualTo(ProjectStatusUpdate.Status.ON_TRACK);
                assertThat(update.getStartDate()).isNull();
                assertThat(update.getTargetDate()).isNull();
            });

        // Verify domain event
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getCreatedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    @Test
    @DisplayName("Should handle status update edited event and verify fields changed")
    void shouldHandleStatusUpdateEditedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");
        handler.handleEvent(createdEvent);
        eventListener.clear();

        GitHubProjectStatusUpdateEventDTO editedEvent = loadPayload("projects_v2_status_update.edited");
        handler.handleEvent(editedEvent);

        assertThat(statusUpdateRepository.findByNodeId(editedEvent.statusUpdate().nodeId()))
            .isPresent()
            .get()
            .satisfies(update -> {
                assertThat(update.getBody()).isEqualTo(FIXTURE_EDITED_BODY);
                assertThat(update.getStatus()).isEqualTo(ProjectStatusUpdate.Status.AT_RISK);
                assertThat(update.getUpdatedAt()).isEqualTo(Instant.parse("2025-11-02T00:05:24Z"));
            });

        // Verify ProjectStatusUpdateUpdated event (not Created)
        assertThat(eventListener.getCreatedEvents()).isEmpty();
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
        assertThat(eventListener.getUpdatedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
    }

    @Test
    @DisplayName("Should handle status update deleted event and publish domain event")
    void shouldHandleStatusUpdateDeletedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");
        handler.handleEvent(createdEvent);
        eventListener.clear();

        GitHubProjectStatusUpdateEventDTO deletedEvent = loadPayload("projects_v2_status_update.deleted");
        handler.handleEvent(deletedEvent);

        assertThat(statusUpdateRepository.findByNodeId(deletedEvent.statusUpdate().nodeId())).isEmpty();

        // Verify domain event
        assertThat(eventListener.getDeletedEvents()).hasSize(1);
        assertThat(eventListener.getDeletedEvents().getFirst().projectId()).isEqualTo(testProject.getId());
        assertThat(eventListener.getDeletedEvents().getFirst().statusUpdateId()).isEqualTo(FIXTURE_STATUS_UPDATE_ID);
    }

    @Test
    @DisplayName("Should skip event when organization is not found")
    void shouldSkipEventWhenOrganizationNotFound() throws Exception {
        // Given - clean database so no org exists
        databaseTestUtils.cleanDatabase();
        eventListener.clear();

        GitHubProjectStatusUpdateEventDTO event = loadPayload("projects_v2_status_update.created");

        // When
        handler.handleEvent(event);

        // Then — no status update should be created
        assertThat(statusUpdateRepository.count()).isZero();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should skip event when project is not found")
    void shouldSkipEventWhenProjectNotFound() throws Exception {
        // Given — org/workspace exist but project does not
        statusUpdateRepository.deleteAll();
        projectRepository.deleteAll();
        eventListener.clear();

        GitHubProjectStatusUpdateEventDTO event = loadPayload("projects_v2_status_update.created");

        // When
        handler.handleEvent(event);

        // Then — no status update should be created
        assertThat(statusUpdateRepository.count()).isZero();
        assertThat(eventListener.getCreatedEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should handle duplicate created events idempotently")
    void shouldHandleDuplicateCreatedEventsIdempotently() throws Exception {
        // Given
        GitHubProjectStatusUpdateEventDTO event = loadPayload("projects_v2_status_update.created");

        // When — process the same event twice
        handler.handleEvent(event);
        handler.handleEvent(event);

        // Then — only one status update should exist
        assertThat(statusUpdateRepository.findByNodeId(event.statusUpdate().nodeId())).isPresent();

        // First call publishes Created, second publishes Updated
        assertThat(eventListener.getCreatedEvents()).hasSize(1);
        assertThat(eventListener.getUpdatedEvents()).hasSize(1);
    }

    private void setupTestData() {
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setProviderId(215361191L);
        testOrganization.setLogin("HephaestusTest");
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        testOrganization.setHtmlUrl("https://github.com/HephaestusTest");
        testOrganization = organizationRepository.save(testOrganization);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(testOrganization);
        workspace.setAccountLogin("HephaestusTest");
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);

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

    private GitHubProjectStatusUpdateEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubProjectStatusUpdateEventDTO.class);
    }

    /**
     * Test event listener that captures status update domain events for assertion.
     */
    @Component
    static class TestStatusUpdateEventListener {

        private final List<DomainEvent.ProjectStatusUpdateCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectStatusUpdateUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.ProjectStatusUpdateDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.ProjectStatusUpdateCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.ProjectStatusUpdateUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.ProjectStatusUpdateDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.ProjectStatusUpdateCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.ProjectStatusUpdateUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.ProjectStatusUpdateDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
