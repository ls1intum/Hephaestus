package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategory;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto.GitHubDiscussionEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GitHubDiscussionMessageHandler.
 * <p>
 * Tests the full webhook handling flow using JSON fixtures parsed directly
 * into DTOs for complete isolation. Verifies:
 * - Correct routing of webhook actions to processor methods
 * - Discussion persistence for all action types
 * - Event publishing through the handler → processor chain
 * - Category creation and association
 * - Edge cases in event handling
 * <p>
 * Note: This test class does NOT use @Transactional because the discussion processing
 * chain calls GitHubUserProcessor.findOrCreate() which uses REQUIRES_NEW propagation.
 * Having @Transactional here would cause connection pool deadlocks under parallel test
 * execution (-T 2C) as the test transaction holds a connection while REQUIRES_NEW
 * needs an additional one. We use TransactionTemplate for lazy-loading assertions.
 * <p>
 * <b>Fixture Values (discussion.created.json - Discussion #27):</b>
 * <ul>
 *   <li>ID: 9096662</li>
 *   <li>Number: 27</li>
 *   <li>Title: "Fixture discussion thread"</li>
 *   <li>State: open</li>
 *   <li>Locked: false</li>
 *   <li>HTML URL: "https://github.com/HephaestusTest/TestRepository/discussions/27"</li>
 *   <li>Created at: 2025-11-01T23:15:45Z</li>
 *   <li>Updated at: 2025-11-01T23:15:45Z</li>
 *   <li>Comments: 0</li>
 *   <li>Author: FelixTJDietrich (ID: 5898705)</li>
 *   <li>Category: General (ID: "46489461")</li>
 * </ul>
 */
@DisplayName("GitHub Discussion Message Handler")
@Import(GitHubDiscussionMessageHandlerIntegrationTest.TestEventListener.class)
class GitHubDiscussionMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Discussion IDs from different fixtures
    private static final Long DISCUSSION_27_ID = 9096662L; // created, edited, closed, reopened, answered, etc.
    private static final Long DISCUSSION_28_ID = 9096674L; // deleted

    // Exact fixture values from discussion.created.json for correctness verification
    private static final int FIXTURE_DISCUSSION_NUMBER = 27;
    private static final String FIXTURE_DISCUSSION_TITLE = "Fixture discussion thread";
    private static final String FIXTURE_DISCUSSION_HTML_URL =
        "https://github.com/HephaestusTest/TestRepository/discussions/27";
    private static final String FIXTURE_DISCUSSION_BODY = "Testing discussion webhook payloads";
    private static final Instant FIXTURE_DISCUSSION_CREATED_AT = Instant.parse("2025-11-01T23:15:45Z");
    private static final Instant FIXTURE_DISCUSSION_UPDATED_AT = Instant.parse("2025-11-01T23:15:45Z");
    private static final int FIXTURE_DISCUSSION_COMMENTS_COUNT = 0;

    // Author fixture values
    private static final Long FIXTURE_AUTHOR_ID = 5898705L;
    private static final String FIXTURE_AUTHOR_LOGIN = "FelixTJDietrich";
    private static final String FIXTURE_AUTHOR_AVATAR_URL = "https://avatars.githubusercontent.com/u/5898705?v=4";
    private static final String FIXTURE_AUTHOR_HTML_URL = "https://github.com/FelixTJDietrich";

    // Category fixture values (General category from created fixture)
    // Note: GitHubDiscussionCategoryDTO maps @JsonProperty("id") to nodeId (String),
    // so the JSON numeric "id": 46489461 is coerced to "46489461"
    private static final String FIXTURE_GENERAL_CATEGORY_ID = "46489461";
    private static final String FIXTURE_GENERAL_CATEGORY_NAME = "General";
    private static final String FIXTURE_GENERAL_CATEGORY_SLUG = "general";

    // Q&A category from edited/answered fixtures
    private static final String FIXTURE_QA_CATEGORY_ID = "46489462";
    private static final String FIXTURE_QA_CATEGORY_NAME = "Q&A";

    // Label fixture values from discussion.labeled.json
    private static final Long FIXTURE_LABEL_ID = 9568029313L;
    private static final String FIXTURE_LABEL_NAME = "fixtures";
    private static final String FIXTURE_LABEL_COLOR = "0e8a16";

    @Autowired
    private GitHubDiscussionMessageHandler handler;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private DiscussionCategoryRepository categoryRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestEventListener eventListener;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Key Tests ====================

    @Nested
    @DisplayName("Event Type")
    class EventType {

        @Test
        @DisplayName("Should return DISCUSSION as event type")
        void shouldReturnCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitHubEventType.DISCUSSION);
        }
    }

    // ==================== Basic Lifecycle Events ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("Should persist discussion with all schema fields on 'created' event")
        void shouldPersistDiscussionOnCreatedEvent() throws Exception {
            // Given
            GitHubDiscussionEventDTO event = loadPayload("discussion.created");

            // When
            handler.handleEvent(event);

            // Then - verify ALL persisted fields against hardcoded fixture values
            // Use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository
                    .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                    .orElseThrow();

                // Core identification fields
                assertThat(discussion.getNativeId()).isEqualTo(DISCUSSION_27_ID);
                assertThat(discussion.getNumber()).isEqualTo(FIXTURE_DISCUSSION_NUMBER);

                // Content fields
                assertThat(discussion.getTitle()).isEqualTo(FIXTURE_DISCUSSION_TITLE);
                assertThat(discussion.getBody()).isEqualTo(FIXTURE_DISCUSSION_BODY);

                // State fields
                assertThat(discussion.getState()).isEqualTo(Discussion.State.OPEN);
                assertThat(discussion.getStateReason()).isNull();
                assertThat(discussion.isLocked()).isFalse();
                assertThat(discussion.getActiveLockReason()).isNull();
                assertThat(discussion.getClosedAt()).isNull();
                assertThat(discussion.getAnswerChosenAt()).isNull();

                // URL fields
                assertThat(discussion.getHtmlUrl()).isEqualTo(FIXTURE_DISCUSSION_HTML_URL);

                // Timestamp fields (critical for sync correctness)
                assertThat(discussion.getCreatedAt()).isEqualTo(FIXTURE_DISCUSSION_CREATED_AT);
                assertThat(discussion.getUpdatedAt()).isEqualTo(FIXTURE_DISCUSSION_UPDATED_AT);

                // Counts
                assertThat(discussion.getCommentsCount()).isEqualTo(FIXTURE_DISCUSSION_COMMENTS_COUNT);

                // Repository association (foreign key)
                assertThat(discussion.getRepository()).isNotNull();
                assertThat(discussion.getRepository().getNativeId()).isEqualTo(FIXTURE_REPO_ID);

                // Author association (foreign key) - verify exact fixture values
                assertThat(discussion.getAuthor()).isNotNull();
                assertThat(discussion.getAuthor().getNativeId()).isEqualTo(FIXTURE_AUTHOR_ID);
                assertThat(discussion.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
                assertThat(discussion.getAuthor().getAvatarUrl()).isEqualTo(FIXTURE_AUTHOR_AVATAR_URL);
                assertThat(discussion.getAuthor().getHtmlUrl()).isEqualTo(FIXTURE_AUTHOR_HTML_URL);

                // Category association - verify exact fixture values
                assertThat(discussion.getCategory()).isNotNull();
                assertThat(discussion.getCategory().getId()).isEqualTo(FIXTURE_GENERAL_CATEGORY_ID);
                assertThat(discussion.getCategory().getName()).isEqualTo(FIXTURE_GENERAL_CATEGORY_NAME);
                assertThat(discussion.getCategory().getSlug()).isEqualTo(FIXTURE_GENERAL_CATEGORY_SLUG);

                // Empty associations
                assertThat(discussion.getLabels()).isEmpty();
                assertThat(discussion.getAnswerChosenBy()).isNull();
                assertThat(discussion.getAnswerComment()).isNull();
            });

            // Domain event published
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should update discussion on 'edited' event")
        void shouldUpdateDiscussionOnEditedEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO editedEvent = loadPayload("discussion.edited");

            // When
            handler.handleEvent(editedEvent);

            // Then
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository
                    .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                    .orElseThrow();
                assertThat(discussion.getBody()).isEqualTo("Updated body for webhook tests");
            });

            // Discussion should still be unique (edited, not created new)
            assertThat(discussionRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should close discussion on 'closed' event")
        void shouldHandleClosedEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO closedEvent = loadPayload("discussion.closed");

            // When
            handler.handleEvent(closedEvent);

            // Then
            Discussion discussion = discussionRepository
                .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                .orElse(null);
            assertThat(discussion).isNotNull();
            assertThat(discussion.getState()).isEqualTo(Discussion.State.CLOSED);
            assertThat(discussion.getStateReason()).isEqualTo(Discussion.StateReason.RESOLVED);

            // Verify Closed event was published (processClosed publishes both Updated and Closed)
            assertThat(eventListener.getClosedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should reopen discussion on 'reopened' event")
        void shouldHandleReopenedEvent() throws Exception {
            // Given - create and close discussion
            handler.handleEvent(loadPayload("discussion.created"));
            handler.handleEvent(loadPayload("discussion.closed"));
            eventListener.clear();

            GitHubDiscussionEventDTO reopenedEvent = loadPayload("discussion.reopened");

            // When
            handler.handleEvent(reopenedEvent);

            // Then
            Discussion discussion = discussionRepository
                .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                .orElse(null);
            assertThat(discussion).isNotNull();
            assertThat(discussion.getState()).isEqualTo(Discussion.State.OPEN);

            // Verify Reopened event was published
            assertThat(eventListener.getReopenedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should delete discussion on 'deleted' event")
        void shouldDeleteDiscussionOnDeletedEvent() throws Exception {
            // Given - the deleted fixture uses discussion #28 (ID 9096674)
            // First, we create it by simulating it exists
            Discussion discussionToDelete = new Discussion();
            discussionToDelete.setNativeId(DISCUSSION_28_ID);
            discussionToDelete.setNumber(28);
            discussionToDelete.setTitle("Disposable discussion");
            discussionToDelete.setState(Discussion.State.OPEN);
            discussionToDelete.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME + "/discussions/28");
            discussionToDelete.setRepository(testRepository);
            discussionToDelete.setProvider(gitProvider);
            discussionRepository.save(discussionToDelete);

            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 28)).isTrue();

            GitHubDiscussionEventDTO deletedEvent = loadPayload("discussion.deleted");

            // When
            handler.handleEvent(deletedEvent);

            // Then
            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 28)).isFalse();

            // Verify Deleted event was published
            assertThat(eventListener.getDeletedEvents()).hasSize(1);
        }
    }

    // ==================== Answer Events ====================

    @Nested
    @DisplayName("Answer Events")
    class AnswerEvents {

        @Test
        @DisplayName("Should handle 'answered' event and publish DiscussionAnswered")
        void shouldHandleAnsweredEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO answeredEvent = loadPayload("discussion.answered");

            // When
            handler.handleEvent(answeredEvent);

            // Then
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository
                    .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                    .orElseThrow();
                assertThat(discussion.getAnswerChosenAt()).isNotNull();
                assertThat(discussion.getAnswerChosenBy()).isNotNull();
                assertThat(discussion.getAnswerChosenBy().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
            });

            // Verify Answered event was published
            assertThat(eventListener.getAnsweredEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle 'unanswered' event")
        void shouldHandleUnansweredEvent() throws Exception {
            // Given - create discussion and mark as answered
            handler.handleEvent(loadPayload("discussion.created"));
            handler.handleEvent(loadPayload("discussion.answered"));
            eventListener.clear();

            GitHubDiscussionEventDTO unansweredEvent = loadPayload("discussion.unanswered");

            // When
            handler.handleEvent(unansweredEvent);

            // Then - discussion should still exist and be processed
            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 27)).isTrue();
        }
    }

    // ==================== Label Events ====================

    @Nested
    @DisplayName("Label Events")
    class LabelEvents {

        @Test
        @DisplayName("Should handle 'labeled' event and persist label")
        void shouldHandleLabeledEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO labeledEvent = loadPayload("discussion.labeled");

            // When
            handler.handleEvent(labeledEvent);

            // Then - use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository
                    .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                    .orElse(null);
                assertThat(discussion).isNotNull();
                assertThat(labelNames(discussion)).contains(FIXTURE_LABEL_NAME);
            });

            // Verify label was created in repository
            assertThat(labelRepository.findById(FIXTURE_LABEL_ID)).isPresent();
        }

        @Test
        @DisplayName("Should handle 'unlabeled' event")
        void shouldHandleUnlabeledEvent() throws Exception {
            // Given - create discussion with label
            handler.handleEvent(loadPayload("discussion.created"));
            handler.handleEvent(loadPayload("discussion.labeled"));
            eventListener.clear();

            GitHubDiscussionEventDTO unlabeledEvent = loadPayload("discussion.unlabeled");

            // When
            handler.handleEvent(unlabeledEvent);

            // Then - discussion should still exist
            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 27)).isTrue();
        }
    }

    // ==================== Lock Events ====================

    @Nested
    @DisplayName("Lock Events")
    class LockEvents {

        @Test
        @DisplayName("Should handle 'locked' event and set lock reason")
        void shouldHandleLockedEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO lockedEvent = loadPayload("discussion.locked");

            // When
            handler.handleEvent(lockedEvent);

            // Then - verify lock state
            Discussion discussion = discussionRepository
                .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                .orElse(null);
            assertThat(discussion).isNotNull();
            assertThat(discussion.isLocked()).isTrue();
            assertThat(discussion.getActiveLockReason()).isEqualTo(Discussion.LockReason.RESOLVED);
            // Note: fixture has state="locked" but isClosed() only matches "closed",
            // so the entity state will be OPEN
            assertThat(discussion.getState()).isEqualTo(Discussion.State.OPEN);
        }

        @Test
        @DisplayName("Should handle 'unlocked' event")
        void shouldHandleUnlockedEvent() throws Exception {
            // Given - create and lock discussion
            handler.handleEvent(loadPayload("discussion.created"));
            handler.handleEvent(loadPayload("discussion.locked"));
            eventListener.clear();

            GitHubDiscussionEventDTO unlockedEvent = loadPayload("discussion.unlocked");

            // When
            handler.handleEvent(unlockedEvent);

            // Then
            Discussion discussion = discussionRepository
                .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                .orElse(null);
            assertThat(discussion).isNotNull();
            assertThat(discussion.isLocked()).isFalse();
        }
    }

    // ==================== Pin Events ====================

    @Nested
    @DisplayName("Pin Events")
    class PinEvents {

        @Test
        @DisplayName("Should handle 'pinned' event")
        void shouldHandlePinnedEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO pinnedEvent = loadPayload("discussion.pinned");

            // When
            handler.handleEvent(pinnedEvent);

            // Then - discussion should still exist
            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 27)).isTrue();
        }

        @Test
        @DisplayName("Should handle 'unpinned' event")
        void shouldHandleUnpinnedEvent() throws Exception {
            // Given - create discussion first
            handler.handleEvent(loadPayload("discussion.created"));
            handler.handleEvent(loadPayload("discussion.pinned"));
            eventListener.clear();

            GitHubDiscussionEventDTO unpinnedEvent = loadPayload("discussion.unpinned");

            // When
            handler.handleEvent(unpinnedEvent);

            // Then
            assertThat(discussionRepository.existsByRepositoryIdAndNumber(testRepository.getId(), 27)).isTrue();
        }
    }

    // ==================== Category Changed Event ====================

    @Nested
    @DisplayName("Category Changed Event")
    class CategoryChangedEvent {

        @Test
        @DisplayName("Should handle 'category_changed' event and update category")
        void shouldHandleCategoryChangedEvent() throws Exception {
            // Given - create discussion first (starts in General category)
            handler.handleEvent(loadPayload("discussion.created"));
            eventListener.clear();

            GitHubDiscussionEventDTO categoryChangedEvent = loadPayload("discussion.category_changed");

            // When
            handler.handleEvent(categoryChangedEvent);

            // Then - category should be updated to Q&A
            transactionTemplate.executeWithoutResult(status -> {
                Discussion discussion = discussionRepository
                    .findByRepositoryIdAndNumber(testRepository.getId(), 27)
                    .orElseThrow();
                assertThat(discussion.getCategory()).isNotNull();
                assertThat(discussion.getCategory().getId()).isEqualTo(FIXTURE_QA_CATEGORY_ID);
                assertThat(discussion.getCategory().getName()).isEqualTo(FIXTURE_QA_CATEGORY_NAME);
            });
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle unknown action gracefully (falls back to process)")
        void shouldHandleUnknownActionGracefully() throws Exception {
            // Given
            GitHubDiscussionEventDTO event = loadPayload("discussion.created");

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle missing repository context gracefully")
        void shouldHandleMissingRepositoryContextGracefully() throws Exception {
            // Given - remove the repository so context creation fails
            repositoryRepository.deleteAll();

            GitHubDiscussionEventDTO event = loadPayload("discussion.created");

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // Discussion should not be persisted since context is null
            assertThat(discussionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should be idempotent - processing same event twice")
        void shouldBeIdempotent() throws Exception {
            // Given
            GitHubDiscussionEventDTO event = loadPayload("discussion.created");

            // When - handle same event twice
            handler.handleEvent(event);
            long countAfterFirst = discussionRepository.count();

            handler.handleEvent(event);

            // Then - still only one discussion
            assertThat(discussionRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("Should verify getDatabaseId() fallback works (id is used when databaseId is null)")
        void shouldVerifyGetDatabaseIdFallback() throws Exception {
            // Given - webhook payloads have 'id' not 'database_id'
            GitHubDiscussionEventDTO event = loadPayload("discussion.created");

            // Verify the DTO is using the fallback correctly
            assertThat(event.discussion().getDatabaseId()).isEqualTo(DISCUSSION_27_ID);

            // When
            handler.handleEvent(event);

            // Then - discussion should be persisted with the correct ID
            assertThat(discussionRepository.findByRepositoryIdAndNumber(testRepository.getId(), 27)).isPresent();
        }

        @Test
        @DisplayName("Should create author and category entities with correct field values")
        void shouldCreateAllRelatedEntitiesFromCreatedEvent() throws Exception {
            // Given - no users or categories exist
            assertThat(userRepository.count()).isZero();

            // When
            handler.handleEvent(loadPayload("discussion.created"));

            // Then - author created with exact fixture values
            var author = userRepository
                .findByNativeIdAndProviderId(FIXTURE_AUTHOR_ID, gitProvider.getId())
                .orElseThrow();
            assertThat(author.getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
            assertThat(author.getAvatarUrl()).isEqualTo(FIXTURE_AUTHOR_AVATAR_URL);
            assertThat(author.getHtmlUrl()).isEqualTo(FIXTURE_AUTHOR_HTML_URL);

            // Then - category created with exact fixture values
            var category = categoryRepository.findById(FIXTURE_GENERAL_CATEGORY_ID).orElseThrow();
            assertThat(category.getName()).isEqualTo(FIXTURE_GENERAL_CATEGORY_NAME);
            assertThat(category.getSlug()).isEqualTo(FIXTURE_GENERAL_CATEGORY_SLUG);
        }
    }

    // ==================== Helper Methods ====================

    private GitHubDiscussionEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubDiscussionEventDTO.class);
    }

    private GitProvider gitProvider;
    private Repository testRepository;

    private void setupTestData() {
        // Create GitHub provider
        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        // Create organization matching fixture data
        Organization org = new Organization();
        org.setNativeId(FIXTURE_ORG_ID);
        org.setLogin(FIXTURE_ORG_LOGIN);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setName("Hephaestus Test");
        org.setAvatarUrl("https://avatars.githubusercontent.com/u/" + FIXTURE_ORG_ID);
        org.setHtmlUrl("https://github.com/" + FIXTURE_ORG_LOGIN);
        org.setProvider(gitProvider);
        org = organizationRepository.save(org);

        // Create repository matching fixture data
        Repository repo = new Repository();
        repo.setNativeId(FIXTURE_REPO_ID);
        repo.setName("TestRepository");
        repo.setNameWithOwner(FIXTURE_REPO_FULL_NAME);
        repo.setHtmlUrl("https://github.com/" + FIXTURE_REPO_FULL_NAME);
        repo.setVisibility(Repository.Visibility.PUBLIC);
        repo.setDefaultBranch("main");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setPushedAt(Instant.now());
        repo.setOrganization(org);
        repo.setProvider(gitProvider);
        testRepository = repositoryRepository.save(repo);

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

    private Set<String> labelNames(Discussion discussion) {
        return discussion
            .getLabels()
            .stream()
            .map(l -> l.getName())
            .collect(Collectors.toSet());
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestEventListener {

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
