package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    public static double calculateReviewScore(List<PullRequestReview> pullRequestReviews) {
        return ScoringService.calculateReviewScore(pullRequestReviews, 0);
    }

    public static double calculateReviewScore(List<PullRequestReview> pullRequestReviews, int numberOfIssueComments) {
        // All reviews are for the same pull request
        int complexityScore = ScoringService.calculateComplexityScore(pullRequestReviews.get(0).getPullRequest());

        int approvalReviews = (int) pullRequestReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.APPROVED)
            .count();
        int changesRequestedReviews = (int) pullRequestReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.CHANGES_REQUESTED)
            .count();
        int commentReviews = (int) pullRequestReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.COMMENTED)
            .filter(review -> review.getBody() != null) // Only count if there is a comment
            .count();
        int unknownReviews = (int) pullRequestReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.UNKNOWN)
            .filter(review -> review.getBody() != null) // Only count if there is a comment
            .count();

        int codeComments = pullRequestReviews
            .stream()
            .map(review -> review.getComments().size())
            .reduce(0, Integer::sum);

        double interactionScore =
            (approvalReviews * 1.5 +
                changesRequestedReviews * 2.0 +
                (commentReviews + unknownReviews + numberOfIssueComments) +
                codeComments * 0.5);

        double complexityBonus = 1 + (complexityScore - 1) / 32.0;

        return 5 * interactionScore * complexityBonus;
    }

    public static double calculateReviewScore(IssueComment issueComment) {
        Issue issue = issueComment.getIssue();
        // TODO: we have to find a better way to determine the complexity score for issue comments
        if (!issue.isPullRequest()) {
            return 1;
        }
        PullRequest pullRequest = (PullRequest) issue;
        
        int complexityScore = ScoringService.calculateComplexityScore(pullRequest);
        double complexityBonus = 1 + (complexityScore - 1) / 32.0;

        return 5 * complexityBonus;
    }

    /**
     * Calculates the complexity score for a given pull request.
     * Possible values: 1, 3, 7, 17, 33.
     * Taken from the original leaderboard implementation script.
     *
     * @param pullRequest
     * @return score
     */
    public static int calculateComplexityScore(PullRequest pullRequest) {
        Double complexityScore =
            ((pullRequest.getChangedFiles() * 3) +
                (pullRequest.getCommits() * 0.5) +
                pullRequest.getAdditions() +
                pullRequest.getDeletions()) /
            10;
        if (complexityScore < 10) {
            return 1; // Simple
        } else if (complexityScore < 50) {
            return 3; // Medium
        } else if (complexityScore < 100) {
            return 7; // Large
        } else if (complexityScore < 500) {
            return 17; // Huge
        }
        return 33; // Overly complex
    }
}
