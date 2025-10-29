package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHEventPayloadPullRequestReviewThread;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Pull Request Review Thread Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubPullRequestReviewThreadMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubPullRequestReviewThreadMessageHandler threadHandler;

    @Autowired
    private PullRequestReviewThreadRepository pullRequestReviewThreadRepository;

    @BeforeEach
    void cleanDatabase() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should mark a review thread as resolved")
    void shouldResolveReviewThread(
        @GitHubPayload("pull_request_review_thread.resolved") GHEventPayloadPullRequestReviewThread resolvedThread
    ) {
        threadHandler.handleEvent(resolvedThread);

        long threadId = resolvedThread
            .getThread()
            .getComments()
            .stream()
            .filter(comment -> comment.getInReplyToId() <= 0)
            .findFirst()
            .map(GHPullRequestReviewComment::getId)
            .orElseThrow();

        var thread = pullRequestReviewThreadRepository.findById(threadId).orElseThrow();

        assertThat(thread.getState())
            .isEqualTo(de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread.State.RESOLVED);
        assertThat(thread.getResolvedAt()).isEqualTo(resolvedThread.getUpdatedAt());
        assertThat(thread.getLastActor()).isNotNull();
        assertThat(thread.getLastActor().getLogin()).isEqualTo(resolvedThread.getSender().getLogin());
    }

    @Test
    @DisplayName("Should mark a review thread as unresolved")
    void shouldUnresolveReviewThread(
        @GitHubPayload("pull_request_review_thread.resolved") GHEventPayloadPullRequestReviewThread resolvedThread,
        @GitHubPayload("pull_request_review_thread.unresolved") GHEventPayloadPullRequestReviewThread unresolvedThread
    ) {
        threadHandler.handleEvent(resolvedThread);

        threadHandler.handleEvent(unresolvedThread);

        long threadId = unresolvedThread
            .getThread()
            .getComments()
            .stream()
            .filter(comment -> comment.getInReplyToId() <= 0)
            .findFirst()
            .map(GHPullRequestReviewComment::getId)
            .orElseThrow();

        var thread = pullRequestReviewThreadRepository.findById(threadId).orElseThrow();

        assertThat(thread.getState())
            .isEqualTo(de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED);
        assertThat(thread.getResolvedAt()).isNull();
        assertThat(thread.getUpdatedAt())
            .isEqualTo(unresolvedThread.getUpdatedAt());
        assertThat(thread.getLastActor()).isNotNull();
        assertThat(thread.getLastActor().getLogin()).isEqualTo(unresolvedThread.getSender().getLogin());
    }
}
