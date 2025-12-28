package de.tum.in.www1.hephaestus.gitprovider.common.spi;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.util.List;

/**
 * Calculates review scores for leaderboard integration.
 */
public interface ReviewScoreProvider {
    int calculateReviewScore(List<PullRequestReview> reviews);

    int calculateReviewScore(IssueComment issueComment);
}
