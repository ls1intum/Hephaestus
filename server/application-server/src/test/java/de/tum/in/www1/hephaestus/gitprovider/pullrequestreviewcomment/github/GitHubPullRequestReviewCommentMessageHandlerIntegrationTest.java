package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
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
    private PullRequestReviewCommentRepository pullRequestReviewCommentRepository;

    @Autowired
    private PullRequestReviewThreadRepository pullRequestReviewThreadRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should persist a newly created review comment and create a thread")
    void shouldPersistCreatedComment(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment payload
    ) throws Exception {
        var ghComment = payload.getComment();

        assertThat(pullRequestReviewCommentRepository.findById(ghComment.getId())).isEmpty();

        handler.handleEvent(payload);

        var persistedComment = pullRequestReviewCommentRepository.findById(ghComment.getId()).orElseThrow();
        assertThat(persistedComment.getBody()).isEqualTo(ghComment.getBody());
        assertThat(persistedComment.getCommitId()).isEqualTo(ghComment.getCommitId());
        assertThat(persistedComment.getPath()).isEqualTo(ghComment.getPath());
        assertThat(persistedComment.getLine()).isEqualTo(ghComment.getLine());
        assertThat(persistedComment.getAuthor()).isNotNull();
        assertThat(persistedComment.getThread()).isNotNull();
        assertThat(persistedComment.getThread().getId()).isEqualTo(ghComment.getId());

        var thread = pullRequestReviewThreadRepository.findById(ghComment.getId()).orElseThrow();
        assertThat(thread.getState()).isEqualTo(de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED);
        assertThat(thread.getRootComment()).isNotNull();
        assertThat(thread.getPullRequest()).isNotNull();
        assertThat(pullRequestReviewCommentRepository.existsByThreadIdAndId(thread.getId(), ghComment.getId())).isTrue();
    }

    @Test
    @DisplayName("Should link reply comments to their parent and thread")
    void shouldLinkReplyCommentToThread(
        @GitHubPayload("pull_request_review_comment.created.thread-1") GHEventPayload.PullRequestReviewComment rootPayload,
        @GitHubPayload("pull_request_review_comment.created.thread-2") GHEventPayload.PullRequestReviewComment replyPayload
    ) {
        handler.handleEvent(rootPayload);
        handler.handleEvent(replyPayload);

        var rootComment = pullRequestReviewCommentRepository.findById(rootPayload.getComment().getId()).orElseThrow();
        var replyComment = pullRequestReviewCommentRepository.findById(replyPayload.getComment().getId()).orElseThrow();

        assertThat(replyComment.getInReplyTo()).isNotNull();
        assertThat(replyComment.getInReplyTo().getId()).isEqualTo(rootComment.getId());
        assertThat(replyComment.getThread()).isNotNull();
        assertThat(replyComment.getThread().getId()).isEqualTo(rootComment.getId());
        assertThat(pullRequestReviewCommentRepository.existsByThreadIdAndId(rootComment.getId(), replyComment.getId())).isTrue();
        assertThat(pullRequestReviewCommentRepository.countByThreadId(rootComment.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Should update an existing review comment on edit events")
    void shouldUpdateCommentOnEdit(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment createdPayload,
        @GitHubPayload("pull_request_review_comment.edited") GHEventPayload.PullRequestReviewComment editedPayload
    ) throws Exception {
        handler.handleEvent(createdPayload);

        handler.handleEvent(editedPayload);

        var editedComment = pullRequestReviewCommentRepository.findById(editedPayload.getComment().getId()).orElseThrow();
        assertThat(editedComment.getBody()).isEqualTo(editedPayload.getComment().getBody());
        assertThat(editedComment.getUpdatedAt()).isEqualTo(editedPayload.getComment().getUpdatedAt());
    }

    @Test
    @DisplayName("Should delete review comments and their thread when the root is removed")
    void shouldDeleteCommentAndThread(
        @GitHubPayload("pull_request_review_comment.created") GHEventPayload.PullRequestReviewComment createdPayload,
        @GitHubPayload("pull_request_review_comment.deleted") GHEventPayload.PullRequestReviewComment deletedPayload
    ) {
        handler.handleEvent(createdPayload);
        assertThat(pullRequestReviewCommentRepository.findById(createdPayload.getComment().getId())).isPresent();

        handler.handleEvent(deletedPayload);

        assertThat(pullRequestReviewCommentRepository.findById(deletedPayload.getComment().getId())).isEmpty();
        assertThat(pullRequestReviewThreadRepository.findById(deletedPayload.getComment().getId())).isEmpty();
    }
}
