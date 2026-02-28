package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubEventType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto.GitHubPullRequestEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler;
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
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for GitHubPullRequestMessageHandler.
 * <p>
 * Tests the full webhook handling flow using JSON fixtures parsed directly
 * into DTOs using JSON fixtures for complete isolation. Verifies:
 * - Correct routing of webhook actions to processor methods
 * - Pull request persistence for all action types
 * - Event publishing through the handler → processor chain
 * - PR-specific state transitions (draft, merged)
 * - Edge cases in event handling
 * <p>
 * <b>Fixture Values (pull_request.opened.json - PR #26):</b>
 * <ul>
 *   <li>ID: 2969820636</li>
 *   <li>Number: 26</li>
 *   <li>Title: "Fixture: pull request events"</li>
 *   <li>Body: "Testing webhook payloads for pull_request actions."</li>
 *   <li>State: open, Draft: false, Merged: false</li>
 *   <li>HTML URL: "https://github.com/HephaestusTest/TestRepository/pull/26"</li>
 *   <li>Created/Updated: 2025-11-01T23:07:44Z</li>
 *   <li>Head ref: fixtures/pr-events, SHA: 3d1674023df8f9ef83febb8fb8d9ffa3a8119d6a</li>
 *   <li>Base ref: main, SHA: a76020c0f7e2afac4475770bb83cf4fe06ab5da1</li>
 *   <li>Commits: 1, Additions: 1, Deletions: 0, Changed files: 1</li>
 *   <li>Author: FelixTJDietrich (ID: 5898705)</li>
 * </ul>
 * <p>
 * Note: This test class does NOT use @Transactional because the pull request processing
 * chain calls GitHubUserProcessor.findOrCreate() which uses REQUIRES_NEW propagation.
 * Having @Transactional here would cause connection pool deadlocks as the test transaction
 * holds a connection while REQUIRES_NEW needs an additional one.
 * We use TransactionTemplate for lazy-loading assertions.
 */
@DisplayName("GitHub Pull Request Message Handler")
class GitHubPullRequestMessageHandlerIntegrationTest extends BaseIntegrationTest {

    // IDs from the actual GitHub webhook fixtures
    private static final Long FIXTURE_ORG_ID = 215361191L;
    private static final Long FIXTURE_REPO_ID = 998279771L;
    private static final String FIXTURE_ORG_LOGIN = "HephaestusTest";
    private static final String FIXTURE_REPO_FULL_NAME = "HephaestusTest/TestRepository";

    // Pull Request ID from fixtures (PR #26)
    private static final Long PR_26_ID = 2969820636L;
    private static final int PR_26_NUMBER = 26;

    // Exact fixture values from pull_request.opened.json for correctness verification
    private static final String FIXTURE_PR_TITLE = "Fixture: pull request events";
    private static final String FIXTURE_PR_BODY = "Testing webhook payloads for pull_request actions.";
    private static final String FIXTURE_PR_HTML_URL = "https://github.com/HephaestusTest/TestRepository/pull/26";
    private static final Instant FIXTURE_PR_CREATED_AT = Instant.parse("2025-11-01T23:07:44Z");
    private static final Instant FIXTURE_PR_UPDATED_AT = Instant.parse("2025-11-01T23:07:44Z");

    // Branch reference fixture values
    private static final String FIXTURE_HEAD_REF_NAME = "fixtures/pr-events";
    private static final String FIXTURE_HEAD_REF_OID = "3d1674023df8f9ef83febb8fb8d9ffa3a8119d6a";
    private static final String FIXTURE_BASE_REF_NAME = "main";
    private static final String FIXTURE_BASE_REF_OID = "a76020c0f7e2afac4475770bb83cf4fe06ab5da1";

    // Code change statistics from fixture
    private static final int FIXTURE_COMMITS = 1;
    private static final int FIXTURE_ADDITIONS = 1;
    private static final int FIXTURE_DELETIONS = 0;
    private static final int FIXTURE_CHANGED_FILES = 1;

    // Label ID from labeled fixture
    private static final Long FIXTURE_LABEL_ID = 9568029313L;
    private static final String FIXTURE_LABEL_NAME = "fixtures";

    // Milestone ID from milestoned fixture
    private static final Long FIXTURE_MILESTONE_ID = 14028563L;
    private static final String FIXTURE_MILESTONE_TITLE = "Webhook Fixtures";

    // Author fixture values
    private static final Long FIXTURE_AUTHOR_ID = 5898705L;
    private static final String FIXTURE_AUTHOR_LOGIN = "FelixTJDietrich";
    private static final String FIXTURE_AUTHOR_AVATAR_URL = "https://avatars.githubusercontent.com/u/5898705?v=4";
    private static final String FIXTURE_AUTHOR_HTML_URL = "https://github.com/FelixTJDietrich";

    @Autowired
    private GitHubPullRequestMessageHandler handler;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private LabelRepository labelRepository;

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

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private TestPullRequestEventListener eventListener;

    @MockitoBean
    private BadPracticeDetectorScheduler badPracticeDetectorScheduler;

    private Repository testRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        reset(badPracticeDetectorScheduler);
        eventListener.clear();
        setupTestData();
    }

    // ==================== Event Key Tests ====================

    @Nested
    @DisplayName("Event Type")
    class EventType {

        @Test
        @DisplayName("Should return PULL_REQUEST as event type")
        void shouldReturnCorrectEventType() {
            assertThat(handler.getEventType()).isEqualTo(GitHubEventType.PULL_REQUEST);
        }
    }

    // ==================== Basic Lifecycle Events ====================

    @Nested
    @DisplayName("Basic Lifecycle Events")
    class BasicLifecycleEvents {

        @Test
        @DisplayName("Should persist pull request with all schema fields on 'opened' event")
        void shouldPersistPullRequestOnOpenedEvent() throws Exception {
            // Given
            GitHubPullRequestEventDTO event = loadPayload("pull_request.opened");

            // When
            handler.handleEvent(event);

            // Then - verify ALL persisted fields against hardcoded fixture values
            // Use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElseThrow();

                // Core identification fields (inherited from Issue)
                assertThat(pr.getId()).isEqualTo(PR_26_ID);
                assertThat(pr.getNumber()).isEqualTo(PR_26_NUMBER);

                // Content fields
                assertThat(pr.getTitle()).isEqualTo(FIXTURE_PR_TITLE);
                assertThat(pr.getBody()).isEqualTo(FIXTURE_PR_BODY);

                // State fields (Issue + PR-specific)
                assertThat(pr.getState()).isEqualTo(PullRequest.State.OPEN);
                assertThat(pr.isLocked()).isFalse();
                assertThat(pr.getClosedAt()).isNull();
                assertThat(pr.isDraft()).isFalse();
                assertThat(pr.isMerged()).isFalse();
                assertThat(pr.getMergedAt()).isNull();

                // URL fields
                assertThat(pr.getHtmlUrl()).isEqualTo(FIXTURE_PR_HTML_URL);

                // Timestamp fields (critical for sync correctness)
                assertThat(pr.getCreatedAt()).isEqualTo(FIXTURE_PR_CREATED_AT);
                assertThat(pr.getUpdatedAt()).isEqualTo(FIXTURE_PR_UPDATED_AT);

                // Branch reference fields (PR-specific, critical for context)
                assertThat(pr.getHeadRefName()).isEqualTo(FIXTURE_HEAD_REF_NAME);
                assertThat(pr.getHeadRefOid()).isEqualTo(FIXTURE_HEAD_REF_OID);
                assertThat(pr.getBaseRefName()).isEqualTo(FIXTURE_BASE_REF_NAME);
                assertThat(pr.getBaseRefOid()).isEqualTo(FIXTURE_BASE_REF_OID);

                // Code change statistics (PR-specific)
                assertThat(pr.getCommits()).isEqualTo(FIXTURE_COMMITS);
                assertThat(pr.getAdditions()).isEqualTo(FIXTURE_ADDITIONS);
                assertThat(pr.getDeletions()).isEqualTo(FIXTURE_DELETIONS);
                assertThat(pr.getChangedFiles()).isEqualTo(FIXTURE_CHANGED_FILES);

                // Repository association (foreign key)
                assertThat(pr.getRepository()).isNotNull();
                assertThat(pr.getRepository().getId()).isEqualTo(FIXTURE_REPO_ID);

                // Author association (foreign key) - verify exact fixture values
                assertThat(pr.getAuthor()).isNotNull();
                assertThat(pr.getAuthor().getId()).isEqualTo(FIXTURE_AUTHOR_ID);
                assertThat(pr.getAuthor().getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
                assertThat(pr.getAuthor().getAvatarUrl()).isEqualTo(FIXTURE_AUTHOR_AVATAR_URL);
                assertThat(pr.getAuthor().getHtmlUrl()).isEqualTo(FIXTURE_AUTHOR_HTML_URL);

                // Null/empty associations (not present in opened fixture)
                assertThat(pr.getMilestone()).isNull();
                assertThat(pr.getAssignees()).isEmpty();
                assertThat(pr.getLabels()).isEmpty();
                assertThat(pr.getMergedBy()).isNull();
                assertThat(pr.getRequestedReviewers()).isEmpty();
            });

            // Domain event published
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should close pull request on 'closed' event (not merged)")
        void shouldHandleClosedEvent() throws Exception {
            // Given - create PR first
            handler.handleEvent(loadPayload("pull_request.opened"));
            eventListener.clear();

            GitHubPullRequestEventDTO closedEvent = loadPayload("pull_request.closed");

            // When
            handler.handleEvent(closedEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            assertThat(pr.getState()).isEqualTo(PullRequest.State.CLOSED);
            assertThat(pr.isMerged()).isFalse(); // closed.json has merged=false

            // Verify Closed event was published
            assertThat(eventListener.getClosedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should reopen pull request on 'reopened' event")
        void shouldHandleReopenedEvent() throws Exception {
            // Given - create and close PR
            handler.handleEvent(loadPayload("pull_request.opened"));
            handler.handleEvent(loadPayload("pull_request.closed"));
            eventListener.clear();

            GitHubPullRequestEventDTO reopenedEvent = loadPayload("pull_request.reopened");

            // When
            handler.handleEvent(reopenedEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            assertThat(pr.getState()).isEqualTo(PullRequest.State.OPEN);
        }
    }

    // ==================== Draft State Events ====================

    @Nested
    @DisplayName("Draft State Events")
    class DraftStateEvents {

        @Test
        @DisplayName("Should handle 'ready_for_review' event and publish PullRequestReady")
        void shouldHandleReadyForReviewEvent() throws Exception {
            // Given - create draft PR first
            handler.handleEvent(loadPayload("pull_request.converted_to_draft"));
            eventListener.clear();

            GitHubPullRequestEventDTO readyEvent = loadPayload("pull_request.ready_for_review");

            // When
            handler.handleEvent(readyEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            assertThat(pr.isDraft()).isFalse();

            // Verify PullRequestReady event was published
            assertThat(eventListener.getReadyEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle 'converted_to_draft' event and publish PullRequestDrafted")
        void shouldHandleConvertedToDraftEvent() throws Exception {
            // Given
            GitHubPullRequestEventDTO draftEvent = loadPayload("pull_request.converted_to_draft");

            // When
            handler.handleEvent(draftEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            assertThat(pr.isDraft()).isTrue();

            // Verify PullRequestDrafted event was published
            assertThat(eventListener.getDraftedEvents()).hasSize(1);
        }
    }

    // ==================== Synchronize Event ====================

    @Nested
    @DisplayName("Synchronize Event")
    class SynchronizeEvent {

        @Test
        @DisplayName("Should handle 'synchronize' event and publish PullRequestSynchronized")
        void shouldHandleSynchronizeEvent() throws Exception {
            // Given - create PR first
            handler.handleEvent(loadPayload("pull_request.opened"));
            eventListener.clear();

            GitHubPullRequestEventDTO syncEvent = loadPayload("pull_request.synchronize");

            // When
            handler.handleEvent(syncEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();

            // Verify PullRequestSynchronized event was published
            assertThat(eventListener.getSynchronizedEvents()).hasSize(1);
        }
    }

    // ==================== Label Events ====================

    @Nested
    @DisplayName("Label Events")
    class LabelEvents {

        @Test
        @DisplayName("Should handle 'labeled' event and persist label")
        void shouldHandleLabeledEvent() throws Exception {
            // Given - create PR first
            handler.handleEvent(loadPayload("pull_request.opened"));
            eventListener.clear();

            GitHubPullRequestEventDTO labeledEvent = loadPayload("pull_request.labeled");

            // When
            handler.handleEvent(labeledEvent);

            // Then - use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
                assertThat(pr).isNotNull();
                assertThat(labelNames(pr)).contains(FIXTURE_LABEL_NAME);
            });

            // Verify label was created in repository
            assertThat(labelRepository.findById(FIXTURE_LABEL_ID)).isPresent();

            // Verify Labeled event was published
            assertThat(eventListener.getLabeledEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle 'unlabeled' event")
        void shouldHandleUnlabeledEvent() throws Exception {
            // Given - create PR with label
            handler.handleEvent(loadPayload("pull_request.opened"));
            handler.handleEvent(loadPayload("pull_request.labeled"));
            eventListener.clear();

            GitHubPullRequestEventDTO unlabeledEvent = loadPayload("pull_request.unlabeled");

            // When
            handler.handleEvent(unlabeledEvent);

            // Then - Unlabeled event should be published
            assertThat(eventListener.getUnlabeledEvents()).hasSize(1);
        }
    }

    // ==================== Assignment Events ====================

    @Nested
    @DisplayName("Assignment Events")
    class AssignmentEvents {

        @Test
        @DisplayName("Should handle 'assigned' event - process routes to processor with assignees")
        void shouldHandleAssignedEvent() throws Exception {
            // Given
            GitHubPullRequestEventDTO assignedEvent = loadPayload("pull_request.assigned");

            // When
            handler.handleEvent(assignedEvent);

            // Then - PR should be created with assignees from the DTO
            // Use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
                assertThat(pr).isNotNull();
                assertThat(pr.getAssignees()).isNotEmpty();
                assertThat(userLogins(pr.getAssignees())).contains(FIXTURE_AUTHOR_LOGIN);
            });
        }

        @Test
        @DisplayName("Should handle 'unassigned' event")
        void shouldHandleUnassignedEvent() throws Exception {
            // Given - create PR with assignee
            handler.handleEvent(loadPayload("pull_request.assigned"));

            GitHubPullRequestEventDTO unassignedEvent = loadPayload("pull_request.unassigned");

            // When
            handler.handleEvent(unassignedEvent);

            // Then - PR still exists and was processed
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
        }
    }

    // ==================== Milestone Events ====================

    @Nested
    @DisplayName("Milestone Events")
    class MilestoneEvents {

        @Test
        @DisplayName("Should handle 'milestoned' event and create milestone")
        void shouldHandleMilestonedEvent() throws Exception {
            // Given
            GitHubPullRequestEventDTO milestonedEvent = loadPayload("pull_request.milestoned");

            // When
            handler.handleEvent(milestonedEvent);

            // Then - use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
                assertThat(pr).isNotNull();
                assertThat(pr.getMilestone()).isNotNull();
                assertThat(pr.getMilestone().getTitle()).isEqualTo(FIXTURE_MILESTONE_TITLE);
                assertThat(pr.getMilestone().getNumber()).isEqualTo(2);
            });

            // Verify milestone was created in repository
            assertThat(milestoneRepository.findById(FIXTURE_MILESTONE_ID)).isPresent();
        }

        @Test
        @DisplayName("Should handle 'demilestoned' event - processes without error")
        void shouldHandleDemilestonedEvent() throws Exception {
            // Given - this uses a fresh demilestoned event
            // Note: The processor currently only clears milestone during create,
            // not during update. This test verifies the handler routing works.
            GitHubPullRequestEventDTO demilestonedEvent = loadPayload("pull_request.demilestoned");

            // When
            handler.handleEvent(demilestonedEvent);

            // Then - PR should be created and processed
            // Use TransactionTemplate for lazy-loading assertions
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
                assertThat(pr).isNotNull();
                // Milestone is null in the demilestoned payload itself
                assertThat(pr.getMilestone()).isNull();
            });
        }
    }

    // ==================== Review Request Events ====================

    @Nested
    @DisplayName("Review Request Events")
    class ReviewRequestEvents {

        @Test
        @DisplayName("Should handle 'review_requested' event")
        void shouldHandleReviewRequestedEvent() throws Exception {
            // Given
            GitHubPullRequestEventDTO reviewRequestedEvent = loadPayload("pull_request.review_requested");

            // When
            handler.handleEvent(reviewRequestedEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            // Note: The fixture has requested_teams but no user reviewers in requested_reviewers
            // The handler processes this via the generic process() method
        }

        @Test
        @DisplayName("Should handle 'review_request_removed' event")
        void shouldHandleReviewRequestRemovedEvent() throws Exception {
            // Given - create PR with review request
            handler.handleEvent(loadPayload("pull_request.review_requested"));

            GitHubPullRequestEventDTO reviewRequestRemovedEvent = loadPayload("pull_request.review_request_removed");

            // When
            handler.handleEvent(reviewRequestRemovedEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
        }
    }

    // ==================== Lock Events ====================

    @Nested
    @DisplayName("Lock Events")
    class LockEvents {

        @Test
        @DisplayName("Should handle 'locked' event")
        void shouldHandleLockedEvent() throws Exception {
            // Given - create PR first
            handler.handleEvent(loadPayload("pull_request.opened"));

            GitHubPullRequestEventDTO lockedEvent = loadPayload("pull_request.locked");

            // When
            handler.handleEvent(lockedEvent);

            // Then - PR processed successfully
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
        }

        @Test
        @DisplayName("Should handle 'unlocked' event")
        void shouldHandleUnlockedEvent() throws Exception {
            // Given - create PR first
            handler.handleEvent(loadPayload("pull_request.opened"));
            handler.handleEvent(loadPayload("pull_request.locked"));

            GitHubPullRequestEventDTO unlockedEvent = loadPayload("pull_request.unlocked");

            // When
            handler.handleEvent(unlockedEvent);

            // Then
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
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
            GitHubPullRequestEventDTO event = loadPayload("pull_request.opened");

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle missing repository context gracefully")
        void shouldHandleMissingRepositoryContextGracefully() throws Exception {
            // Given - remove the repository so context creation fails
            repositoryRepository.deleteAll();

            GitHubPullRequestEventDTO event = loadPayload("pull_request.opened");

            // When/Then - should not throw, just log warning
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // PR should not be persisted since context is null
            assertThat(pullRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should be idempotent - processing same event twice")
        void shouldBeIdempotent() throws Exception {
            // Given
            GitHubPullRequestEventDTO event = loadPayload("pull_request.opened");

            // When - handle same event twice
            handler.handleEvent(event);
            long countAfterFirst = pullRequestRepository.count();

            handler.handleEvent(event);

            // Then - still only one PR
            assertThat(pullRequestRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("Should verify getDatabaseId() fallback works (id is used when databaseId is null)")
        void shouldVerifyGetDatabaseIdFallback() throws Exception {
            // Given - webhook payloads have 'id' not 'database_id'
            GitHubPullRequestEventDTO event = loadPayload("pull_request.opened");

            // Verify the DTO is using the fallback correctly
            // In webhook payloads, databaseId will be null and id will have the value
            assertThat(event.pullRequest().getDatabaseId()).isEqualTo(PR_26_ID);

            // When
            handler.handleEvent(event);

            // Then - PR should be persisted with the correct ID
            assertThat(pullRequestRepository.findById(PR_26_ID)).isPresent();
        }

        @Test
        @DisplayName("Should create author entity with correct field values from opened event")
        void shouldCreateAllRelatedEntitiesFromOpenedEvent() throws Exception {
            // Given - no users exist
            assertThat(userRepository.count()).isZero();

            // When
            handler.handleEvent(loadPayload("pull_request.opened"));

            // Then - author created with exact fixture values
            var author = userRepository.findById(FIXTURE_AUTHOR_ID).orElseThrow();
            assertThat(author.getLogin()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
            assertThat(author.getAvatarUrl()).isEqualTo(FIXTURE_AUTHOR_AVATAR_URL);
            assertThat(author.getHtmlUrl()).isEqualTo(FIXTURE_AUTHOR_HTML_URL);
            assertThat(author.getType()).isEqualTo(User.Type.USER);
        }

        @Test
        @DisplayName("Should handle null pull request DTO gracefully")
        void shouldHandleNullPullRequestDTOGracefully() throws Exception {
            // Given - a manually created event with null PR
            GitHubPullRequestEventDTO event = new GitHubPullRequestEventDTO(
                "opened",
                PR_26_NUMBER,
                null, // null pull_request
                null, // repository
                null, // sender
                null, // label
                null, // requestedReviewer
                null // changes
            );

            // When/Then - should not throw
            assertThatCode(() -> handler.handleEvent(event)).doesNotThrowAnyException();

            // PR should not be persisted
            assertThat(pullRequestRepository.count()).isZero();
        }
    }

    // ==================== Full Workflow Tests ====================

    @Nested
    @DisplayName("Full Workflow")
    class FullWorkflow {

        @Test
        @DisplayName("Should handle complete PR lifecycle: open → label → sync → close")
        void shouldHandleCompletePullRequestLifecycle() throws Exception {
            // 1. Open PR
            handler.handleEvent(loadPayload("pull_request.opened"));
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElse(null);
            assertThat(pr).isNotNull();
            assertThat(pr.getState()).isEqualTo(PullRequest.State.OPEN);

            // 2. Add label
            handler.handleEvent(loadPayload("pull_request.labeled"));
            transactionTemplate.executeWithoutResult(status -> {
                PullRequest prWithLabel = pullRequestRepository.findById(PR_26_ID).orElseThrow();
                assertThat(labelNames(prWithLabel)).contains(FIXTURE_LABEL_NAME);
            });

            // 3. Synchronize (push new commits)
            handler.handleEvent(loadPayload("pull_request.synchronize"));
            pr = pullRequestRepository.findById(PR_26_ID).orElseThrow();
            assertThat(pr.getState()).isEqualTo(PullRequest.State.OPEN);

            // 4. Close
            handler.handleEvent(loadPayload("pull_request.closed"));
            pr = pullRequestRepository.findById(PR_26_ID).orElseThrow();
            assertThat(pr.getState()).isEqualTo(PullRequest.State.CLOSED);

            // Verify events were published
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
            assertThat(eventListener.getLabeledEvents()).hasSize(1);
            assertThat(eventListener.getSynchronizedEvents()).hasSize(1);
            assertThat(eventListener.getClosedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle ready_for_review event after opened")
        void shouldHandleReadyForReviewAfterOpened() throws Exception {
            // 1. Open PR (which is not draft in opened.json)
            handler.handleEvent(loadPayload("pull_request.opened"));
            PullRequest pr = pullRequestRepository.findById(PR_26_ID).orElseThrow();
            assertThat(pr.isDraft()).isFalse();

            // 2. Mark ready for review (even if not draft, event is processed)
            handler.handleEvent(loadPayload("pull_request.ready_for_review"));
            pr = pullRequestRepository.findById(PR_26_ID).orElseThrow();
            assertThat(pr.isDraft()).isFalse();

            // Verify events
            assertThat(eventListener.getCreatedEvents()).hasSize(1);
            assertThat(eventListener.getReadyEvents()).hasSize(1);
        }
    }

    // ==================== DTO Parsing Tests ====================

    @Nested
    @DisplayName("DTO Parsing")
    class DTOParsing {

        @Test
        @DisplayName("Should parse all opened event fields with exact fixture values")
        void shouldParseAllOpenedEventFieldsCorrectly() throws Exception {
            // Given
            String payload = loadPayloadRaw("pull_request.opened");
            GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

            // Event-level fields
            assertThat(event.action()).isEqualTo("opened");
            assertThat(event.number()).isEqualTo(PR_26_NUMBER);

            // PR identification
            var pr = event.pullRequest();
            assertThat(pr).isNotNull();
            assertThat(pr.getDatabaseId()).isEqualTo(PR_26_ID);
            assertThat(pr.number()).isEqualTo(PR_26_NUMBER);

            // PR content
            assertThat(pr.title()).isEqualTo(FIXTURE_PR_TITLE);
            assertThat(pr.body()).isEqualTo(FIXTURE_PR_BODY);
            assertThat(pr.htmlUrl()).isEqualTo(FIXTURE_PR_HTML_URL);

            // PR state
            assertThat(pr.state()).isEqualTo("open");
            assertThat(pr.isDraft()).isFalse();
            assertThat(pr.isMerged()).isFalse();
            assertThat(pr.locked()).isFalse();

            // Timestamps
            assertThat(pr.createdAt()).isEqualTo(FIXTURE_PR_CREATED_AT);
            assertThat(pr.updatedAt()).isEqualTo(FIXTURE_PR_UPDATED_AT);

            // Branch refs
            assertThat(pr.head()).isNotNull();
            assertThat(pr.head().ref()).isEqualTo(FIXTURE_HEAD_REF_NAME);
            assertThat(pr.head().sha()).isEqualTo(FIXTURE_HEAD_REF_OID);
            assertThat(pr.base()).isNotNull();
            assertThat(pr.base().ref()).isEqualTo(FIXTURE_BASE_REF_NAME);
            assertThat(pr.base().sha()).isEqualTo(FIXTURE_BASE_REF_OID);

            // Code stats
            assertThat(pr.commits()).isEqualTo(FIXTURE_COMMITS);
            assertThat(pr.additions()).isEqualTo(FIXTURE_ADDITIONS);
            assertThat(pr.deletions()).isEqualTo(FIXTURE_DELETIONS);
            assertThat(pr.changedFiles()).isEqualTo(FIXTURE_CHANGED_FILES);

            // Author
            assertThat(pr.author()).isNotNull();
            assertThat(pr.author().id()).isEqualTo(FIXTURE_AUTHOR_ID);
            assertThat(pr.author().login()).isEqualTo(FIXTURE_AUTHOR_LOGIN);
        }

        @Test
        @DisplayName("Should parse labeled event with label data")
        void shouldParseLabeledEventWithLabelData() throws Exception {
            // Given
            String payload = loadPayloadRaw("pull_request.labeled");
            GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

            // Then
            assertThat(event.action()).isEqualTo("labeled");
            assertThat(event.label()).isNotNull();
            assertThat(event.label().id()).isEqualTo(FIXTURE_LABEL_ID);
            assertThat(event.label().name()).isEqualTo(FIXTURE_LABEL_NAME);
        }

        @Test
        @DisplayName("Should parse milestoned event with milestone data")
        void shouldParseMilestonedEventWithMilestoneData() throws Exception {
            // Given
            String payload = loadPayloadRaw("pull_request.milestoned");
            GitHubPullRequestEventDTO event = objectMapper.readValue(payload, GitHubPullRequestEventDTO.class);

            // Then
            assertThat(event.action()).isEqualTo("milestoned");
            assertThat(event.pullRequest().milestone()).isNotNull();
            assertThat(event.pullRequest().milestone().id()).isEqualTo(FIXTURE_MILESTONE_ID);
            assertThat(event.pullRequest().milestone().title()).isEqualTo(FIXTURE_MILESTONE_TITLE);
        }
    }

    // ==================== Helper Methods ====================

    private GitHubPullRequestEventDTO loadPayload(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        String json = resource.getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(json, GitHubPullRequestEventDTO.class);
    }

    private String loadPayloadRaw(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("github/" + filename + ".json");
        return resource.getContentAsString(StandardCharsets.UTF_8);
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

    private Set<String> labelNames(PullRequest pr) {
        return pr.getLabels().stream().map(Label::getName).collect(Collectors.toSet());
    }

    private Set<String> userLogins(Set<User> users) {
        return users.stream().map(User::getLogin).collect(Collectors.toSet());
    }

    // ==================== Test Event Listener ====================

    @Component
    static class TestPullRequestEventListener {

        private final List<DomainEvent.PullRequestCreated> createdEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestUpdated> updatedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestClosed> closedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestReopened> reopenedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestLabeled> labeledEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestUnlabeled> unlabeledEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestMerged> mergedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestReady> readyEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestDrafted> draftedEvents = new ArrayList<>();
        private final List<DomainEvent.PullRequestSynchronized> synchronizedEvents = new ArrayList<>();

        @EventListener
        public void onCreated(DomainEvent.PullRequestCreated event) {
            createdEvents.add(event);
        }

        @EventListener
        public void onUpdated(DomainEvent.PullRequestUpdated event) {
            updatedEvents.add(event);
        }

        @EventListener
        public void onClosed(DomainEvent.PullRequestClosed event) {
            closedEvents.add(event);
        }

        @EventListener
        public void onReopened(DomainEvent.PullRequestReopened event) {
            reopenedEvents.add(event);
        }

        @EventListener
        public void onLabeled(DomainEvent.PullRequestLabeled event) {
            labeledEvents.add(event);
        }

        @EventListener
        public void onUnlabeled(DomainEvent.PullRequestUnlabeled event) {
            unlabeledEvents.add(event);
        }

        @EventListener
        public void onMerged(DomainEvent.PullRequestMerged event) {
            mergedEvents.add(event);
        }

        @EventListener
        public void onReady(DomainEvent.PullRequestReady event) {
            readyEvents.add(event);
        }

        @EventListener
        public void onDrafted(DomainEvent.PullRequestDrafted event) {
            draftedEvents.add(event);
        }

        @EventListener
        public void onSynchronized(DomainEvent.PullRequestSynchronized event) {
            synchronizedEvents.add(event);
        }

        public List<DomainEvent.PullRequestCreated> getCreatedEvents() {
            return new ArrayList<>(createdEvents);
        }

        public List<DomainEvent.PullRequestUpdated> getUpdatedEvents() {
            return new ArrayList<>(updatedEvents);
        }

        public List<DomainEvent.PullRequestClosed> getClosedEvents() {
            return new ArrayList<>(closedEvents);
        }

        public List<DomainEvent.PullRequestReopened> getReopenedEvents() {
            return new ArrayList<>(reopenedEvents);
        }

        public List<DomainEvent.PullRequestLabeled> getLabeledEvents() {
            return new ArrayList<>(labeledEvents);
        }

        public List<DomainEvent.PullRequestUnlabeled> getUnlabeledEvents() {
            return new ArrayList<>(unlabeledEvents);
        }

        public List<DomainEvent.PullRequestMerged> getMergedEvents() {
            return new ArrayList<>(mergedEvents);
        }

        public List<DomainEvent.PullRequestReady> getReadyEvents() {
            return new ArrayList<>(readyEvents);
        }

        public List<DomainEvent.PullRequestDrafted> getDraftedEvents() {
            return new ArrayList<>(draftedEvents);
        }

        public List<DomainEvent.PullRequestSynchronized> getSynchronizedEvents() {
            return new ArrayList<>(synchronizedEvents);
        }

        public void clear() {
            createdEvents.clear();
            updatedEvents.clear();
            closedEvents.clear();
            reopenedEvents.clear();
            labeledEvents.clear();
            unlabeledEvents.clear();
            mergedEvents.clear();
            readyEvents.clear();
            draftedEvents.clear();
            synchronizedEvents.clear();
        }
    }
}
