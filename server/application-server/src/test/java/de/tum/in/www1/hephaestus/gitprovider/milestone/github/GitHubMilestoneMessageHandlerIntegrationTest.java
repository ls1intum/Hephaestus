package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for GitHubMilestoneMessageHandler.
 * <p>
 * Tests use JSON fixtures parsed directly into DTOs (no hub4j dependency).
 * Fixtures are real GitHub webhook payloads from HephaestusTest/TestRepository.
 */
@DisplayName("GitHub Milestone Message Handler")
@Transactional
class GitHubMilestoneMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final Long FIXTURE_MILESTONE_ID = 14028854L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_NAME = "TestRepository";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    @Autowired
    private GitHubMilestoneMessageHandler handler;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization matching fixture data
        Organization org = new Organization();
        org.setId(FIXTURE_ORG_ID);
        org.setGithubId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID + "?v=4");
        org = organizationRepository.save(org);

        // Create repository matching fixture data
        testRepository = new Repository();
        testRepository.setId(FIXTURE_REPO_ID);
        testRepository.setName(FIXTURE_REPO_NAME);
        testRepository.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        testRepository.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
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
        workspace.setAccountLogin(FIXTURE_ORG_LOGIN);
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
                assertThat(milestone.getId()).isEqualTo(FIXTURE_MILESTONE_ID);
                assertThat(milestone.getTitle()).isEqualTo(event.milestone().title());
                assertThat(milestone.getDescription()).isEqualTo(event.milestone().description());
                assertThat(milestone.getState()).isEqualTo(Milestone.State.OPEN);
                assertThat(milestone.getNumber()).isEqualTo(3);
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
    @DisplayName("Should reopen milestone on opened event")
    void shouldReopenMilestoneOnOpenedEvent() throws Exception {
        // Given - create milestone in closed state (use values from closed event fixture)
        Milestone closedMilestone = new Milestone();
        closedMilestone.setId(FIXTURE_MILESTONE_ID);
        closedMilestone.setNumber(3);
        closedMilestone.setTitle("Fixture Milestone");
        closedMilestone.setState(Milestone.State.CLOSED);
        closedMilestone.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3");
        closedMilestone.setRepository(testRepository);
        milestoneRepository.save(closedMilestone);

        // Load opened event
        GitHubMilestoneEventDTO openedEvent = loadPayload("milestone.opened");

        // When
        handler.handleEvent(openedEvent);

        // Then
        assertThat(milestoneRepository.findById(FIXTURE_MILESTONE_ID))
            .isPresent()
            .get()
            .satisfies(milestone -> {
                assertThat(milestone.getState()).isEqualTo(Milestone.State.OPEN);
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

    /**
     * Creates a test repository reference DTO matching the fixture data.
     */
    private GitHubRepositoryRefDTO createTestRepoRef() {
        return new GitHubRepositoryRefDTO(
            FIXTURE_REPO_ID,
            "R_kgDOO4CKWw",
            FIXTURE_REPO_NAME,
            FIXTURE_REPO_FULL_NAME,
            false,
            "https://github.com/" + FIXTURE_REPO_FULL_NAME
        );
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null milestone in event gracefully")
        void shouldHandleNullMilestoneGracefully() {
            // Given - event with null milestone
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "created",
                null,
                createTestRepoRef(),
                null
            );

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // No milestones should be created
            assertThat(milestoneRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should handle event with missing repository context")
        void shouldHandleMissingRepositoryContext() {
            // Given - event without repository
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                999999L,
                1,
                "orphan-milestone",
                "Test description",
                "open",
                null,
                "https://example.com"
            );
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "created",
                milestoneDto,
                null,
                null
            );

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle milestone with null description")
        void shouldHandleMilestoneWithNullDescription() {
            // Given - create milestone DTO with null description
            Long milestoneId = 123456789L;
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                milestoneId,
                1,
                "no-description-milestone",
                null, // null description
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/1"
            );
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "created",
                milestoneDto,
                createTestRepoRef(),
                null
            );

            // When
            handler.handleEvent(event);

            // Then
            assertThat(milestoneRepository.findById(milestoneId))
                .isPresent()
                .get()
                .satisfies(milestone -> {
                    assertThat(milestone.getTitle()).isEqualTo("no-description-milestone");
                    assertThat(milestone.getDescription()).isNull();
                });
        }

        @Test
        @DisplayName("Should handle milestone with null dueOn")
        void shouldHandleMilestoneWithNullDueOn() {
            // Given
            Long milestoneId = 234567890L;
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                milestoneId,
                2,
                "no-due-date-milestone",
                "Description",
                "open",
                null, // null dueOn
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/2"
            );
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "created",
                milestoneDto,
                createTestRepoRef(),
                null
            );

            // When
            handler.handleEvent(event);

            // Then
            assertThat(milestoneRepository.findById(milestoneId))
                .isPresent()
                .get()
                .satisfies(milestone -> {
                    assertThat(milestone.getDueOn()).isNull();
                });
        }

        @Test
        @DisplayName("Should update description from value to null")
        void shouldUpdateDescriptionToNull() {
            // Given - existing milestone with description
            Long milestoneId = 987654321L;
            Milestone existingMilestone = new Milestone();
            existingMilestone.setId(milestoneId);
            existingMilestone.setNumber(10);
            existingMilestone.setTitle("has-description");
            existingMilestone.setState(Milestone.State.OPEN);
            existingMilestone.setDescription("original description");
            existingMilestone.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/10");
            existingMilestone.setRepository(testRepository);
            milestoneRepository.save(existingMilestone);

            // When - update with null description (note: handler checks if dto.description() != null before setting)
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                milestoneId,
                10,
                "has-description",
                null, // setting to null
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/10"
            );
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "edited",
                milestoneDto,
                createTestRepoRef(),
                null
            );
            handler.handleEvent(event);

            // Then - description should remain unchanged (handler only updates if not null)
            assertThat(milestoneRepository.findById(milestoneId))
                .isPresent()
                .get()
                .extracting(Milestone::getDescription)
                .isEqualTo("original description");
        }

        @Test
        @DisplayName("Should handle idempotent milestone creation")
        void shouldHandleIdempotentCreation() throws Exception {
            // Given
            GitHubMilestoneEventDTO event = loadPayload("milestone.created");

            // When - handle same event twice
            handler.handleEvent(event);
            handler.handleEvent(event);

            // Then - only one milestone should exist
            assertThat(milestoneRepository.findById(event.milestone().id())).isPresent();
            assertThat(milestoneRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle deletion of non-existent milestone gracefully")
        void shouldHandleDeletionOfNonExistentMilestone() throws Exception {
            // Given - milestone doesn't exist
            GitHubMilestoneEventDTO event = loadPayload("milestone.deleted");
            assertThat(milestoneRepository.findById(event.milestone().id())).isEmpty();

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownAction() {
            // Given - event with unknown action
            GitHubMilestoneDTO milestoneDto = new GitHubMilestoneDTO(
                111222333L,
                1,
                "unknown-action-milestone",
                "desc",
                "open",
                null,
                "https://example.com"
            );
            GitHubMilestoneEventDTO event = new GitHubMilestoneEventDTO(
                "unknown_action", // not created/edited/closed/opened/deleted
                milestoneDto,
                createTestRepoRef(),
                null
            );

            // When/Then - should not throw, handler will process it as non-delete
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }
    }

    // ==================== Repository Association Tests ====================

    @Nested
    @DisplayName("Repository Association")
    class RepositoryAssociation {

        @Test
        @DisplayName("Should associate created milestone with correct repository")
        void shouldAssociateMilestoneWithRepository() throws Exception {
            // Given
            GitHubMilestoneEventDTO event = loadPayload("milestone.created");

            // When
            handler.handleEvent(event);

            // Then
            assertThat(milestoneRepository.findById(event.milestone().id()))
                .isPresent()
                .get()
                .satisfies(milestone -> {
                    assertThat(milestone.getRepository()).isNotNull();
                    assertThat(milestone.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);
                    assertThat(milestone.getRepository().getNameWithOwner()).isEqualTo(FIXTURE_REPO_FULL_NAME);
                });
        }

        @Test
        @DisplayName("Should find milestone by repository and number")
        void shouldFindMilestoneByRepositoryAndNumber() throws Exception {
            // Given
            GitHubMilestoneEventDTO event = loadPayload("milestone.created");
            handler.handleEvent(event);

            // When
            var foundMilestone = milestoneRepository.findByNumberAndRepositoryId(
                event.milestone().number(),
                FIXTURE_REPO_ID
            );

            // Then
            assertThat(foundMilestone)
                .isPresent()
                .get()
                .satisfies(milestone -> {
                    assertThat(milestone.getId()).isEqualTo(event.milestone().id());
                    assertThat(milestone.getTitle()).isEqualTo(event.milestone().title());
                });
        }
    }

    // ==================== Milestone-Issue Relationship Tests ====================

    @Nested
    @DisplayName("Milestone-Issue Relationship")
    class MilestoneIssueRelationship {

        @Test
        @DisplayName("Should preserve milestone-issue relationships after milestone edit")
        void shouldPreserveMilestoneIssueRelationshipsAfterEdit() throws Exception {
            // Given - create milestone and issue with that milestone
            GitHubMilestoneEventDTO createEvent = loadPayload("milestone.created");
            handler.handleEvent(createEvent);

            Milestone milestone = milestoneRepository.findById(createEvent.milestone().id()).orElseThrow();

            Issue issue = new Issue();
            issue.setId(12345L);
            issue.setNumber(1);
            issue.setTitle("Test Issue");
            issue.setState(Issue.State.OPEN);
            issue.setRepository(testRepository);
            issue.setCreatedAt(Instant.now());
            issue.setUpdatedAt(Instant.now());
            issue.setMilestone(milestone);
            issueRepository.save(issue);

            // When - edit the milestone
            GitHubMilestoneDTO editedDto = new GitHubMilestoneDTO(
                createEvent.milestone().id(),
                3,
                "Renamed Fixture Milestone",
                "Updated description",
                "open",
                Instant.parse("2026-01-14T08:00:00Z"),
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3"
            );
            GitHubMilestoneEventDTO editEvent = new GitHubMilestoneEventDTO(
                "edited",
                editedDto,
                createTestRepoRef(),
                null
            );
            handler.handleEvent(editEvent);

            // Then - issue should still have the milestone (now with updated title)
            Issue updatedIssue = issueRepository.findById(12345L).orElseThrow();
            assertThat(updatedIssue.getMilestone())
                .isNotNull()
                .satisfies(m -> {
                    assertThat(m.getTitle()).isEqualTo("Renamed Fixture Milestone");
                    assertThat(m.getDescription()).isEqualTo("Updated description");
                });
        }
    }
}
