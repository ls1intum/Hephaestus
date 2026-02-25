package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionCategoryDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionDTO;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.dto.GitHubDiscussionCommentDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Integration tests for GitHubDiscussionProcessor.
 * <p>
 * Tests the processor independently from the webhook handler to verify:
 * - Discussion upsert logic (create vs update)
 * - Domain event publishing (Created, Updated, Closed, Reopened, Answered, Deleted)
 * - Context handling and workspace association
 * - Author user association and creation
 * - Label and category associations
 * - Stale-data protection
 * - Edge cases in DTO processing including the critical getDatabaseId() fallback
 */
@DisplayName("GitHub Discussion Processor")
@Import(GitHubDiscussionProcessorIntegrationTest.TestDiscussionEventListener.class)
class GitHubDiscussionProcessorIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final Long FIXTURE_AUTHOR_ID = 5898705L;
    private static final Long FIXTURE_DISCUSSION_ID = 9096662L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";
    private static final String FIXTURE_AUTHOR_LOGIN = "FelixTJDietrich";

    @Autowired
    private GitHubDiscussionProcessor processor;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private DiscussionCategoryRepository categoryRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private TestDiscussionEventListener eventListener;

    private Repository testRepository;
    private Workspace testWorkspace;
    private Organization testOrganization;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    private void setupTestData() {
        // Create organization matching fixture data
        testOrganization = new Organization();
        testOrganization.setId(FIXTURE_ORG_ID);
        testOrganization.setGithubId(FIXTURE_ORG_ID);
        testOrganization.setLogin(FIXTURE_ORG_LOGIN);
        testOrganization.setCreatedAt(Instant.now());
        testOrganization.setUpdatedAt(Instant.now());
        testOrganization.setName("Hephaestus Test");
        testOrganization.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
        testOrganization = organizationRepository.save(testOrganization);

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
        testRepository.setOrganization(testOrganization);
        testRepository = repositoryRepository.save(testRepository);

        // Create workspace
        testWorkspace = new Workspace();
        testWorkspace.setWorkspaceSlug("hephaestus-test");
        testWorkspace.setDisplayName("Hephaestus Test");
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        testWorkspace.setIsPubliclyViewable(true);
        testWorkspace.setOrganization(testOrganization);
        testWorkspace.setAccountLogin(FIXTURE_ORG_LOGIN);
        testWorkspace.setAccountType(AccountType.ORG);
        testWorkspace = workspaceRepository.save(testWorkspace);
    }

    private ProcessingContext createContext() {
        return ProcessingContext.forSync(testWorkspace.getId(), testRepository);
    }

    private GitHubUserDTO createAuthorDto() {
        return new GitHubUserDTO(
            FIXTURE_AUTHOR_ID,
            FIXTURE_AUTHOR_ID,
            FIXTURE_AUTHOR_LOGIN,
            "https://avatars.githubusercontent.com/u/" + FIXTURE_AUTHOR_ID,
            "https://github.com/" + FIXTURE_AUTHOR_LOGIN,
            null,
            null
        );
    }

    private GitHubDiscussionDTO createBasicDiscussionDto(Long id, int number) {
        return new GitHubDiscussionDTO(
            id, // id (webhook style - no databaseId)
            null, // databaseId (null for webhook payloads)
            "D_node_" + id,
            number,
            "Test Discussion #" + number,
            "This is the body of test discussion #" + number,
            "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/" + number,
            "open",
            null, // stateReason
            false, // locked
            null, // activeLockReason
            0, // commentsCount
            0, // upvoteCount
            Instant.parse("2025-11-01T21:42:45Z"),
            Instant.parse("2025-11-01T21:42:45Z"),
            null, // closedAt
            null, // answerChosenAt
            createAuthorDto(),
            null, // answerChosenBy
            null, // category
            null, // labels
            null // answerComment
        );
    }

    // ==================== Critical: getDatabaseId() Fallback Tests ====================

    @Nested
    @DisplayName("getDatabaseId() Fallback Logic")
    class GetDatabaseIdFallback {

        @Test
        @DisplayName("Should use databaseId when present (GraphQL style)")
        void shouldUseDatabaseIdWhenPresent() {
            // Given - GraphQL style DTO with databaseId
            Long databaseId = 123456789L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                null, // id (null for GraphQL)
                databaseId, // databaseId
                "D_node_xyz",
                27,
                "GraphQL Discussion",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - should use databaseId
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(databaseId);
            assertThat(discussionRepository.findById(databaseId)).isPresent();
        }

        @Test
        @DisplayName("Should fallback to id when databaseId is null (Webhook style)")
        void shouldFallbackToIdWhenDatabaseIdNull() {
            // Given - Webhook style DTO with only id
            Long webhookId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                webhookId, // id (this is the database ID in webhooks)
                null, // databaseId is null in webhooks
                "D_kwDOO4CKW84CxV91",
                27,
                "Webhook Discussion",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // Verify the fallback works
            assertThat(dto.getDatabaseId()).isEqualTo(webhookId);

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - should use id as fallback
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(webhookId);
            assertThat(discussionRepository.findById(webhookId)).isPresent();
        }

        @Test
        @DisplayName("Should return null when both id and databaseId are null")
        void shouldReturnNullWhenBothIdsNull() {
            // Given - malformed DTO with no IDs
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                null, // id
                null, // databaseId
                "D_node_xyz",
                1,
                "No ID Discussion",
                "Body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // Verify fallback returns null
            assertThat(dto.getDatabaseId()).isNull();

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result).isNull();
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }
    }

    // ==================== Process (Create) Tests ====================

    @Nested
    @DisplayName("Process Method - Create")
    class ProcessMethodCreate {

        @Test
        @DisplayName("Should create new discussion and publish Created event")
        void shouldCreateNewDiscussionAndPublishEvent() {
            // Given
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO dto = createBasicDiscussionDto(discussionId, 27);

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - verify discussion created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(discussionId);
            assertThat(result.getNumber()).isEqualTo(27);
            assertThat(result.getTitle()).isEqualTo("Test Discussion #27");
            assertThat(result.getState()).isEqualTo(Discussion.State.OPEN);
            assertThat(result.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

            // Verify persisted
            assertThat(discussionRepository.findById(discussionId)).isPresent();

            // Verify Created event published
            assertThat(eventListener.getCreatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.discussion().id()).isEqualTo(discussionId);
                    assertThat(event.context().scopeId()).isEqualTo(testWorkspace.getId());
                });
        }

        @Test
        @DisplayName("Should create author user if not exists")
        void shouldCreateAuthorIfNotExists() {
            // Given - no user exists
            assertThat(userRepository.findById(FIXTURE_AUTHOR_ID)).isEmpty();

            Long discussionId = 111222333L;
            GitHubDiscussionDTO dto = createBasicDiscussionDto(discussionId, 1);

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getAuthor()).isNotNull();
            assertThat(result.getAuthor().getId()).isEqualTo(FIXTURE_AUTHOR_ID);
            assertThat(result.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
            assertThat(userRepository.findById(FIXTURE_AUTHOR_ID)).isPresent();
        }

        @Test
        @DisplayName("Should reuse existing author user")
        void shouldReuseExistingAuthor() {
            // Given - create user first
            User existingUser = new User();
            existingUser.setId(FIXTURE_AUTHOR_ID);
            existingUser.setLogin(FIXTURE_AUTHOR_LOGIN);
            existingUser.setAvatarUrl("https://avatars.example.com");
            userRepository.save(existingUser);

            long userCountBefore = userRepository.count();

            Long discussionId = 222333444L;
            GitHubDiscussionDTO dto = createBasicDiscussionDto(discussionId, 2);

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - should reuse existing user, not create new
            assertThat(result.getAuthor().getId()).isEqualTo(FIXTURE_AUTHOR_ID);
            assertThat(userRepository.count()).isEqualTo(userCountBefore);
        }

        @Test
        @DisplayName("Should handle discussion with null body")
        void shouldHandleNullBody() {
            // Given
            Long discussionId = 333444555L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                3,
                "Title",
                null, // null body
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getBody()).isNull();
        }

        @Test
        @DisplayName("Should handle discussion with null author")
        void shouldHandleNullAuthor() {
            // Given
            Long discussionId = 444555666L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                4,
                "Title",
                "Body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null, // null author
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAuthor()).isNull();
        }

        @Test
        @DisplayName("Should create labels when included in discussion")
        void shouldCreateLabelsWhenIncluded() {
            // Given
            Long discussionId = 555666777L;
            Long labelId = 9567656085L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(labelId, "LA_node", "bug", "Bug label", "ff0000", null, null);
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                5,
                "Title",
                "Body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                List.of(labelDto),
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getLabels()).hasSize(1);
            assertThat(result.getLabels().iterator().next().getName()).isEqualTo("bug");
            assertThat(labelRepository.findById(labelId)).isPresent();
        }

        @Test
        @DisplayName("Should create category when included in discussion")
        void shouldCreateCategoryWhenIncluded() {
            // Given
            Long discussionId = 666777888L;
            GitHubDiscussionCategoryDTO categoryDto = new GitHubDiscussionCategoryDTO(
                "DIC_kwDOO4CKW84CxV91",
                "General",
                "general",
                "\uD83D\uDCAC", // 💬
                "General discussions",
                false,
                Instant.now(),
                Instant.now()
            );
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                6,
                "Title",
                "Body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                categoryDto,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getCategory()).isNotNull();
            assertThat(result.getCategory().getName()).isEqualTo("General");
            assertThat(categoryRepository.findById("DIC_kwDOO4CKW84CxV91")).isPresent();
        }

        @Test
        @DisplayName("Should handle category with answerable flag")
        void shouldHandleCategoryWithAnswerable() {
            // Given
            Long discussionId = 777888999L;
            GitHubDiscussionCategoryDTO categoryDto = new GitHubDiscussionCategoryDTO(
                "DIC_kwDOO4CKW84CxV92",
                "Q&A",
                "q-a",
                "\u2753", // ❓
                "Ask questions and get answers",
                true, // answerable
                Instant.now(),
                Instant.now()
            );
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                7,
                "Question Title",
                "Question body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                categoryDto,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getCategory()).isNotNull();
            assertThat(result.getCategory().getName()).isEqualTo("Q&A");
            assertThat(result.getCategory().isAnswerable()).isTrue();
        }
    }

    // ==================== Process (Update) Tests ====================

    @Nested
    @DisplayName("Process Method - Update")
    class ProcessMethodUpdate {

        @Test
        @DisplayName("Should update existing discussion and publish Updated event")
        void shouldUpdateExistingDiscussionAndPublishUpdatedEvent() {
            // Given - create existing discussion
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO initialDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Old Title",
                "Old body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );
            processor.process(initialDto, createContext());

            eventListener.clear();

            // Now update with new data
            GitHubDiscussionDTO updateDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "New Title",
                "New body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-02T10:00:00Z"), // newer updatedAt
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(updateDto, createContext());

            // Then - verify discussion updated
            assertThat(result.getTitle()).isEqualTo("New Title");
            assertThat(result.getBody()).isEqualTo("New body");

            // Verify Updated event with changedFields
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.changedFields()).contains("title", "body");
                });
        }

        @Test
        @DisplayName("Should not publish Updated event when no fields changed")
        void shouldNotPublishUpdatedEventWhenNoFieldsChanged() {
            // Given - create existing discussion
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Same Title",
                "Same body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );
            processor.process(dto, createContext());

            eventListener.clear();

            // Process same data again with a newer updatedAt so it doesn't get skipped as stale
            GitHubDiscussionDTO sameDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Same Title",
                "Same body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-02T10:00:00Z"), // newer so it passes stale check
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            processor.process(sameDto, createContext());

            // Then - no Updated event (empty changedFields means no event published)
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should be idempotent - processing same DTO twice")
        void shouldBeIdempotent() {
            // Given
            Long discussionId = 111222333L;
            GitHubDiscussionDTO dto = createBasicDiscussionDto(discussionId, 10);

            // When - process twice
            processor.process(dto, createContext());
            long countAfterFirst = discussionRepository.count();

            eventListener.clear();
            processor.process(dto, createContext());

            // Then - only one discussion exists
            assertThat(discussionRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("Should skip stale update when existing data is newer")
        void shouldSkipStaleUpdate() {
            // Given - create discussion with a future updatedAt
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO initialDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Original Title",
                "Original body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2030-01-01T00:00:00Z"), // far future updatedAt
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );
            processor.process(initialDto, createContext());

            eventListener.clear();

            // Now try to update with an older updatedAt
            GitHubDiscussionDTO staleDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Stale Title",
                "Stale body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"), // older than existing
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(staleDto, createContext());

            // Then - should return existing without updating
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Original Title");

            // No Updated event published
            assertThat(eventListener.getUpdatedEvents()).isEmpty();
            // No Created event published either (it was already created)
            assertThat(eventListener.getCreatedEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should track labels changed in Updated event")
        void shouldTrackLabelsChangedInUpdatedEvent() {
            // Given - create discussion without labels
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO initialDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Title",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );
            processor.process(initialDto, createContext());

            eventListener.clear();

            // Now update with a label
            Long labelId = 9567656085L;
            GitHubLabelDTO labelDto = new GitHubLabelDTO(
                labelId,
                "LA_node",
                "enhancement",
                "Enhancement",
                "84b6eb",
                null,
                null
            );
            GitHubDiscussionDTO updateDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Title",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-02T10:00:00Z"), // newer updatedAt
                null,
                null,
                createAuthorDto(),
                null,
                null,
                List.of(labelDto),
                null
            );

            // When
            processor.process(updateDto, createContext());

            // Then - Updated event should contain "labels" in changedFields
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.changedFields()).contains("labels");
                });
        }

        @Test
        @DisplayName("Should update category when changed")
        void shouldUpdateCategoryWhenChanged() {
            // Given - create discussion without category
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO initialDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Title",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                null,
                null,
                createAuthorDto(),
                null,
                null, // no category
                null,
                null
            );
            processor.process(initialDto, createContext());

            eventListener.clear();

            // Now update with a category
            GitHubDiscussionCategoryDTO categoryDto = new GitHubDiscussionCategoryDTO(
                "DIC_kwDOO4CKW84CxV91",
                "General",
                "general",
                "\uD83D\uDCAC",
                "General discussions",
                false,
                Instant.now(),
                Instant.now()
            );
            GitHubDiscussionDTO updateDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Title",
                "Body",
                "https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/27",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-02T10:00:00Z"), // newer updatedAt
                null,
                null,
                createAuthorDto(),
                null,
                categoryDto,
                null,
                null
            );

            // When
            Discussion result = processor.process(updateDto, createContext());

            // Then
            assertThat(result.getCategory()).isNotNull();
            assertThat(result.getCategory().getName()).isEqualTo("General");

            // Updated event should contain "category" in changedFields
            assertThat(eventListener.getUpdatedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.changedFields()).contains("category");
                });
        }
    }

    // ==================== State Transition Tests ====================

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("processClosed should publish Closed event")
        void processClosedShouldPublishClosedEvent() {
            // Given
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Title",
                "Body",
                "https://example.com",
                "closed",
                "resolved", // closed with reason
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now(), // closedAt
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.processClosed(dto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Discussion.State.CLOSED);
            assertThat(result.getStateReason()).isEqualTo(Discussion.StateReason.RESOLVED);

            // Verify Closed event
            assertThat(eventListener.getClosedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.stateReason()).isEqualTo("resolved");
                });
        }

        @Test
        @DisplayName("processReopened should publish Reopened event")
        void processReopenedShouldPublishReopenedEvent() {
            // Given - create closed discussion first
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO closedDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Closed Discussion",
                "Body",
                "https://example.com",
                "closed",
                "resolved",
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-01T22:00:00Z"),
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );
            processor.process(closedDto, createContext());

            eventListener.clear();

            // Now reopen
            GitHubDiscussionDTO reopenedDto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Closed Discussion",
                "Body",
                "https://example.com",
                "open",
                "reopened",
                false,
                null,
                0,
                0,
                Instant.parse("2025-11-01T21:42:45Z"),
                Instant.parse("2025-11-02T10:00:00Z"), // newer updatedAt
                null, // closedAt cleared
                null,
                createAuthorDto(),
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.processReopened(reopenedDto, createContext());

            // Then
            assertThat(result.getState()).isEqualTo(Discussion.State.OPEN);

            // Verify Reopened event
            assertThat(eventListener.getReopenedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.discussion().id()).isEqualTo(discussionId);
                });
        }

        @Test
        @DisplayName("processAnswered should publish Answered event")
        void processAnsweredShouldPublishAnsweredEvent() {
            // Given
            Long discussionId = FIXTURE_DISCUSSION_ID;
            Instant now = Instant.now();
            GitHubDiscussionCommentDTO answerComment = new GitHubDiscussionCommentDTO(
                14848457L,
                null,
                "DC_node",
                "Answer body",
                "https://example.com",
                true,
                false,
                null,
                "MEMBER",
                now,
                now,
                createAuthorDto(),
                null
            );
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                27,
                "Question Title",
                "Question body",
                "https://example.com",
                "open",
                null,
                false,
                null,
                1,
                0,
                now,
                now,
                null,
                now, // answerChosenAt
                createAuthorDto(),
                createAuthorDto(), // answerChosenBy
                null,
                null,
                answerComment
            );

            // When
            Discussion result = processor.processAnswered(dto, createContext());

            // Then
            assertThat(result).isNotNull();

            // Verify Answered event
            assertThat(eventListener.getAnsweredEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.discussion().id()).isEqualTo(discussionId);
                    assertThat(event.answerCommentId()).isEqualTo(14848457L);
                });
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Method")
    class DeleteMethod {

        @Test
        @DisplayName("processDeleted should delete discussion")
        void processDeletedShouldDeleteDiscussion() {
            // Given - create discussion
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO createDto = createBasicDiscussionDto(discussionId, 27);
            processor.process(createDto, createContext());

            assertThat(discussionRepository.findById(discussionId)).isPresent();

            eventListener.clear();

            GitHubDiscussionDTO deleteDto = createBasicDiscussionDto(discussionId, 27);

            // When
            processor.processDeleted(deleteDto, createContext());

            // Then
            assertThat(discussionRepository.findById(discussionId)).isEmpty();

            // Verify Deleted event
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.discussionId()).isEqualTo(discussionId);
                });
        }

        @Test
        @DisplayName("processDeleted should handle non-existent discussion gracefully")
        void processDeletedShouldHandleNonExistentGracefully() {
            // Given - discussion doesn't exist
            Long nonExistentId = 999999999L;
            assertThat(discussionRepository.findById(nonExistentId)).isEmpty();

            GitHubDiscussionDTO dto = createBasicDiscussionDto(nonExistentId, 99);

            // When/Then - should not throw
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("processDeleted should handle null ID gracefully")
        void processDeletedShouldHandleNullIdGracefully() {
            // Given
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                null, // id
                null, // databaseId
                "node",
                1,
                "Title",
                null,
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // When/Then - should not throw (falls through to findByRepositoryIdAndNumber)
            assertThatCode(() -> processor.processDeleted(dto, createContext())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("processDeleted should fallback to repository and number")
        void processDeletedShouldFallbackToRepositoryAndNumber() {
            // Given - create discussion with a known ID
            Long discussionId = FIXTURE_DISCUSSION_ID;
            GitHubDiscussionDTO createDto = createBasicDiscussionDto(discussionId, 27);
            processor.process(createDto, createContext());

            assertThat(discussionRepository.findById(discussionId)).isPresent();

            eventListener.clear();

            // Delete DTO has null IDs - should find by repo+number
            GitHubDiscussionDTO deleteDto = new GitHubDiscussionDTO(
                null, // id is null
                null, // databaseId is null
                "node",
                27, // same number
                "Title",
                null,
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // When
            processor.processDeleted(deleteDto, createContext());

            // Then - discussion should be deleted via the fallback path
            assertThat(discussionRepository.findById(discussionId)).isEmpty();

            // Verify Deleted event
            assertThat(eventListener.getDeletedEvents())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.discussionId()).isEqualTo(discussionId);
                });
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null DTO IDs gracefully")
        void shouldHandleNullDtoIds() {
            // When - DTO with null IDs (getDatabaseId() will return null)
            GitHubDiscussionDTO nullIdDto = new GitHubDiscussionDTO(
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            Discussion result = processor.process(nullIdDto, createContext());

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should default to OPEN state for non-closed state")
        void shouldDefaultToOpenStateForNonClosedState() {
            // Given
            Long discussionId = 111222333L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                1,
                "Title",
                null,
                "https://example.com",
                "UNKNOWN_STATE", // not "closed"
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - isClosed() returns false for anything other than "closed", so state = OPEN
            assertThat(result.getState()).isEqualTo(Discussion.State.OPEN);
        }

        @Test
        @DisplayName("Should handle unknown stateReason as UNKNOWN")
        void shouldHandleUnknownStateReasonAsUnknown() {
            // Given
            Long discussionId = 222333444L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                2,
                "Title",
                null,
                "https://example.com",
                "closed",
                "some_weird_reason",
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getStateReason()).isEqualTo(Discussion.StateReason.UNKNOWN);
        }

        @Test
        @DisplayName("Should handle unknown lock reason")
        void shouldHandleUnknownLockReason() {
            // Given
            Long discussionId = 333444555L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                3,
                "Title",
                null,
                "https://example.com",
                "open",
                null,
                true, // locked
                "some_unknown_reason", // unknown lock reason
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then - unknown lock reason returns null per convertLockReason
            assertThat(result.isLocked()).isTrue();
            assertThat(result.getActiveLockReason()).isNull();
        }

        @Test
        @DisplayName("Should handle empty labels list")
        void shouldHandleEmptyLabelsList() {
            // Given
            Long discussionId = 444555666L;
            GitHubDiscussionDTO dto = new GitHubDiscussionDTO(
                discussionId,
                null,
                "node",
                4,
                "Title",
                null,
                "https://example.com",
                "open",
                null,
                false,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                createAuthorDto(),
                null,
                null,
                List.of(), // empty labels
                null
            );

            // When
            Discussion result = processor.process(dto, createContext());

            // Then
            assertThat(result.getLabels()).isEmpty();
        }
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestDiscussionEventListener {

        private final List<DomainEvent.DiscussionCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionAnswered> answeredEvents = new ArrayList<>();
        private final List<DomainEvent.DiscussionDeleted> deletedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.DiscussionCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.DiscussionUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.DiscussionClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.DiscussionReopened event) {
            reopenedEvents.add(event);
        }

        @EventListener
        public void onAnswered(DomainEvent.DiscussionAnswered event) {
            answeredEvents.add(event);
        }

        @EventListener
        public void onDeleted(DomainEvent.DiscussionDeleted event) {
            deletedEvents.add(event);
        }

        public List<DomainEvent.DiscussionCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.DiscussionUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.DiscussionClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.DiscussionReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public List<DomainEvent.DiscussionAnswered> getAnsweredEvents() {
            return new ArrayList<>(answeredEvents);
        }

        public List<DomainEvent.DiscussionDeleted> getDeletedEvents() {
            return new ArrayList<>(deletedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
            answeredEvents.clear();
            deletedEvents.clear();
        }
    }
}
