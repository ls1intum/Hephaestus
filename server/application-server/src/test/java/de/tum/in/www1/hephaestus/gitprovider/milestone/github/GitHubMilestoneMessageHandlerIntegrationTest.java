package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubMilestoneMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 */
@DisplayName("GitHub Milestone Message Handler")
@Transactional
class GitHubMilestoneMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubMilestoneMessageHandler handler;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization
        Organization org = new Organization();
        org.setId(215361191L);
        org.setGithubId(215361191L);
        org.setLogin("HephaestusTest");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/215361191?v=4");
        org = organizationRepository.save(org);

        // Create repository
        testRepository = new Repository();
        testRepository.setId(1000663383L);
        testRepository.setName("TestRepository");
        testRepository.setNameWithOwner("HephaestusTest/TestRepository");
        testRepository.setHtmlUrl("https://github.com/HephaestusTest/TestRepository");
        testRepository.setVisibility(Repository.Visibility.PUBLIC);
        testRepository.setDefaultBranch("main");
        testRepository.setCreatedAt(Instant.now());
        testRepository.setUpdatedAt(Instant.now());
        testRepository.setPushedAt(Instant.now());
        testRepository.setOrganization(org);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("hephaestus-test");
        workspace.setDisplayName("Hephaestus Test");
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        workspace.setIsPubliclyViewable(true);
        workspace.setOrganization(org);
        workspace.setAccountLogin("HephaestusTest");
        workspace.setAccountType(AccountType.ORG);
        workspaceRepository.save(workspace);
    }

    @Test
    @DisplayName("Should return correct event key")
    void shouldReturnCorrectEventKey() {
        assertThat(handler.getEventKey()).isEqualTo("milestone");
    }

    @Test
    @DisplayName("Should persist milestones on creation events")
    void shouldPersistMilestoneOnCreatedEvent() throws Exception {
        // Given
        GitHubMilestoneEventDTO event = loadPayload("milestone.created");

        // Verify milestone doesn't exist initially
        assertThat(milestoneRepository.findById(event.milestone().id())).isEmpty();

        // When
        handler.handleEvent(event);

        // Then
        assertThat(milestoneRepository.findById(event.milestone().id()))
            .isPresent()
            .get()
            .satisfies(milestone -> {
                assertThat(milestone.getId()).isEqualTo(event.milestone().id());
                assertThat(milestone.getTitle()).isEqualTo(event.milestone().title());
                assertThat(milestone.getDescription()).isEqualTo(event.milestone().description());
                assertThat(milestone.getState()).isEqualTo(Milestone.State.OPEN);
            });
    }

    @Test
    @DisplayName("Should update milestone details on edit events")
    void shouldUpdateMilestoneOnEditedEvent() throws Exception {
        // Given - first create the milestone
        GitHubMilestoneEventDTO createEvent = loadPayload("milestone.created");
        handler.handleEvent(createEvent);

        // Load edited event
        GitHubMilestoneEventDTO editEvent = loadPayload("milestone.edited");

        // When
        handler.handleEvent(editEvent);

        // Then
        assertThat(milestoneRepository.findById(editEvent.milestone().id()))
            .isPresent()
            .get()
            .satisfies(milestone -> {
                assertThat(milestone.getTitle()).isEqualTo(editEvent.milestone().title());
                assertThat(milestone.getDescription()).isEqualTo(editEvent.milestone().description());
            });
    }

    @Test
    @DisplayName("Should close milestone on closed event")
    void shouldCloseMilestoneOnClosedEvent() throws Exception {
        // Given - first create the milestone
        GitHubMilestoneEventDTO createEvent = loadPayload("milestone.created");
        handler.handleEvent(createEvent);

        // Load closed event
        GitHubMilestoneEventDTO closedEvent = loadPayload("milestone.closed");

        // When
        handler.handleEvent(closedEvent);

        // Then
        assertThat(milestoneRepository.findById(closedEvent.milestone().id()))
            .isPresent()
            .get()
            .satisfies(milestone -> {
                assertThat(milestone.getState()).isEqualTo(Milestone.State.CLOSED);
            });
    }

    @Test
    @DisplayName("Should delete milestone on deleted event")
    void shouldDeleteMilestoneOnDeletedEvent() throws Exception {
        // Given - first create the milestone
        GitHubMilestoneEventDTO createEvent = loadPayload("milestone.created");
        handler.handleEvent(createEvent);

        // Verify it exists
        assertThat(milestoneRepository.findById(createEvent.milestone().id())).isPresent();

        // Load deleted event
        GitHubMilestoneEventDTO deleteEvent = loadPayload("milestone.deleted");

        // When
        handler.handleEvent(deleteEvent);

        // Then
        assertThat(milestoneRepository.findById(deleteEvent.milestone().id())).isEmpty();
    }

    private GitHubMilestoneEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubMilestoneEventDTO.class);
    }
}
