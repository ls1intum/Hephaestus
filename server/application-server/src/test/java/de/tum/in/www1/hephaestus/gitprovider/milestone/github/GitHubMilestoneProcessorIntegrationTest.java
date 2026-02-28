package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto.GitHubMilestoneDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Integration tests for GitHubMilestoneProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Milestone upsert logic (create vs update)
 * - Domain event publishing (MilestoneProcessed, MilestoneDeleted)
 * - Context handling and workspace association
 * - Creator user association
 * - Edge cases in DTO processing
 */
@DisplayName("GitHub Milestone Processor")
class GitHubMilestoneProcessorIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final Long FIXTURE_CREATOR_ID = 5898705L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";
    private static final String FIXTURE_CREATOR_LOGIN = "FelixTJDietrich";

    @Autowired
    private GitHubMilestoneProcessor processor;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestMilestoneEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization matching fixture data
        Organization org = new Organization();
        org.setId(FIXTURE_ORG_ID);
        org.setProviderId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
        org = organizationRepository.save(org);

        // Create repository matching fixture data
        testRepository = new Repository();
        testRepository.setId(FIXTURE_REPO_ID);
        testRepository.setName("TestRepository");
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
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("hephaestus-test");
        testWorkspace.setDisplayName("Hephaestus Test");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(org);
        testWorkspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    private GitHubUserDTO createCreatorDto() {
        return new GitHubUserDTO(
            FIXTURE_CREATOR_ID,
            FIXTURE_CREATOR_ID,
            FIXTURE_CREATOR_LOGIN,
            "https://avatars.githubusercontent.com/u/" + FIXTURE_CREATOR_ID,
            "https://github.com/" + FIXTURE_CREATOR_LOGIN,
            null,
            null
        );
    }

    // ==================== Process (Create/Update) Tests ====================

    @Nested
    @DisplayName("Process Method")
    class ProcessMethod {

        @Test
        @DisplayName("Should create new milestone and publish MilestoneCreated event")
        void shouldCreateNewMilestoneAndPublishEvent() {
            // Given
            Long milestoneId = 14028854L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                3,
                "New Milestone",
                "A new milestone for testing",
                "open",
                Instant.parse("2025-11-30T08:00:00Z"),
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, createCreatorDto(), createContext());

            // Then - verify milestone created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(milestoneId);
            assertThat(result.getNumber()).isEqualTo(3);
            assertThat(result.getTitle()).isEqualTo("New Milestone");
            assertThat(result.getDescription()).isEqualTo("A new milestone for testing");
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
            assertThat(result.getDueOn()).isNotNull();
            assertThat(result.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

            // Verify persisted
            assertThat(milestoneRepository.findById(milestoneId)).isPresent();

            // Verify MilestoneCreated event published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.milestone().id()).isEqualTo(milestoneId);
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                    assertThat(event.context().repository().id()).isEqualTo(FIXTURE_REPO_ID);
                });
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should update existing milestone and publish MilestoneProcessed event with isNew=false")
        void shouldUpdateExistingMilestoneAndPublishEvent() {
            // Given - create existing milestone
            Long milestoneId = 14028854L;
            Milestone existing = new Milestone();
            existing.setId(milestoneId);
            existing.setNumber(3);
            existing.setTitle("Old Title");
            existing.setDescription("Old description");
            existing.setState(Milestone.State.OPEN);
            existing.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3");
            existing.setRepository(testRepository);
            milestoneRepository.save(existing);

            eventListener.clear();

            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                3,
                "Updated Title",
                "Updated description",
                "closed",
                Instant.parse("2026-01-14T08:00:00Z"),
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, createCreatorDto(), createContext());

            // Then - verify milestone updated
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            assertThat(result.getDescription()).isEqualTo("Updated description");
            assertThat(result.getState()).isEqualTo(Milestone.State.CLOSED);

            // Verify MilestoneUpdated event published
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.milestone().title()).isEqualTo("Updated Title");
                });
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should return null for null DTO")
        void shouldReturnNullForNullDto() {
            // When
            Milestone result = processor.process(null, testRepository, createCreatorDto(), createContext());

            // Then
            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should create milestone with generated ID when DTO has null ID (GraphQL sync scenario)")
        void shouldCreateMilestoneWithGeneratedIdWhenDtoHasNullId() {
            // Given - DTO without ID (like GraphQL sync)
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                null, // null ID - simulates GraphQL response
                1,
                "GraphQL Synced Milestone",
                "desc",
                "open",
                null,
                "https://example.com",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then - milestone should be created with a generated negative ID
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getId()).isNegative(); // Generated IDs are negative to avoid collision
            assertThat(result.getNumber()).isEqualTo(1);
            assertThat(result.getTitle()).isEqualTo("GraphQL Synced Milestone");
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should update existing milestone by number when DTO has null ID")
        void shouldUpdateExistingMilestoneByNumberWhenDtoHasNullId() {
            // Given - existing milestone
            Long existingId = 999888777L;
            Milestone existingMilestone = new Milestone();
            existingMilestone.setId(existingId);
            existingMilestone.setNumber(42);
            existingMilestone.setTitle("Existing Milestone");
            existingMilestone.setState(Milestone.State.OPEN);
            existingMilestone.setDescription("old description");
            existingMilestone.setRepository(testRepository);
            milestoneRepository.save(existingMilestone);

            // DTO without ID (like GraphQL sync) but matching number
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                null, // null ID
                42, // same number
                "Updated Title",
                "new description",
                "open",
                null,
                "https://example.com",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then - should update existing milestone, not create new one
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingId); // keeps original ID
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            assertThat(result.getDescription()).isEqualTo("new description");
            assertThat(milestoneRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle milestone with null description")
        void shouldHandleMilestoneWithNullDescription() {
            // Given
            Long milestoneId = 777888999L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                5,
                "No Description Milestone",
                null, // null description
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/5",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then
            assertThat(result.getDescription()).isNull();
            assertThat(milestoneRepository.findById(milestoneId).get().getDescription()).isNull();
        }

        @Test
        @DisplayName("Should handle milestone with null dueOn")
        void shouldHandleMilestoneWithNullDueOn() {
            // Given
            Long milestoneId = 888999000L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                6,
                "No Due Date Milestone",
                "Has description but no due date",
                "open",
                null, // null dueOn
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/6",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then
            assertThat(result.getDueOn()).isNull();
            assertThat(milestoneRepository.findById(milestoneId).get().getDueOn()).isNull();
        }

        @Test
        @DisplayName("Should be idempotent - processing same DTO twice")
        void shouldBeIdempotent() {
            // Given
            Long milestoneId = 123123123L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                7,
                "Idempotent Milestone",
                "Same every time",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/7",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When - process twice
            processor.process(dto, testRepository, null, createContext());
            eventListener.clear();
            processor.process(dto, testRepository, null, createContext());

            // Then - only one milestone exists, second time emits MilestoneUpdated (not Created)
            assertThat(milestoneRepository.count()).isEqualTo(1);
            assertThat(eventListener.getUpdatedEvents()).hasSize(1);
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should set creator user when provided")
        void shouldSetCreatorWhenProvided() {
            // Given
            Long milestoneId = 444555666L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                8,
                "Milestone With Creator",
                "Description",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/8",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, createCreatorDto(), createContext());

            // Then
            assertThat(result.getCreator()).isNotNull();
            assertThat(result.getCreator().getId()).isEqualTo(FIXTURE_CREATOR_ID);
            assertThat(result.getCreator().getLogin()).isEqualTo(FIXTURE_CREATOR_LOGIN);
        }

        @Test
        @DisplayName("Should create user if not exists when processing creator")
        void shouldCreateUserIfNotExists() {
            // Given
            Long milestoneId = 555666777L;
            Long newUserId = 999888777L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                9,
                "Milestone With New Creator",
                "Description",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/9",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );
            GitHubUserDTO newCreator = new GitHubUserDTO(
                newUserId,
                newUserId,
                "new-creator",
                "https://avatars.example.com/new",
                "https://github.com/new-creator",
                null,
                null
            );

            // Verify user doesn't exist
            assertThat(userRepository.findById(newUserId)).isEmpty();

            // When
            Milestone result = processor.process(dto, testRepository, newCreator, createContext());

            // Then
            assertThat(result.getCreator()).isNotNull();
            assertThat(userRepository.findById(newUserId)).isPresent();
        }

        @Test
        @DisplayName("Should handle null creator DTO gracefully")
        void shouldHandleNullCreator() {
            // Given
            Long milestoneId = 666777888L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                10,
                "Milestone Without Creator",
                "Description",
                "open",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/10",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then - milestone saved but creator is null
            assertThat(result).isNotNull();
            assertThat(result.getCreator()).isNull();
        }

        @Test
        @DisplayName("Should parse closed state correctly")
        void shouldParseClosedState() {
            // Given
            Long milestoneId = 777888999L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                11,
                "Closed Milestone",
                "Description",
                "closed",
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/11",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Milestone.State.CLOSED);
        }

        @Test
        @DisplayName("Should default to OPEN state for null state string")
        void shouldDefaultToOpenStateForNullState() {
            // Given
            Long milestoneId = 888999111L;
            GitHubMilestoneDTO dto = new GitHubMilestoneDTO(
                milestoneId,
                12,
                "Unknown State Milestone",
                "Description",
                null, // null state
                null,
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/12",
                0,
                0,
                Instant.now(),
                Instant.now(),
                null // closedAt
            );

            // When
            Milestone result = processor.process(dto, testRepository, null, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Milestone.State.OPEN);
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("Should delete existing milestone and publish MilestoneDeleted event")
        void shouldDeleteMilestoneAndPublishEvent() {
            // Given - create milestone
            Long milestoneId = 14028854L;
            Milestone milestone = new Milestone();
            milestone.setId(milestoneId);
            milestone.setNumber(3);
            milestone.setTitle("To Delete Milestone");
            milestone.setState(Milestone.State.OPEN);
            milestone.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/milestone/3");
            milestone.setRepository(testRepository);
            milestoneRepository.save(milestone);

            assertThat(milestoneRepository.findById(milestoneId)).isPresent();

            // When
            processor.delete(milestoneId, createContext());

            // Then - milestone deleted
            assertThat(milestoneRepository.findById(milestoneId)).isEmpty();

            // Verify event published
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.milestoneId()).isEqualTo(milestoneId);
                    assertThat(event.title()).isEqualTo("To Delete Milestone");
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                    assertThat(event.context().repository().id()).isEqualTo(FIXTURE_REPO_ID);
                });
        }

        @Test
        @DisplayName("Should handle deletion of non-existent milestone gracefully")
        void shouldHandleDeletionOfNonExistentMilestone() {
            // Given - milestone doesn't exist
            Long nonExistentId = 999999999L;
            assertThat(milestoneRepository.findById(nonExistentId)).isEmpty();

            // When/Then - should not throw
            assertThatCode(() -> processor.delete(nonExistentId, createContext())).doesNotThrowAnyException();

            // No event published
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null milestone ID")
        void shouldHandleNullMilestoneId() {
            // When/Then - should not throw
            assertThatCode(() -> processor.delete(null, createContext())).doesNotThrowAnyException();

            // No event published
            assertThat(eventListener.getDeletedEvents()).isEmpty();
        }
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestMilestoneEventListener {

        private final List<DomainEvent.MilestoneCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.MilestoneDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onMilestoneCreated(DomainEvent.MilestoneCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onMilestoneUpdated(DomainEvent.MilestoneUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onMilestoneDeleted(DomainEvent.MilestoneDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.MilestoneCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.MilestoneUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.MilestoneDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            deletedEvents.clear();
        }
    }
}
