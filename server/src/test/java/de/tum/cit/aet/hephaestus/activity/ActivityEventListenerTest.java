package de.tum.cit.aet.hephaestus.activity;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.Commit;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.discussion.Discussion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.discussioncomment.DiscussionComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for ActivityEventListener.
 *
 * <p>Tests verify that activity events are correctly recorded using event payload data
 * and getReferenceById() for entity references (no N+1 queries).
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityEventListenerTest extends BaseUnitTest {

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private ActivityEventRepository activityEventRepository;

    @Mock
    private ExperiencePointCalculator experiencePointCalculator;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private PullRequestReviewThreadRepository reviewThreadRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private IssueRepository issueRepository;

    private ActivityEventListener listener;

    private User testUser;
    private Repository testRepository;

    @BeforeEach
    void setUp() {
        // Set up default XP values for the mock
        when(experiencePointCalculator.getXpPullRequestOpened()).thenReturn(
            ExperiencePointCalculator.XP_PULL_REQUEST_OPENED
        );
        when(experiencePointCalculator.getXpPullRequestMerged()).thenReturn(
            ExperiencePointCalculator.XP_PULL_REQUEST_MERGED
        );
        when(experiencePointCalculator.getXpReviewComment()).thenReturn(ExperiencePointCalculator.XP_REVIEW_COMMENT);
        when(experiencePointCalculator.getXpPullRequestReady()).thenReturn(0.5);
        when(experiencePointCalculator.getXpIssueCreated()).thenReturn(0.25);
        when(experiencePointCalculator.getXpCommitCreated()).thenReturn(0.5);
        when(experiencePointCalculator.getXpDiscussionCreated()).thenReturn(0.25);
        when(experiencePointCalculator.getXpDiscussionAnswered()).thenReturn(0.5);
        when(experiencePointCalculator.getXpDiscussionCommentCreated()).thenReturn(0.25);

        listener = new ActivityEventListener(
            activityEventService,
            activityEventRepository,
            experiencePointCalculator,
            reviewRepository,
            pullRequestRepository,
            issueCommentRepository,
            reviewThreadRepository,
            userRepository,
            repositoryRepository,
            issueRepository
        );

        testUser = new User();
        testUser.setId(100L);
        testUser.setLogin("testuser");

        testRepository = new Repository();
        testRepository.setId(200L);
        testRepository.setName("test-repo");

        // Set up getReferenceById mocks - these return proxy references without DB queries
        when(userRepository.getReferenceById(100L)).thenReturn(testUser);
        when(repositoryRepository.getReferenceById(200L)).thenReturn(testRepository);
        when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));
    }

    @Nested
    class PullRequestCreatedTests {

        @Test
        void recordsPullRequestOpened() {
            PullRequest pullRequest = createPullRequest(1L);

            var event = new ScmDomainEvent.PullRequestCreated(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.PULL_REQUEST_OPENED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.PULL_REQUEST),
                eq(1L),
                eq(ExperiencePointCalculator.XP_PULL_REQUEST_OPENED)
            );
            // Verify no findById was called (N+1 fix)
            verify(userRepository).getReferenceById(100L);
            verify(repositoryRepository).getReferenceById(200L);
        }

        @Test
        void noOpWhenAuthorIdMissing() {
            PullRequest pullRequest = createPullRequest(1L);
            pullRequest.setAuthor(null); // No author

            var event = new ScmDomainEvent.PullRequestCreated(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    class PullRequestMergedTests {

        @Test
        void recordsPullRequestMerged() {
            PullRequest pullRequest = createPullRequest(2L);
            pullRequest.setMergedAt(Instant.now());
            pullRequest.setMergedBy(testUser);

            var event = new ScmDomainEvent.PullRequestMerged(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestMerged(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.PULL_REQUEST_MERGED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.PULL_REQUEST),
                eq(2L),
                eq(ExperiencePointCalculator.XP_PULL_REQUEST_MERGED)
            );
        }
    }

    @Nested
    class PullRequestClosedTests {

        @Test
        void recordsPullRequestClosedWithZeroXp() {
            PullRequest pullRequest = createPullRequest(3L);
            pullRequest.setClosedAt(Instant.now());

            var event = new ScmDomainEvent.PullRequestClosed(
                createPullRequestData(pullRequest),
                false,
                createContext()
            );

            listener.onPullRequestClosed(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.PULL_REQUEST_CLOSED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.PULL_REQUEST),
                eq(3L),
                eq(0.0)
            );
        }

        @Test
        @DisplayName("does nothing when pull request was merged (handled by onPullRequestMerged)")
        void noOpWhenMerged() {
            var event = new ScmDomainEvent.PullRequestClosed(
                createPullRequestData(createPullRequest(4L)),
                true,
                createContext()
            );

            listener.onPullRequestClosed(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    class PullRequestReopenedTests {

        @Test
        void recordsPullRequestReopenedWithZeroXp() {
            PullRequest pullRequest = createPullRequest(5L);

            var event = new ScmDomainEvent.PullRequestReopened(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestReopened(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.PULL_REQUEST_REOPENED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.PULL_REQUEST),
                eq(5L),
                eq(0.0)
            );
        }
    }

    @Nested
    class PullRequestReadyTests {

        @Test
        void recordsPullRequestReady() {
            PullRequest pullRequest = createPullRequest(6L);

            var event = new ScmDomainEvent.PullRequestReady(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestReady(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.PULL_REQUEST_READY),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.PULL_REQUEST),
                eq(6L),
                eq(0.5) // XP from mock
            );
        }
    }

    @Nested
    class ReviewSubmittedTests {

        @Test
        @DisplayName("maps APPROVED review state to REVIEW_APPROVED event type using event data")
        void usesExperiencePointCalculatorForReviewXp() {
            PullRequest pullRequest = createPullRequest(10L);
            PullRequestReview review = createReview(5L, pullRequest);
            review.setState(PullRequestReview.State.APPROVED);
            // Mock findById to return the single review for XP calculation
            when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));
            when(experiencePointCalculator.calculateReviewExperiencePoints(review)).thenReturn(7.5);

            var event = new ScmDomainEvent.ReviewSubmitted(createReviewData(review), createContext());

            listener.onReviewSubmitted(event);

            verify(experiencePointCalculator).calculateReviewExperiencePoints(review);
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.REVIEW_APPROVED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.REVIEW),
                eq(5L),
                eq(7.5)
            );
        }

        @Test
        void handlesMissingAuthor() {
            PullRequest pullRequest = createPullRequest(1L);
            PullRequestReview review = createReview(6L, pullRequest);
            review.setAuthor(null); // No author

            var event = new ScmDomainEvent.ReviewSubmitted(createReviewData(review), createContext());

            listener.onReviewSubmitted(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    class IssueCreatedTests {

        @Test
        void recordsIssueCreatedWithXp() {
            Issue issue = createIssue(10L);

            var event = new ScmDomainEvent.IssueCreated(createIssueData(issue), createContext());

            listener.onIssueCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.ISSUE_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.ISSUE),
                eq(10L),
                eq(0.25) // XP from mock
            );
        }

        @Test
        void skipsPullRequests() {
            PullRequest pullRequest = createPullRequest(11L);

            var event = new ScmDomainEvent.IssueCreated(ScmEventPayload.IssueData.from(pullRequest), createContext());

            listener.onIssueCreated(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        void recordsIssueWithNullAuthorAndZeroXp() {
            // Create issue WITHOUT author - simulates deleted GitHub user or bot
            Issue issue = createIssue(14L);
            issue.setAuthor(null);

            var event = new ScmDomainEvent.IssueCreated(createIssueData(issue), createContext());

            listener.onIssueCreated(event);

            // Event is STILL recorded (for audit trail), but with null actor and 0 XP
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.ISSUE_CREATED),
                any(Instant.class),
                isNull(), // null actor - user deleted or bot
                eq(testRepository),
                eq(ActivityTargetType.ISSUE),
                eq(14L),
                eq(0.0) // Zero XP for unknown authors
            );
        }
    }

    @Nested
    class IssueClosedTests {

        @Test
        void recordsIssueClosedWithZeroXp() {
            Issue issue = createIssue(12L);
            issue.setClosedAt(Instant.now());

            var event = new ScmDomainEvent.IssueClosed(createIssueData(issue), null, createContext());

            listener.onIssueClosed(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.ISSUE_CLOSED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.ISSUE),
                eq(12L),
                eq(0.0)
            );
        }

        @Test
        void skipsPullRequests() {
            PullRequest pullRequest = createPullRequest(13L);

            var event = new ScmDomainEvent.IssueClosed(
                ScmEventPayload.IssueData.from(pullRequest),
                null,
                createContext()
            );

            listener.onIssueClosed(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        void recordsIssueClosedWithNullAuthor() {
            // Create issue WITHOUT author - simulates deleted GitHub user or bot
            Issue issue = createIssue(15L);
            issue.setAuthor(null);
            issue.setClosedAt(Instant.now());

            var event = new ScmDomainEvent.IssueClosed(createIssueData(issue), null, createContext());

            listener.onIssueClosed(event);

            // Event is STILL recorded (for audit trail), but with null actor
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.ISSUE_CLOSED),
                any(Instant.class),
                isNull(), // null actor - user deleted or bot
                eq(testRepository),
                eq(ActivityTargetType.ISSUE),
                eq(15L),
                eq(0.0) // Issue closure has 0 XP anyway
            );
        }
    }

    @Nested
    class CommitCreatedTests {

        @Test
        void recordsCommitCreatedWithXp() {
            Commit commit = createCommit(20L);

            var event = new ScmDomainEvent.CommitCreated(createCommitData(commit), createContext());

            listener.onCommitCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.COMMIT_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.COMMIT),
                eq(20L),
                eq(0.5) // XP from mock
            );
        }

        @Test
        void recordsCommitWithNullAuthorAndZeroXp() {
            Commit commit = createCommit(21L);
            commit.setAuthor(null);

            var event = new ScmDomainEvent.CommitCreated(createCommitData(commit), createContext());

            listener.onCommitCreated(event);

            // Event is STILL recorded (for audit trail), but with null actor and 0 XP
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.COMMIT_CREATED),
                any(Instant.class),
                isNull(), // null actor - user deleted or bot
                eq(testRepository),
                eq(ActivityTargetType.COMMIT),
                eq(21L),
                eq(0.0) // Zero XP for unknown authors
            );
        }

        @Test
        void skipsCommitWhenScopeIdIsNull() {
            Commit commit = createCommit(22L);

            RepositoryRef repoRef = new RepositoryRef(testRepository.getId(), testRepository.getName(), "test");
            EventContext contextWithNullScope = new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                null, // null scopeId
                repoRef,
                DataSource.WEBHOOK,
                null,
                UUID.randomUUID().toString(),
                null
            );
            var event = new ScmDomainEvent.CommitCreated(createCommitData(commit), contextWithNullScope);

            listener.onCommitCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    class DiscussionCreatedTests {

        @Test
        void recordsDiscussionCreatedWithXp() {
            Discussion discussion = createDiscussion(30L);

            var event = new ScmDomainEvent.DiscussionCreated(createDiscussionData(discussion), createContext());

            listener.onDiscussionCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(30L),
                eq(0.25) // XP from mock
            );
        }

        @Test
        void recordsDiscussionWithNullAuthorAndZeroXp() {
            Discussion discussion = createDiscussion(31L);
            discussion.setAuthor(null);

            var event = new ScmDomainEvent.DiscussionCreated(createDiscussionData(discussion), createContext());

            listener.onDiscussionCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_CREATED),
                any(Instant.class),
                isNull(),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(31L),
                eq(0.0)
            );
        }
    }

    @Nested
    class DiscussionClosedTests {

        @Test
        void recordsDiscussionClosedWithZeroXp() {
            Discussion discussion = createDiscussion(32L);
            discussion.setClosedAt(Instant.now());

            var event = new ScmDomainEvent.DiscussionClosed(
                createDiscussionData(discussion),
                "resolved",
                createContext()
            );

            listener.onDiscussionClosed(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_CLOSED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(32L),
                eq(0.0)
            );
        }
    }

    @Nested
    class DiscussionReopenedTests {

        @Test
        void recordsDiscussionReopenedWithZeroXp() {
            Discussion discussion = createDiscussion(33L);

            var event = new ScmDomainEvent.DiscussionReopened(createDiscussionData(discussion), createContext());

            listener.onDiscussionReopened(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_REOPENED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(33L),
                eq(0.0)
            );
        }
    }

    @Nested
    class DiscussionAnsweredTests {

        @Test
        void recordsDiscussionAnsweredWithXp() {
            Discussion discussion = createDiscussion(34L);
            discussion.setAnswerChosenAt(Instant.now());

            var event = new ScmDomainEvent.DiscussionAnswered(createDiscussionData(discussion), 999L, createContext());

            listener.onDiscussionAnswered(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_ANSWERED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(34L),
                eq(0.5) // XP from mock
            );
        }

        @Test
        void recordsDiscussionAnsweredWithNullAuthorAndZeroXp() {
            Discussion discussion = createDiscussion(35L);
            discussion.setAuthor(null);
            discussion.setAnswerChosenAt(Instant.now());

            var event = new ScmDomainEvent.DiscussionAnswered(createDiscussionData(discussion), 999L, createContext());

            listener.onDiscussionAnswered(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_ANSWERED),
                any(Instant.class),
                isNull(),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION),
                eq(35L),
                eq(0.0)
            );
        }
    }

    @Nested
    class DiscussionDeletedTests {

        @Test
        void recordsDiscussionDeleted() {
            var event = new ScmDomainEvent.DiscussionDeleted(36L, createContext());

            listener.onDiscussionDeleted(event);

            verify(activityEventService).recordDeleted(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_DELETED),
                any(Instant.class),
                eq(ActivityTargetType.DISCUSSION),
                eq(36L)
            );
        }
    }

    @Nested
    class ReviewCommentCreatedTests {

        @Test
        void onReviewCommentCreated_standaloneComment_awardsXp() {
            PullRequest pullRequest = createPullRequest(50L);
            // Different author so it's not a self-review
            User prAuthor = new User();
            prAuthor.setId(999L);
            prAuthor.setLogin("pr-author");
            pullRequest.setAuthor(prAuthor);

            when(pullRequestRepository.findById(50L)).thenReturn(Optional.of(pullRequest));
            when(experiencePointCalculator.calculateStandaloneReviewCommentXp(any(), any(), anyInt())).thenReturn(0.5);

            var commentData = new ScmEventPayload.ReviewCommentData(
                77L, // id
                "This is a substantive review comment with enough length", // body
                "src/Main.java", // path
                42, // line
                "https://github.com/test/test-repo/pull/1#discussion_r77", // htmlUrl
                null, // reviewId - null = standalone
                100L, // authorId
                Instant.now(), // createdAt
                50L, // pullRequestId
                200L // repositoryId
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 50L, createContext());

            listener.onReviewCommentCreated(event);

            verify(pullRequestRepository).findById(50L);
            verify(experiencePointCalculator).calculateStandaloneReviewCommentXp(eq(pullRequest), eq(100L), anyInt());
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.REVIEW_COMMENT_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.REVIEW_COMMENT),
                eq(77L),
                eq(0.5)
            );
        }

        @Test
        void onReviewCommentCreated_linkedToReview_awardsZeroXp() {
            var commentData = new ScmEventPayload.ReviewCommentData(
                78L, // id
                "Some comment", // body
                "src/Main.java", // path
                10, // line
                "https://github.com/test/test-repo/pull/1#discussion_r78", // htmlUrl
                42L, // reviewId - linked to review
                100L, // authorId
                Instant.now(), // createdAt
                50L, // pullRequestId
                200L // repositoryId
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 50L, createContext());

            listener.onReviewCommentCreated(event);

            // pullRequestRepository.findById should never be called for linked comments
            verify(pullRequestRepository, never()).findById(any());
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.REVIEW_COMMENT_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.REVIEW_COMMENT),
                eq(78L),
                eq(0.0)
            );
        }

        @Test
        void onReviewCommentCreated_nullScopeId_skips() {
            var commentData = new ScmEventPayload.ReviewCommentData(
                79L,
                "body",
                "path.java",
                1,
                "https://example.com/comment",
                null,
                100L,
                Instant.now(),
                50L,
                200L
            );
            RepositoryRef repoRef = new RepositoryRef(testRepository.getId(), testRepository.getName(), "test");
            EventContext contextWithNullScope = new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                null, // null scopeId
                repoRef,
                DataSource.WEBHOOK,
                null,
                UUID.randomUUID().toString(),
                null
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 50L, contextWithNullScope);

            listener.onReviewCommentCreated(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        void onReviewCommentCreated_nullAuthorId_skips() {
            var commentData = new ScmEventPayload.ReviewCommentData(
                80L,
                "body",
                "path.java",
                1,
                "https://example.com/comment",
                null, // reviewId
                null, // authorId - null
                Instant.now(),
                50L,
                200L
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 50L, createContext());

            listener.onReviewCommentCreated(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        void onReviewCommentCreated_prNotFound_recordsZeroXp() {
            when(pullRequestRepository.findById(999L)).thenReturn(Optional.empty());

            var commentData = new ScmEventPayload.ReviewCommentData(
                81L,
                "This comment's PR doesn't exist yet",
                "src/Main.java",
                1,
                "https://example.com/comment",
                null, // reviewId - standalone
                100L, // authorId
                Instant.now(),
                999L, // pullRequestId - not found
                200L
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 999L, createContext());

            listener.onReviewCommentCreated(event);

            verify(pullRequestRepository).findById(999L);
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.REVIEW_COMMENT_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.REVIEW_COMMENT),
                eq(81L),
                eq(0.0) // Zero XP because PR not found
            );
        }

        @Test
        void onReviewCommentCreated_nullBody_passesZeroLength() {
            PullRequest pullRequest = createPullRequest(50L);
            User prAuthor = new User();
            prAuthor.setId(999L);
            prAuthor.setLogin("pr-author");
            pullRequest.setAuthor(prAuthor);

            when(pullRequestRepository.findById(50L)).thenReturn(Optional.of(pullRequest));
            when(experiencePointCalculator.calculateStandaloneReviewCommentXp(any(), any(), eq(0))).thenReturn(0.25);

            var commentData = new ScmEventPayload.ReviewCommentData(
                82L,
                null, // null body
                "src/Main.java",
                1,
                "https://example.com/comment",
                null, // reviewId - standalone
                100L,
                Instant.now(),
                50L,
                200L
            );
            var event = new ScmDomainEvent.ReviewCommentCreated(commentData, 50L, createContext());

            listener.onReviewCommentCreated(event);

            // Verify body length 0 was passed to calculator (not NPE)
            verify(experiencePointCalculator).calculateStandaloneReviewCommentXp(eq(pullRequest), eq(100L), eq(0));
        }
    }

    @Nested
    class DiscussionCommentCreatedTests {

        @Test
        void recordsDiscussionCommentCreatedWithXp() {
            DiscussionComment comment = createDiscussionComment(37L);

            var event = new ScmDomainEvent.DiscussionCommentCreated(
                createDiscussionCommentData(comment),
                30L, // discussionId
                createContext()
            );

            listener.onDiscussionCommentCreated(event);

            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.DISCUSSION_COMMENT_CREATED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.DISCUSSION_COMMENT),
                eq(37L),
                eq(0.25) // XP from mock
            );
        }

        @Test
        void skipsCommentWhenAuthorIsNull() {
            DiscussionComment comment = createDiscussionComment(38L);
            comment.setAuthor(null);

            var event = new ScmDomainEvent.DiscussionCommentCreated(
                createDiscussionCommentData(comment),
                30L,
                createContext()
            );

            listener.onDiscussionCommentCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    class CommitAuthorsReconciledTests {

        @Test
        void backfillsCommitActorsOnReconciliation() {
            when(activityEventRepository.backfillCommitActors(eq(200L), eq(0.5))).thenReturn(3);

            var event = new ScmDomainEvent.CommitAuthorsReconciled(200L, createContext());

            listener.onCommitAuthorsReconciled(event);

            verify(activityEventRepository).backfillCommitActors(200L, 0.5);
        }

        @Test
        void noOpWhenRepositoryIdMissing() {
            var event = new ScmDomainEvent.CommitAuthorsReconciled(null, createContext());

            listener.onCommitAuthorsReconciled(event);

            verify(activityEventRepository, never()).backfillCommitActors(anyLong(), anyDouble());
        }

        @Test
        void swallowsBackfillExceptions() {
            when(activityEventRepository.backfillCommitActors(eq(200L), anyDouble())).thenThrow(
                new RuntimeException("db outage")
            );

            var event = new ScmDomainEvent.CommitAuthorsReconciled(200L, createContext());

            listener.onCommitAuthorsReconciled(event);

            verify(activityEventRepository).backfillCommitActors(200L, 0.5);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Commit createCommit(Long id) {
        Commit commit = new Commit();
        commit.setId(id);
        commit.setSha("abc123def456789012345678901234567890abcd");
        commit.setMessage("Test commit message");
        commit.setAuthoredAt(Instant.now());
        commit.setCommittedAt(Instant.now());
        commit.setAuthor(testUser);
        commit.setRepository(testRepository);
        return commit;
    }

    private ScmEventPayload.CommitData createCommitData(Commit commit) {
        return ScmEventPayload.CommitData.from(commit);
    }

    private PullRequest createPullRequest(Long id) {
        PullRequest pullRequest = new PullRequest();
        pullRequest.setId(id);
        pullRequest.setNumber(1);
        pullRequest.setTitle("Test Pull Request");
        pullRequest.setState(PullRequest.State.OPEN);
        pullRequest.setAuthor(testUser);
        pullRequest.setRepository(testRepository);
        pullRequest.setCreatedAt(Instant.now());
        pullRequest.setUpdatedAt(Instant.now());
        pullRequest.setHtmlUrl("https://github.com/test/test-repo/pull/1");
        return pullRequest;
    }

    private Issue createIssue(Long id) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setNumber(100);
        issue.setTitle("Test Issue");
        issue.setState(Issue.State.OPEN);
        issue.setAuthor(testUser);
        issue.setRepository(testRepository);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue.setHtmlUrl("https://github.com/test/test-repo/issues/100");
        return issue;
    }

    private PullRequestReview createReview(Long id, PullRequest pullRequest) {
        PullRequestReview review = new PullRequestReview();
        review.setId(id);
        review.setAuthor(testUser);
        review.setPullRequest(pullRequest);
        review.setSubmittedAt(Instant.now());
        review.setState(PullRequestReview.State.APPROVED);
        return review;
    }

    private ScmEventPayload.PullRequestData createPullRequestData(PullRequest pullRequest) {
        return ScmEventPayload.PullRequestData.from(pullRequest);
    }

    private ScmEventPayload.ReviewData createReviewData(PullRequestReview review) {
        return ScmEventPayload.ReviewData.from(review).orElseThrow();
    }

    private ScmEventPayload.IssueData createIssueData(Issue issue) {
        return ScmEventPayload.IssueData.from(issue);
    }

    private EventContext createContext() {
        RepositoryRef repoRef = new RepositoryRef(testRepository.getId(), testRepository.getName(), "test");
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            42L,
            repoRef,
            DataSource.WEBHOOK,
            null,
            UUID.randomUUID().toString(),
            null
        );
    }

    private Discussion createDiscussion(Long id) {
        Discussion discussion = new Discussion();
        discussion.setId(id);
        discussion.setNumber(100);
        discussion.setTitle("Test Discussion");
        discussion.setState(Discussion.State.OPEN);
        discussion.setAuthor(testUser);
        discussion.setRepository(testRepository);
        discussion.setCreatedAt(Instant.now());
        discussion.setUpdatedAt(Instant.now());
        discussion.setHtmlUrl("https://github.com/test/test-repo/discussions/100");
        return discussion;
    }

    private DiscussionComment createDiscussionComment(Long id) {
        Discussion discussion = createDiscussion(30L);
        DiscussionComment comment = new DiscussionComment();
        comment.setId(id);
        comment.setBody("Test comment");
        comment.setHtmlUrl("https://github.com/test/test-repo/discussions/100#discussioncomment-" + id);
        comment.setAuthor(testUser);
        comment.setDiscussion(discussion);
        comment.setCreatedAt(Instant.now());
        return comment;
    }

    private ScmEventPayload.DiscussionData createDiscussionData(Discussion discussion) {
        return ScmEventPayload.DiscussionData.from(discussion);
    }

    private ScmEventPayload.DiscussionCommentData createDiscussionCommentData(DiscussionComment comment) {
        return ScmEventPayload.DiscussionCommentData.from(comment);
    }
}
