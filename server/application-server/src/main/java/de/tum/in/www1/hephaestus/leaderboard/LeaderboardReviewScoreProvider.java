package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.ReviewScoreProvider;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Leaderboard module's implementation of {@link ReviewScoreProvider}.
 * <p>
 * This bridges the gitprovider module's need for scoring with the
 * leaderboard module's scoring logic, following Dependency Inversion Principle.
 */
@Component
public class LeaderboardReviewScoreProvider implements ReviewScoreProvider {

    private final ScoringService scoringService;

    public LeaderboardReviewScoreProvider(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @Override
    public int calculateReviewScore(List<PullRequestReview> reviews) {
        return (int) scoringService.calculateReviewScore(reviews);
    }

    @Override
    public int calculateReviewScore(IssueComment issueComment) {
        return (int) scoringService.calculateReviewScore(issueComment);
    }
}
