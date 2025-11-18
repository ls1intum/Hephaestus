package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("GitHub Pull Request Review Thread Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubPullRequestReviewThreadMessageHandlerIntegrationTest extends BaseIntegrationTest {

    private static final long ROOT_COMMENT_ID = 2494208170L;
    private static final Instant RESOLVED_TIMESTAMP = Instant.parse("2025-11-05T12:14:01Z");

    @Autowired
    private GitHubPullRequestReviewThreadMessageHandler handler;

    @Autowired
    private PullRequestReviewThreadRepository threadRepository;

    @Autowired
    private PullRequestReviewCommentRepository commentRepository;

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
    @DisplayName("resolved events mark threads as resolved and stamp timestamps")
    void resolvedEventUpdatesThread(
        @GitHubPayload("pull_request_review_thread.resolved") GHEventPayloadPullRequestReviewThread payload
    ) throws Exception {
        // Arrange
        seedPullRequestAndReviews(payload);

        // Act
        handler.handleEvent(payload);

        // Assert
        var thread = threadRepository.findWithCommentsById(ROOT_COMMENT_ID).orElseThrow();
        assertThat(thread.getState()).isEqualTo(PullRequestReviewThread.State.RESOLVED);
        assertThat(thread.getResolvedAt()).isEqualTo(RESOLVED_TIMESTAMP);
        assertThat(thread.getUpdatedAt()).isEqualTo(RESOLVED_TIMESTAMP);
        assertThat(thread.getRootComment()).isNotNull();
        assertThat(thread.getComments()).hasSize(2);
        assertThat(thread.getProviderThreadId()).isEqualTo(ROOT_COMMENT_ID);
        assertThat(thread.getNodeId()).isEqualTo("PRRT_kwDOQNibEc5gpi6h");
        assertThat(thread.getPath()).isEqualTo("README.md");
        assertThat(thread.getLine()).isEqualTo(5);
        assertThat(thread.getStartLine()).isNull();
        assertThat(thread.getSide()).isEqualTo(PullRequestReviewComment.Side.RIGHT);
        assertThat(thread.getStartSide()).isEqualTo(PullRequestReviewComment.Side.UNKNOWN);
        assertThat(thread.getOutdated()).isNull();
        assertThat(thread.getCollapsed()).isNull();
        assertThat(thread.getResolvedBy()).isNull();

        var comments = commentRepository.findAll();
        assertThat(comments).hasSize(2);
        assertThat(comments).allMatch(comment -> comment.getThread().getId().equals(ROOT_COMMENT_ID));
    }

    @Test
    @DisplayName("unresolved events reopen threads without duplicating comments")
    void unresolvedEventReopensThread(
        @GitHubPayload("pull_request_review_thread.resolved") GHEventPayloadPullRequestReviewThread resolved,
        @GitHubPayload("pull_request_review_thread.unresolved") GHEventPayloadPullRequestReviewThread unresolved
    ) throws Exception {
        // Arrange
        seedPullRequestAndReviews(resolved);
        handler.handleEvent(resolved);

        // Act
        handler.handleEvent(unresolved);

        // Assert
        var thread = threadRepository.findWithCommentsById(ROOT_COMMENT_ID).orElseThrow();
        assertThat(thread.getState()).isEqualTo(PullRequestReviewThread.State.UNRESOLVED);
        assertThat(thread.getResolvedAt()).isNull();
        assertThat(thread.getUpdatedAt()).isEqualTo(RESOLVED_TIMESTAMP);
        assertThat(thread.getComments()).hasSize(2);
        assertThat(thread.getResolvedBy()).isNull();
        assertThat(thread.getOutdated()).isNull();
        assertThat(thread.getCollapsed()).isNull();
        assertThat(thread.getStartSide()).isEqualTo(PullRequestReviewComment.Side.UNKNOWN);

        assertThat(commentRepository.count()).isEqualTo(2);
        assertThat(threadRepository.count()).isEqualTo(1);
    }

    private void seedPullRequestAndReviews(GHEventPayloadPullRequestReviewThread payload) {
        PullRequest pullRequest = pullRequestRepository
            .findById(payload.getPullRequest().getId())
            .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(payload.getPullRequest())));

        Set<Long> reviewIds = payload
            .getThread()
            .getComments()
            .stream()
            .map(org.kohsuke.github.GHPullRequestReviewComment::getPullRequestReviewId)
            .filter(id -> id != null && id > 0)
            .collect(java.util.stream.Collectors.toSet());

        reviewIds.forEach(id -> {
            if (reviewRepository.existsById(id)) {
                return;
            }
            PullRequestReview review = new PullRequestReview();
            review.setId(id);
            review.setState(PullRequestReview.State.COMMENTED);
            review.setDismissed(false);
            review.setHtmlUrl(
                "https://github.com/HephaestusTest/payload-fixture-repo-renamed/pull/3#pullrequestreview-" + id
            );
            review.setSubmittedAt(Instant.parse("2025-11-05T12:13:46Z"));
            review.setPullRequest(pullRequest);
            reviewRepository.save(review);
        });
    }
}
