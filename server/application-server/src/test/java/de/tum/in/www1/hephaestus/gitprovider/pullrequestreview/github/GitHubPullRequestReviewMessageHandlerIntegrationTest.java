package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.contribution.ContributionEventRepository;
import de.tum.in.www1.hephaestus.gitprovider.contribution.ContributionSourceType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Pull Request Review Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubPullRequestReviewMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewMessageHandler handler;

    @Autowired
    private PullRequestReviewRepository reviewRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private GitHubPullRequestConverter pullRequestConverter;

    @Autowired
    private ContributionEventRepository contributionEventRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should persist submitted reviews")
    void submittedEventPersistsReview(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var review = reviewRepository.findById(payload.getReview().getId());
        assertThat(review)
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.getState()).isEqualTo(PullRequestReview.State.APPROVED);
                assertThat(saved.isDismissed()).isFalse();
                assertThat(saved.getHtmlUrl()).isEqualTo(payload.getReview().getHtmlUrl().toString());
                assertThat(saved.getSubmittedAt()).isEqualTo(payload.getReview().getSubmittedAt());
            });
    }

    @Test
    @DisplayName("should persist contribution events for submitted reviews")
    void submittedEventCreatesContributionEvent(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview payload
    ) throws Exception {
        handler.handleEvent(payload);

        var event = contributionEventRepository
            .findBySourceTypeAndSourceId(ContributionSourceType.PULL_REQUEST_REVIEW, payload.getReview().getId())
            .orElseThrow();

        assertThat(event.getActor()).isNotNull();
        assertThat(event.getActor().getId()).isEqualTo(payload.getReview().getUser().getId());
        assertThat(event.getOccurredAt()).isEqualTo(payload.getReview().getSubmittedAt());
        assertThat(event.getXpAwarded()).isZero();
    }

    @Test
    @DisplayName("should link submitted reviews to pull requests and authors")
    void submittedEventLinksReviewAndAuthor(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview payload
    ) throws Exception {
        // Act
        handler.handleEvent(payload);

        // Assert
        var review = reviewRepository.findById(payload.getReview().getId()).orElseThrow();
        assertThat(review.getPullRequest()).isNotNull();
        assertThat(review.getPullRequest().getId()).isEqualTo(payload.getPullRequest().getId());
        assertThat(review.getAuthor()).isNotNull();
        assertThat(review.getAuthor().getId()).isEqualTo(payload.getReview().getUser().getId());
        assertThat(review.getAuthor().getLogin()).isEqualTo(payload.getReview().getUser().getLogin());
        assertThat(review.getCommitId()).isEqualTo(payload.getReview().getCommitId());

        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(pullRequestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should ignore duplicate submitted events")
    void replayedSubmittedEventIsIdempotent(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview payload
    ) throws Exception {
        // Arrange
        handler.handleEvent(payload);
        var original = reviewRepository.findById(payload.getReview().getId()).orElseThrow();
        var originalSubmittedAt = original.getSubmittedAt();

        // Act
        handler.handleEvent(payload);

        // Assert
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(pullRequestRepository.count()).isEqualTo(1);

        var review = reviewRepository.findById(payload.getReview().getId()).orElseThrow();
        assertThat(review.getSubmittedAt()).isEqualTo(originalSubmittedAt);
        assertThat(review.getBody()).isEqualTo(payload.getReview().getBody());
        assertThat(review.isDismissed()).isFalse();
    }

    @Test
    @DisplayName("should upsert contribution events idempotently on replay")
    void replayedSubmittedEventUpsertsContributionEvent(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview payload
    ) throws Exception {
        handler.handleEvent(payload);
        handler.handleEvent(payload);

        assertThat(contributionEventRepository.count()).isEqualTo(1);

        var event = contributionEventRepository
            .findBySourceTypeAndSourceId(ContributionSourceType.PULL_REQUEST_REVIEW, payload.getReview().getId())
            .orElseThrow();
        assertThat(event.getOccurredAt()).isEqualTo(payload.getReview().getSubmittedAt());
    }

    @Test
    @DisplayName("should retain review state when dismissed")
    void dismissedEventMarksReview(
        @GitHubPayload("pull_request_review.dismissed") GHEventPayload.PullRequestReview dismissed
    ) throws Exception {
        // Arrange
        var pullRequest = dismissed.getPullRequest();
        var pullRequestEntity = pullRequestRepository
            .findById(pullRequest.getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));

        PullRequestReview existingReview = new PullRequestReview();
        existingReview.setId(dismissed.getReview().getId());
        existingReview.setState(PullRequestReview.State.APPROVED);
        existingReview.setDismissed(false);
        existingReview.setHtmlUrl(dismissed.getReview().getHtmlUrl().toString());
        existingReview.setSubmittedAt(Instant.now());
        existingReview.setPullRequest(pullRequestEntity);
        reviewRepository.save(existingReview);

        // Act
        handler.handleEvent(dismissed);

        // Assert
        var review = reviewRepository.findById(dismissed.getReview().getId()).orElseThrow();
        assertThat(review.getState()).isEqualTo(PullRequestReview.State.APPROVED);
        assertThat(review.isDismissed()).isTrue();
    }

    @Test
    @DisplayName("should update existing reviews on edit events")
    void editedEventUpdatesReview(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview submitted,
        @GitHubPayload("pull_request_review.edited") GHEventPayload.PullRequestReview edited
    ) throws Exception {
        // Arrange
        handler.handleEvent(submitted);

        // Act
        handler.handleEvent(edited);

        // Assert
        var review = reviewRepository.findById(submitted.getReview().getId()).orElseThrow();
        assertThat(review.getBody()).isEqualTo(edited.getReview().getBody());
        assertThat(review.getSubmittedAt()).isEqualTo(edited.getReview().getSubmittedAt());
    }

    @Test
    @DisplayName("should keep dismissal flag when edits arrive")
    void editedEventKeepsDismissedState(
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview submitted,
        @GitHubPayload("pull_request_review.edited") GHEventPayload.PullRequestReview edited
    ) throws Exception {
        // Arrange
        handler.handleEvent(submitted);
        var review = reviewRepository.findById(submitted.getReview().getId()).orElseThrow();
        review.setDismissed(true);
        reviewRepository.save(review);

        // Act
        handler.handleEvent(edited);

        // Assert
        var updated = reviewRepository.findById(submitted.getReview().getId()).orElseThrow();
        assertThat(updated.isDismissed()).isTrue();
        assertThat(updated.getState()).isEqualTo(PullRequestReview.State.APPROVED);
        assertThat(updated.getBody()).isEqualTo(edited.getReview().getBody());
    }
}
