package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.GitHubPullRequestReviewSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Pull Request Review Comment Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubPullRequestReviewCommentMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewCommentMessageHandler handler;

    @Autowired
    private GitHubPullRequestReviewSyncService reviewSyncService;

    @Autowired
    private PullRequestReviewCommentRepository commentRepository;

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
    @DisplayName("should persist newly created review comments and threads")
    void createdEventPersistsCommentAndThread(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment commentPayload,
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview reviewPayload
    ) throws Exception {
        // Arrange
        reviewSyncService.processPullRequestReview(reviewPayload.getReview());
        assertThat(commentRepository.findById(commentPayload.getComment().getId())).isEmpty();

        // Act
        handler.handleEvent(commentPayload);

        // Assert
        var savedComment = commentRepository.findById(commentPayload.getComment().getId());
        assertThat(savedComment)
            .isPresent()
            .get()
            .satisfies(comment -> {
                assertThat(comment.getBody()).isEqualTo(commentPayload.getComment().getBody());
                assertThat(comment.getHtmlUrl()).isEqualTo(commentPayload.getComment().getHtmlUrl().toString());
                assertThat(comment.getLine()).isEqualTo(commentPayload.getComment().getLine());
                assertThat(comment.getSide().name()).isEqualTo(commentPayload.getComment().getSide().name());
                assertThat(comment.getThread()).isNotNull();
                assertThat(comment.getThread().getId()).isEqualTo(comment.getId());
                assertThat(comment.getThread().getState()).isEqualTo(
                    de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED
                );
            });

        assertThat(threadRepository.findById(commentPayload.getComment().getId())).isPresent();
    }

    @Test
    @DisplayName("should ignore duplicate create events for the same review comment")
    void createdEventIsIdempotent(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment commentPayload,
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview reviewPayload
    ) throws Exception {
        // Arrange
        reviewSyncService.processPullRequestReview(reviewPayload.getReview());
        handler.handleEvent(commentPayload);
        var existing = commentRepository.findById(commentPayload.getComment().getId()).orElseThrow();
        var originalUpdatedAt = existing.getUpdatedAt();

        // Act
        handler.handleEvent(commentPayload);

        // Assert
        assertThat(commentRepository.count()).isEqualTo(1);
        assertThat(threadRepository.count()).isEqualTo(1);
        var replayed = commentRepository.findById(commentPayload.getComment().getId()).orElseThrow();
        assertThat(replayed.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(replayed.getThread().getComments()).hasSize(1);
    }

    @Test
    @DisplayName("should update existing comments on edit events")
    void editedEventUpdatesComment(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment createdPayload,
        @GitHubPayload("pull_request_review_comment.edited") GHEventPayload.PullRequestReviewComment editedPayload,
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview reviewPayload
    ) throws Exception {
        // Arrange
        reviewSyncService.processPullRequestReview(reviewPayload.getReview());
        handler.handleEvent(createdPayload);
        var original = commentRepository.findById(createdPayload.getComment().getId()).orElseThrow();
        assertThat(original.getBody()).isEqualTo(createdPayload.getComment().getBody());

        // Act
        handler.handleEvent(editedPayload);

        // Assert
        var updated = commentRepository.findById(editedPayload.getComment().getId()).orElseThrow();
        assertThat(updated.getBody()).isEqualTo(editedPayload.getComment().getBody());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
    }

    @Test
    @DisplayName("should remove comments and associated threads on delete events")
    void deletedEventRemovesCommentAndThread(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment createdPayload,
        @GitHubPayload("pull_request_review_comment.deleted") GHEventPayload.PullRequestReviewComment deletedPayload,
        @GitHubPayload("pull_request_review.submitted") GHEventPayload.PullRequestReview reviewPayload
    ) throws Exception {
        // Arrange
        reviewSyncService.processPullRequestReview(reviewPayload.getReview());
        handler.handleEvent(createdPayload);
        assertThat(commentRepository.findById(createdPayload.getComment().getId())).isPresent();
        assertThat(threadRepository.findById(createdPayload.getComment().getId())).isPresent();

        // Act
        handler.handleEvent(deletedPayload);

        // Assert
        assertThat(commentRepository.findById(createdPayload.getComment().getId())).isEmpty();
        assertThat(threadRepository.findById(createdPayload.getComment().getId())).isEmpty();
    }

    @Test
    @DisplayName("should persist replies and link them to the root thread")
    void createdReplyLinksToThread(
        @GitHubPayload(
            "pull_request_review_comment.created.thread-1"
        ) GHEventPayload.PullRequestReviewComment rootPayload,
        @GitHubPayload(
            "pull_request_review_comment.created.thread-2"
        ) GHEventPayload.PullRequestReviewComment replyPayload
    ) throws Exception {
        // Arrange root review
        ensureReviewExists(rootPayload);

        handler.handleEvent(rootPayload);
        var rootComment = commentRepository.findById(rootPayload.getComment().getId()).orElseThrow();
        assertThat(rootComment.getThread()).isNotNull();

        // Act
        ensureReviewExists(replyPayload);
        handler.handleEvent(replyPayload);

        // Assert
        var replyComment = commentRepository.findById(replyPayload.getComment().getId()).orElseThrow();
        assertThat(replyComment.getInReplyTo()).isNotNull();
        assertThat(replyComment.getInReplyTo().getId()).isEqualTo(rootPayload.getComment().getId());
        assertThat(replyComment.getThread()).isNotNull();
        var threadId = rootPayload.getComment().getId();
        assertThat(replyComment.getThread().getId()).isEqualTo(threadId);
        assertThat(commentRepository.countByThreadId(threadId)).isEqualTo(2);
    }

    @Test
    @DisplayName("should retain threads when a reply is deleted")
    void deletedReplyKeepsThread(
        @GitHubPayload(
            "pull_request_review_comment.created.thread-1"
        ) GHEventPayload.PullRequestReviewComment rootPayload,
        @GitHubPayload(
            "pull_request_review_comment.created.thread-2"
        ) GHEventPayload.PullRequestReviewComment replyPayload,
        @GitHubPayload(
            "pull_request_review_comment.deleted.thread-2"
        ) GHEventPayload.PullRequestReviewComment deletedReplyPayload
    ) throws Exception {
        // Arrange
        ensureReviewExists(rootPayload);
        handler.handleEvent(rootPayload);
        ensureReviewExists(replyPayload);
        handler.handleEvent(replyPayload);
        var threadId = rootPayload.getComment().getId();
        assertThat(threadRepository.findById(threadId)).isPresent();
        assertThat(commentRepository.findById(replyPayload.getComment().getId())).isPresent();

        // Act
        handler.handleEvent(deletedReplyPayload);

        // Assert
        assertThat(commentRepository.findById(replyPayload.getComment().getId())).isEmpty();
        var thread = threadRepository.findById(threadId).orElseThrow();
        assertThat(commentRepository.countByThreadId(threadId)).isEqualTo(1);
        assertThat(thread.getRootComment()).isNotNull();
        assertThat(thread.getRootComment().getId()).isEqualTo(threadId);
    }

    private void ensureReviewExists(GHEventPayload.PullRequestReviewComment payload) throws Exception {
        var reviewId = payload.getComment().getPullRequestReviewId();
        if (reviewId == null) {
            return;
        }

        if (reviewRepository.findById(reviewId).isPresent()) {
            return;
        }

        var pullRequest = payload.getPullRequest();
        var pullRequestEntity = pullRequestRepository
            .findById(pullRequest.getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));

        PullRequestReview review = new PullRequestReview();
        review.setId(reviewId);
        review.setState(PullRequestReview.State.COMMENTED);
        review.setHtmlUrl(payload.getComment().getHtmlUrl().toString());
        review.setSubmittedAt(Instant.now());
        review.setPullRequest(pullRequestEntity);
        reviewRepository.save(review);
    }
}
