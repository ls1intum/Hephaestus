package de.tum.in.www1.hephaestus.activity;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
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
    private ExperiencePointCalculator experiencePointCalculator;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private PullRequestReviewThreadRepository reviewThreadRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ProjectRepository projectRepository;

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
            experiencePointCalculator,
            reviewRepository,
            issueCommentRepository,
            reviewThreadRepository,
            userRepository,
            repositoryRepository,
            projectRepository,
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
    @DisplayName("Pull Request Created Event")
    class PullRequestCreatedTests {

        @Test
        @DisplayName("records pull request opened with fixed XP using event data directly")
        void recordsPullRequestOpened() {
            PullRequest pullRequest = createPullRequest(1L);

            var event = new DomainEvent.PullRequestCreated(createPullRequestData(pullRequest), createContext());

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
        @DisplayName("does nothing when authorId is missing from event data")
        void noOpWhenAuthorIdMissing() {
            PullRequest pullRequest = createPullRequest(1L);
            pullRequest.setAuthor(null); // No author

            var event = new DomainEvent.PullRequestCreated(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Pull Request Merged Event")
    class PullRequestMergedTests {

        @Test
        @DisplayName("records pull request merged with fixed XP using event data")
        void recordsPullRequestMerged() {
            PullRequest pullRequest = createPullRequest(2L);
            pullRequest.setMergedAt(Instant.now());
            pullRequest.setMergedBy(testUser);

            var event = new DomainEvent.PullRequestMerged(createPullRequestData(pullRequest), createContext());

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
    @DisplayName("Pull Request Closed Event")
    class PullRequestClosedTests {

        @Test
        @DisplayName("records pull request closed with zero XP when not merged")
        void recordsPullRequestClosedWithZeroXp() {
            PullRequest pullRequest = createPullRequest(3L);
            pullRequest.setClosedAt(Instant.now());

            var event = new DomainEvent.PullRequestClosed(createPullRequestData(pullRequest), false, createContext());

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
            var event = new DomainEvent.PullRequestClosed(
                createPullRequestData(createPullRequest(4L)),
                true,
                createContext()
            );

            listener.onPullRequestClosed(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Pull Request Reopened Event")
    class PullRequestReopenedTests {

        @Test
        @DisplayName("records pull request reopened with zero XP (lifecycle tracking only)")
        void recordsPullRequestReopenedWithZeroXp() {
            PullRequest pullRequest = createPullRequest(5L);

            var event = new DomainEvent.PullRequestReopened(createPullRequestData(pullRequest), createContext());

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
    @DisplayName("Pull Request Ready Event")
    class PullRequestReadyTests {

        @Test
        @DisplayName("records pull request ready for review with XP")
        void recordsPullRequestReady() {
            PullRequest pullRequest = createPullRequest(6L);

            var event = new DomainEvent.PullRequestReady(createPullRequestData(pullRequest), createContext());

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
    @DisplayName("Review Submitted Event")
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

            var event = new DomainEvent.ReviewSubmitted(createReviewData(review), createContext());

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
        @DisplayName("handles missing author gracefully")
        void handlesMissingAuthor() {
            PullRequest pullRequest = createPullRequest(1L);
            PullRequestReview review = createReview(6L, pullRequest);
            review.setAuthor(null); // No author

            var event = new DomainEvent.ReviewSubmitted(createReviewData(review), createContext());

            listener.onReviewSubmitted(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Issue Created Event")
    class IssueCreatedTests {

        @Test
        @DisplayName("records issue created with XP for regular issues")
        void recordsIssueCreatedWithXp() {
            Issue issue = createIssue(10L);

            var event = new DomainEvent.IssueCreated(createIssueData(issue), createContext());

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
        @DisplayName("skips pull requests (they have PULL_REQUEST_OPENED event)")
        void skipsPullRequests() {
            PullRequest pullRequest = createPullRequest(11L);

            var event = new DomainEvent.IssueCreated(EventPayload.IssueData.from(pullRequest), createContext());

            listener.onIssueCreated(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        @DisplayName("records issue with null author and zero XP (deleted user or bot)")
        void recordsIssueWithNullAuthorAndZeroXp() {
            // Create issue WITHOUT author - simulates deleted GitHub user or bot
            Issue issue = createIssue(14L);
            issue.setAuthor(null);

            var event = new DomainEvent.IssueCreated(createIssueData(issue), createContext());

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
    @DisplayName("Issue Closed Event")
    class IssueClosedTests {

        @Test
        @DisplayName("records issue closed with zero XP (lifecycle tracking only)")
        void recordsIssueClosedWithZeroXp() {
            Issue issue = createIssue(12L);
            issue.setClosedAt(Instant.now());

            var event = new DomainEvent.IssueClosed(createIssueData(issue), null, createContext());

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
        @DisplayName("skips pull requests (they have their own close event)")
        void skipsPullRequests() {
            PullRequest pullRequest = createPullRequest(13L);

            var event = new DomainEvent.IssueClosed(EventPayload.IssueData.from(pullRequest), null, createContext());

            listener.onIssueClosed(event);

            verifyNoInteractions(activityEventService);
        }

        @Test
        @DisplayName("records issue closed with null author (deleted user or bot)")
        void recordsIssueClosedWithNullAuthor() {
            // Create issue WITHOUT author - simulates deleted GitHub user or bot
            Issue issue = createIssue(15L);
            issue.setAuthor(null);
            issue.setClosedAt(Instant.now());

            var event = new DomainEvent.IssueClosed(createIssueData(issue), null, createContext());

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
    @DisplayName("Commit Created Event")
    class CommitCreatedTests {

        @Test
        @DisplayName("records commit created with XP for known author")
        void recordsCommitCreatedWithXp() {
            Commit commit = createCommit(20L);

            var event = new DomainEvent.CommitCreated(createCommitData(commit), createContext());

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
        @DisplayName("records commit with null author and zero XP (deleted user or bot)")
        void recordsCommitWithNullAuthorAndZeroXp() {
            Commit commit = createCommit(21L);
            commit.setAuthor(null);

            var event = new DomainEvent.CommitCreated(createCommitData(commit), createContext());

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
        @DisplayName("skips commit when scopeId is null")
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
                UUID.randomUUID().toString()
            );
            var event = new DomainEvent.CommitCreated(createCommitData(commit), contextWithNullScope);

            listener.onCommitCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Discussion Created Event")
    class DiscussionCreatedTests {

        @Test
        @DisplayName("records discussion created with XP for known author")
        void recordsDiscussionCreatedWithXp() {
            Discussion discussion = createDiscussion(30L);

            var event = new DomainEvent.DiscussionCreated(createDiscussionData(discussion), createContext());

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
        @DisplayName("records discussion with null author and zero XP")
        void recordsDiscussionWithNullAuthorAndZeroXp() {
            Discussion discussion = createDiscussion(31L);
            discussion.setAuthor(null);

            var event = new DomainEvent.DiscussionCreated(createDiscussionData(discussion), createContext());

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
    @DisplayName("Discussion Closed Event")
    class DiscussionClosedTests {

        @Test
        @DisplayName("records discussion closed with zero XP (lifecycle tracking)")
        void recordsDiscussionClosedWithZeroXp() {
            Discussion discussion = createDiscussion(32L);
            discussion.setClosedAt(Instant.now());

            var event = new DomainEvent.DiscussionClosed(createDiscussionData(discussion), "resolved", createContext());

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
    @DisplayName("Discussion Reopened Event")
    class DiscussionReopenedTests {

        @Test
        @DisplayName("records discussion reopened with zero XP (lifecycle tracking)")
        void recordsDiscussionReopenedWithZeroXp() {
            Discussion discussion = createDiscussion(33L);

            var event = new DomainEvent.DiscussionReopened(createDiscussionData(discussion), createContext());

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
    @DisplayName("Discussion Answered Event")
    class DiscussionAnsweredTests {

        @Test
        @DisplayName("records discussion answered with XP for author")
        void recordsDiscussionAnsweredWithXp() {
            Discussion discussion = createDiscussion(34L);
            discussion.setAnswerChosenAt(Instant.now());

            var event = new DomainEvent.DiscussionAnswered(createDiscussionData(discussion), 999L, createContext());

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
        @DisplayName("records discussion answered with null author and zero XP")
        void recordsDiscussionAnsweredWithNullAuthorAndZeroXp() {
            Discussion discussion = createDiscussion(35L);
            discussion.setAuthor(null);
            discussion.setAnswerChosenAt(Instant.now());

            var event = new DomainEvent.DiscussionAnswered(createDiscussionData(discussion), 999L, createContext());

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
    @DisplayName("Discussion Deleted Event")
    class DiscussionDeletedTests {

        @Test
        @DisplayName("records discussion deleted audit trail")
        void recordsDiscussionDeleted() {
            var event = new DomainEvent.DiscussionDeleted(36L, createContext());

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
    @DisplayName("Discussion Comment Created Event")
    class DiscussionCommentCreatedTests {

        @Test
        @DisplayName("records discussion comment created with XP")
        void recordsDiscussionCommentCreatedWithXp() {
            DiscussionComment comment = createDiscussionComment(37L);

            var event = new DomainEvent.DiscussionCommentCreated(
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
        @DisplayName("skips comment when author is null")
        void skipsCommentWhenAuthorIsNull() {
            DiscussionComment comment = createDiscussionComment(38L);
            comment.setAuthor(null);

            var event = new DomainEvent.DiscussionCommentCreated(
                createDiscussionCommentData(comment),
                30L,
                createContext()
            );

            listener.onDiscussionCommentCreated(event);

            verifyNoInteractions(activityEventService);
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

    private EventPayload.CommitData createCommitData(Commit commit) {
        return EventPayload.CommitData.from(commit);
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

    private EventPayload.PullRequestData createPullRequestData(PullRequest pullRequest) {
        return EventPayload.PullRequestData.from(pullRequest);
    }

    private EventPayload.ReviewData createReviewData(PullRequestReview review) {
        return EventPayload.ReviewData.from(review).orElseThrow();
    }

    private EventPayload.IssueData createIssueData(Issue issue) {
        return EventPayload.IssueData.from(issue);
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
            UUID.randomUUID().toString()
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

    private EventPayload.DiscussionData createDiscussionData(Discussion discussion) {
        return EventPayload.DiscussionData.from(discussion);
    }

    private EventPayload.DiscussionCommentData createDiscussionCommentData(DiscussionComment comment) {
        return EventPayload.DiscussionCommentData.from(comment);
    }
}
