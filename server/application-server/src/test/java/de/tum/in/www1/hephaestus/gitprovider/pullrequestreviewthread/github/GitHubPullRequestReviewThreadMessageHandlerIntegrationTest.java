package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.GitHubPullRequestReviewCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Pull Request Review Thread Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubPullRequestReviewThreadMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewThreadMessageHandler handler;

    @Autowired
    private GitHubPullRequestReviewCommentSyncService commentSyncService;

    @Autowired
    private PullRequestReviewThreadRepository threadRepository;

    @Autowired
    private PullRequestReviewRepository reviewRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private GitHubPullRequestConverter pullRequestConverter;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("should update thread state on resolve and unresolve events")
    void threadEventsUpdateState(
        @GitHubPayload(
            "pull_request_review_comment.created.thread-1"
        ) GHEventPayload.PullRequestReviewComment rootComment,
        @GitHubPayload(
            "pull_request_review_comment.created.thread-2"
        ) GHEventPayload.PullRequestReviewComment replyComment,
        @GitHubPayload("pull_request_review_thread.resolved") GHEventPayloadPullRequestReviewThread resolvedPayload,
        @GitHubPayload("pull_request_review_thread.unresolved") GHEventPayloadPullRequestReviewThread unresolvedPayload
    ) throws Exception {
        // Arrange - ensure prerequisite review data and comments are stored
        ensureReviewExists(rootComment);
        commentSyncService.processPullRequestReviewComment(rootComment.getComment(), rootComment.getPullRequest());
        ensureReviewExists(replyComment);
        commentSyncService.processPullRequestReviewComment(replyComment.getComment(), replyComment.getPullRequest());

        var threadId = rootComment.getComment().getId();
        var initialThread = threadRepository.findById(threadId).orElseThrow();
        assertThat(initialThread.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED);

        // Act - resolve event
        handler.handleEvent(resolvedPayload);

        // Assert resolved state
        var resolvedThread = threadRepository.findById(threadId).orElseThrow();
        assertThat(resolvedThread.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED);
        assertThat(resolvedThread.getResolvedAt()).isNotNull();

        // Act - unresolved event
        handler.handleEvent(unresolvedPayload);

        // Assert unresolved state
        var unresolvedThread = threadRepository.findById(threadId).orElseThrow();
        assertThat(unresolvedThread.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED);
        assertThat(unresolvedThread.getResolvedAt()).isNull();
    }

    private void ensureReviewExists(GHEventPayload.PullRequestReviewComment payload) {
        var reviewId = payload.getComment().getPullRequestReviewId();
        if (reviewId == null || reviewRepository.findById(reviewId).isPresent()) {
            return;
        }

        var review = new PullRequestReview();
        review.setId(reviewId);
        review.setState(PullRequestReview.State.COMMENTED);
        review.setHtmlUrl(payload.getComment().getHtmlUrl().toString());
        review.setSubmittedAt(Instant.now());
        var pullRequest = payload.getPullRequest();
        if (pullRequest != null) {
            var pullRequestEntity = pullRequestRepository
                .findById(pullRequest.getId())
                .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));
            review.setPullRequest(pullRequestEntity);
        }
        reviewRepository.save(review);
    }
}
