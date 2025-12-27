package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    private final PullRequestRepository pullRequestRepository;
    private final Set<String> selfReviewAuthorLogins;

    private static final double WEIGHT_APPROVAL = 2.0;
    private static final double WEIGHT_CHANGESREQUESTED = 2.5;
    private static final double WEIGHT_COMMENT = 1.5;

    public ScoringService(PullRequestRepository pullRequestRepository, LeaderboardProperties leaderboardProperties) {
        this.pullRequestRepository = pullRequestRepository;
        this.selfReviewAuthorLogins = leaderboardProperties
            .getSelfReviewAuthorLogins()
            .stream()
            .filter(Objects::nonNull)
            .map(login -> login.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public double calculateReviewScore(PullRequestReview pullRequestReview) {
        return calculateReviewScore(List.of(pullRequestReview));
    }

    public double calculateReviewScore(List<PullRequestReview> pullRequestReviews) {
        return calculateReviewScore(pullRequestReviews, 0);
    }

    public double calculateReviewScore(List<PullRequestReview> pullRequestReviews, int numberOfIssueComments) {
        List<PullRequestReview> eligibleReviews = pullRequestReviews
            .stream()
            .filter(review -> !isSelfAssignedReview(review))
            .toList();

        if (eligibleReviews.isEmpty()) {
            return 0.0;
        }

        // All reviews are for the same pull request
        int complexityScore = calculateComplexityScore(pullRequestReviews.getFirst().getPullRequest());

        double approvalScore = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.APPROVED)
            .filter(review -> review.getAuthor().getId() != review.getPullRequest().getAuthor().getId())
            .map(review -> WEIGHT_APPROVAL * calculateCodeReviewBonus(review.getComments().size(), complexityScore))
            .reduce(0.0, Double::sum);

        double changesRequestedScore = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.CHANGES_REQUESTED)
            .filter(review -> review.getAuthor().getId() != review.getPullRequest().getAuthor().getId())
            .map(
                review ->
                    WEIGHT_CHANGESREQUESTED * calculateCodeReviewBonus(review.getComments().size(), complexityScore)
            )
            .reduce(0.0, Double::sum);

        double commentScore = eligibleReviews
            .stream()
            .filter(
                review ->
                    review.getState() == PullRequestReview.State.COMMENTED ||
                    review.getState() == PullRequestReview.State.UNKNOWN
            )
            .filter(review -> review.getAuthor().getId() != review.getPullRequest().getAuthor().getId())
            .map(review -> WEIGHT_COMMENT * calculateCodeReviewBonus(review.getComments().size(), complexityScore))
            .reduce(0.0, Double::sum);

        double issueCommentScore = WEIGHT_COMMENT * numberOfIssueComments;

        double interactionScore = approvalScore + changesRequestedScore + commentScore + issueCommentScore;
        return (10 * interactionScore * complexityScore) / (interactionScore + complexityScore);
    }

    public double calculateReviewScore(IssueComment issueComment) {
        Issue issue = issueComment.getIssue();
        PullRequest pullRequest;
        if (issue.isPullRequest()) {
            pullRequest = (PullRequest) issue;
        } else {
            var optionalPR = pullRequestRepository.findByRepositoryIdAndNumber(
                issue.getRepository().getId(),
                issue.getNumber()
            );
            if (optionalPR.isEmpty()) {
                logger.error("Issue comment is not associated with a pull request.");
                return 0;
            }
            pullRequest = optionalPR.get();
        }
        if (pullRequest.getAuthor().getId() == issueComment.getAuthor().getId()) {
            return 0;
        }

        int complexityScore = calculateComplexityScore(pullRequest);

        return (10 * WEIGHT_COMMENT * complexityScore) / (WEIGHT_COMMENT + complexityScore);
    }

    /**
     * Calculates the code review bonus for a given number of code comments and complexity score.
     * The bonus is a value between 0 and 2.
     * Taken from the original leaderboard implementation script.
     *
     * @param codeComments
     * @param complexityScore
     * @return bonus
     */
    private double calculateCodeReviewBonus(int codeComments, int complexityScore) {
        double maxBonus = 2;

        double codeReviewBonus = 1;
        if (codeComments < complexityScore) {
            // Function goes from 0 at codeComments = 0 to 1 at codeComments = complexityScore
            codeReviewBonus +=
                (2 * Math.sqrt(complexityScore) * Math.sqrt(codeComments)) / (codeComments + complexityScore);
        } else {
            // Saturate at 1
            codeReviewBonus += 1;
        }
        return (codeReviewBonus / 2) * maxBonus;
    }

    /**
     * Returns true if this review should be excluded because the PR was authored by a
     * configured bot (e.g., Copilot) and the reviewer is an assignee on that PR.
     */
    private boolean isSelfAssignedReview(PullRequestReview review) {
        PullRequest pullRequest = review.getPullRequest();
        User reviewer = review.getAuthor();
        if (pullRequest == null || reviewer == null) {
            return false;
        }

        User pullRequestAuthor = pullRequest.getAuthor();
        if (pullRequestAuthor == null || pullRequestAuthor.getLogin() == null) {
            return false;
        }

        if (!selfReviewAuthorLogins.contains(pullRequestAuthor.getLogin().toLowerCase(Locale.ROOT))) {
            return false;
        }

        Set<User> assignees = pullRequest.getAssignees();
        if (assignees == null || assignees.isEmpty()) {
            return false;
        }

        Long reviewerId = reviewer.getId();
        String reviewerLogin = reviewer.getLogin();

        return assignees
            .stream()
            .filter(Objects::nonNull)
            .anyMatch(assignee -> {
                if (assignee.getId() != null && reviewerId != null) {
                    return assignee.getId().equals(reviewerId);
                }

                return (
                    assignee.getLogin() != null &&
                    reviewerLogin != null &&
                    assignee.getLogin().equalsIgnoreCase(reviewerLogin)
                );
            });
    }

    /**
     * Calculates the complexity score for a given pull request.
     * Possible values: 1, 3, 7, 17, 33.
     * Taken from the original leaderboard implementation script.
     *
     * @param pullRequest
     * @return score
     */
    private int calculateComplexityScore(PullRequest pullRequest) {
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
