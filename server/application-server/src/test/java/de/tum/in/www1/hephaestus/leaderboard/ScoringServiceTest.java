package de.tum.in.www1.hephaestus.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private PullRequestRepository pullRequestRepository;

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService(pullRequestRepository, new LeaderboardProperties());
    }

    @Test
    void calculateReviewScore_skipsSelfAssignedCopilotReviews() {
        User copilot = new User();
        copilot.setId(1L);
        copilot.setLogin("Copilot");

        User reviewer = new User();
        reviewer.setId(2L);
        reviewer.setLogin("reviewer");

        PullRequest pullRequest = new PullRequest();
        pullRequest.setAuthor(copilot);
        pullRequest.getAssignees().add(reviewer);
        pullRequest.setCommits(1);
        pullRequest.setAdditions(1);
        pullRequest.setDeletions(1);
        pullRequest.setChangedFiles(1);

        PullRequestReview review = new PullRequestReview();
        review.setAuthor(reviewer);
        review.setPullRequest(pullRequest);
        review.setState(PullRequestReview.State.APPROVED);
        review.setHtmlUrl("review-url");
        review.setSubmittedAt(Instant.now());

        double score = scoringService.calculateReviewScore(List.of(review));

        assertThat(score).isZero();
    }

    @Test
    void calculateReviewScore_countsReviewsWhenAuthorNotExcluded() {
        User reviewer = new User();
        reviewer.setId(2L);
        reviewer.setLogin("reviewer");

        User author = new User();
        author.setId(3L);
        author.setLogin("regular-author");

        PullRequest pullRequest = new PullRequest();
        pullRequest.setAuthor(author);
        pullRequest.setCommits(1);
        pullRequest.setAdditions(1);
        pullRequest.setDeletions(1);
        pullRequest.setChangedFiles(1);

        PullRequestReview review = new PullRequestReview();
        review.setAuthor(reviewer);
        review.setPullRequest(pullRequest);
        review.setState(PullRequestReview.State.APPROVED);
        review.setHtmlUrl("review-url");
        review.setSubmittedAt(Instant.now());

        double score = scoringService.calculateReviewScore(List.of(review));

        assertThat(score).isGreaterThan(0.0);
    }
}
