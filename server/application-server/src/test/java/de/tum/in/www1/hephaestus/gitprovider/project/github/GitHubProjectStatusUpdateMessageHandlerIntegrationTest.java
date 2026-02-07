package de.tum.in.www1.hephaestus.gitprovider.project.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdateRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.github.dto.GitHubProjectStatusUpdateEventDTO;
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

@DisplayName("GitHub Project Status Update Message Handler")
class GitHubProjectStatusUpdateMessageHandlerIntegrationTest extends BaseIntegrationTest {

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

    private Organization testOrganization;
    private Project testProject;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    @Test
    @DisplayName("Should return correct event type")
    void shouldReturnCorrectEventType() {
        assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PROJECTS_V2_STATUS_UPDATE);
    }

    @Test
    @DisplayName("Should handle status update created event")
    void shouldHandleStatusUpdateCreatedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");

        handler.handleEvent(createdEvent);

        assertThat(statusUpdateRepository.findByNodeId(createdEvent.statusUpdate().nodeId()))
            .isPresent()
            .get()
            .satisfies(update -> {
                assertThat(update.getProject().getId()).isEqualTo(testProject.getId());
                assertThat(update.getBody()).isEqualTo(createdEvent.statusUpdate().body());
            });
    }

    @Test
    @DisplayName("Should handle status update edited event")
    void shouldHandleStatusUpdateEditedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");
        handler.handleEvent(createdEvent);

        GitHubProjectStatusUpdateEventDTO editedEvent = loadPayload("projects_v2_status_update.edited");
        handler.handleEvent(editedEvent);

        assertThat(statusUpdateRepository.findByNodeId(editedEvent.statusUpdate().nodeId()))
            .isPresent()
            .get()
            .satisfies(update -> {
                assertThat(update.getBody()).isEqualTo(editedEvent.statusUpdate().body());
                assertThat(update.getStatus().name()).isEqualTo(editedEvent.statusUpdate().status());
            });
    }

    @Test
    @DisplayName("Should handle status update deleted event")
    void shouldHandleStatusUpdateDeletedEvent() throws Exception {
        GitHubProjectStatusUpdateEventDTO createdEvent = loadPayload("projects_v2_status_update.created");
        handler.handleEvent(createdEvent);

        GitHubProjectStatusUpdateEventDTO deletedEvent = loadPayload("projects_v2_status_update.deleted");
        handler.handleEvent(deletedEvent);

        assertThat(statusUpdateRepository.findByNodeId(deletedEvent.statusUpdate().nodeId())).isEmpty();
    }

    private void setupTestData() {
        testOrganization = new Organization();
        testOrganization.setId(215361191L);
        testOrganization.setGithubId(215361191L);
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
}
