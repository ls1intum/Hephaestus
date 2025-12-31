package de.tum.in.www1.hephaestus.activity;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointCalculator;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for ActivityEventListener.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityEventListenerTest {

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private ExperiencePointCalculator experiencePointCalculator;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

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

        listener = new ActivityEventListener(
            activityEventService,
            experiencePointCalculator,
            reviewRepository,
            pullRequestRepository,
            issueCommentRepository,
            reviewCommentRepository
        );

        testUser = new User();
        testUser.setId(100L);
        testUser.setLogin("testuser");

        testRepository = new Repository();
        testRepository.setId(200L);
        testRepository.setName("test-repo");
    }

    @Nested
    @DisplayName("Pull Request Created Event")
    class PullRequestCreatedTests {

        @Test
        @DisplayName("records pull request opened with fixed XP")
        void recordsPullRequestOpened() {
            PullRequest pullRequest = createPullRequest(1L);
            when(pullRequestRepository.findById(1L)).thenReturn(Optional.of(pullRequest));

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
                eq(ExperiencePointCalculator.XP_PULL_REQUEST_OPENED),
                eq(SourceSystem.GITHUB)
            );
        }

        @Test
        @DisplayName("does nothing when pull request not found")
        void noOpWhenPullRequestNotFound() {
            PullRequest pullRequest = createPullRequest(1L);
            when(pullRequestRepository.findById(1L)).thenReturn(Optional.empty());

            var event = new DomainEvent.PullRequestCreated(createPullRequestData(pullRequest), createContext());

            listener.onPullRequestCreated(event);

            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Pull Request Merged Event")
    class PullRequestMergedTests {

        @Test
        @DisplayName("records pull request merged with fixed XP")
        void recordsPullRequestMerged() {
            PullRequest pullRequest = createPullRequest(2L);
            pullRequest.setMergedAt(Instant.now());
            pullRequest.setMergedBy(testUser);
            when(pullRequestRepository.findById(2L)).thenReturn(Optional.of(pullRequest));

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
                eq(ExperiencePointCalculator.XP_PULL_REQUEST_MERGED),
                eq(SourceSystem.GITHUB)
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
            when(pullRequestRepository.findById(3L)).thenReturn(Optional.of(pullRequest));

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
                eq(0.0),
                eq(SourceSystem.GITHUB)
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

            verifyNoInteractions(pullRequestRepository);
            verifyNoInteractions(activityEventService);
        }
    }

    @Nested
    @DisplayName("Review Submitted Event")
    class ReviewSubmittedTests {

        @Test
        @DisplayName("maps APPROVED review state to REVIEW_APPROVED event type")
        void usesExperiencePointCalculatorForReviewXp() {
            PullRequest pullRequest = createPullRequest(10L);
            PullRequestReview review = createReview(5L, pullRequest);
            review.setState(PullRequestReview.State.APPROVED);
            when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));
            when(experiencePointCalculator.calculateReviewExperiencePoints(List.of(review))).thenReturn(7.5);

            var event = new DomainEvent.ReviewSubmitted(createReviewData(review), createContext());

            listener.onReviewSubmitted(event);

            verify(experiencePointCalculator).calculateReviewExperiencePoints(List.of(review));
            verify(activityEventService).record(
                eq(42L),
                eq(ActivityEventType.REVIEW_APPROVED),
                any(Instant.class),
                eq(testUser),
                eq(testRepository),
                eq(ActivityTargetType.REVIEW),
                eq(5L),
                eq(7.5),
                eq(SourceSystem.GITHUB)
            );
        }

        @Test
        @DisplayName("handles null pull request gracefully")
        void handlesNullPullRequest() {
            PullRequestReview review = new PullRequestReview();
            review.setId(6L);
            review.setPullRequest(null);
            when(reviewRepository.findById(6L)).thenReturn(Optional.of(review));

            PullRequest pullRequest = createPullRequest(1L);
            var event = new DomainEvent.ReviewSubmitted(
                createReviewData(createReview(6L, pullRequest)),
                createContext()
            );

            listener.onReviewSubmitted(event);

            verifyNoInteractions(activityEventService);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

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
        return EventPayload.ReviewData.from(review);
    }

    private EventContext createContext() {
        RepositoryRef repoRef = new RepositoryRef(testRepository.getId(), testRepository.getName(), "test");
        return new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            42L,
            repoRef,
            EventContext.Source.WEBHOOK,
            null,
            UUID.randomUUID().toString()
        );
    }
}
