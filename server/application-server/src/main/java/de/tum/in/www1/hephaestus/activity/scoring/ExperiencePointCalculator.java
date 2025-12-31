package de.tum.in.www1.hephaestus.activity.scoring;

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
import org.springframework.stereotype.Component;

/**
 * Calculates experience points (XP) for developer activities.
 *
 * <p>This is the single source of truth for all XP calculations.
 * Both the activity ledger and leaderboard use this component.
 *
 * <p>Formulas are based on complexity-weighted harmonic means that
 * reward thorough reviews of complex pull requests.
 *
 * <h3>Design Note</h3>
 * <p>This calculator depends on {@code PullRequestRepository} for
 * {@link #calculateIssueCommentExperiencePoints(IssueComment)} when the comment
 * is on a PR accessed via Issue. Ideally, XP calculation would be pure functions
 * taking primitive values, but this would require callers to resolve the
 * PullRequest before calling. This is a known trade-off for API simplicity.
 */
@Component
public class ExperiencePointCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ExperiencePointCalculator.class);

    private final PullRequestRepository pullRequestRepository;
    private final Set<String> selfReviewAuthorLogins;
    private final ExperiencePointProperties properties;

    // ========================================================================
    // Default Experience Point Constants (overridable via properties)
    // ========================================================================

    /** Default XP awarded when a pull request is opened */
    public static final double XP_PULL_REQUEST_OPENED = 1.0;

    /** Default XP awarded when a pull request is merged */
    public static final double XP_PULL_REQUEST_MERGED = 1.0;

    /** Default XP awarded for each inline review comment */
    public static final double XP_REVIEW_COMMENT = 0.5;

    public ExperiencePointCalculator(
        PullRequestRepository pullRequestRepository,
        ExperiencePointProperties properties
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.properties = properties;
        this.selfReviewAuthorLogins = properties
            .getSelfReviewAuthorLogins()
            .stream()
            .filter(Objects::nonNull)
            .map(login -> login.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    // ========================================================================
    // XP Accessors (use configured values from properties)
    // ========================================================================

    /**
     * Get XP for pull request opened (configurable).
     */
    public double getXpPullRequestOpened() {
        return properties.getXpPullRequestOpened();
    }

    /**
     * Get XP for pull request merged (configurable).
     */
    public double getXpPullRequestMerged() {
        return properties.getXpPullRequestMerged();
    }

    /**
     * Get XP for review comment (configurable).
     */
    public double getXpReviewComment() {
        return properties.getXpReviewComment();
    }

    // ========================================================================
    // Review Experience Points
    // ========================================================================

    /**
     * Calculate experience points for a list of reviews on the same pull request.
     *
     * @param reviews list of reviews to score
     * @return total XP for all reviews
     */
    public double calculateReviewExperiencePoints(List<PullRequestReview> reviews) {
        return calculateReviewExperiencePoints(reviews, 0);
    }

    /**
     * Calculate experience points for reviews with additional issue comments.
     *
     * @param reviews list of reviews to score
     * @param issueCommentCount number of issue comments
     * @return total XP for all reviews and comments
     */
    public double calculateReviewExperiencePoints(List<PullRequestReview> reviews, int issueCommentCount) {
        List<PullRequestReview> eligibleReviews = reviews
            .stream()
            .filter(review -> !review.isDismissed())
            .filter(review -> !isSelfAssignedReview(review))
            .toList();

        if (eligibleReviews.isEmpty()) {
            return 0.0;
        }

        // All reviews are for the same pull request
        int complexityScore = calculateComplexityScore(reviews.get(0).getPullRequest());

        // Get configurable weights
        double weightApproval = properties.getWeightApproval();
        double weightChangesRequested = properties.getWeightChangesRequested();
        double weightComment = properties.getWeightComment();

        double approvalExperiencePoints = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.APPROVED)
            .filter(review -> !isSelfReview(review))
            .map(review -> weightApproval * calculateCodeReviewBonus(review.getComments().size(), complexityScore))
            .reduce(0.0, Double::sum);

        double changesRequestedExperiencePoints = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.CHANGES_REQUESTED)
            .filter(review -> !isSelfReview(review))
            .map(
                review ->
                    weightChangesRequested * calculateCodeReviewBonus(review.getComments().size(), complexityScore)
            )
            .reduce(0.0, Double::sum);

        double commentExperiencePoints = eligibleReviews
            .stream()
            .filter(
                review ->
                    review.getState() == PullRequestReview.State.COMMENTED ||
                    review.getState() == PullRequestReview.State.UNKNOWN
            )
            .filter(review -> !isSelfReview(review))
            .map(review -> weightComment * calculateCodeReviewBonus(review.getComments().size(), complexityScore))
            .reduce(0.0, Double::sum);

        double issueCommentExperiencePoints = weightComment * issueCommentCount;

        double interactionScore =
            approvalExperiencePoints +
            changesRequestedExperiencePoints +
            commentExperiencePoints +
            issueCommentExperiencePoints;

        // Guard against division by zero in harmonic mean
        double denominator = interactionScore + complexityScore;
        if (denominator == 0.0) {
            return 0.0;
        }

        // Harmonic mean formula: rewards balanced interaction with complexity
        return (10 * interactionScore * complexityScore) / denominator;
    }

    /**
     * Calculate experience points for an issue comment on a pull request.
     *
     * @param issueComment the comment to score
     * @return XP for the comment
     */
    public double calculateIssueCommentExperiencePoints(IssueComment issueComment) {
        Issue issue = issueComment.getIssue();
        PullRequest pullRequest;

        if (issue.isPullRequest()) {
            pullRequest = (PullRequest) issue;
        } else {
            var optionalPullRequest = pullRequestRepository.findByRepositoryIdAndNumber(
                issue.getRepository().getId(),
                issue.getNumber()
            );
            if (optionalPullRequest.isEmpty()) {
                logger.error("Issue comment is not associated with a pull request.");
                return 0;
            }
            pullRequest = optionalPullRequest.get();
        }

        // No XP for commenting on your own pull request
        if (pullRequest.getAuthor().getId().equals(issueComment.getAuthor().getId())) {
            return 0;
        }

        int complexityScore = calculateComplexityScore(pullRequest);

        // Harmonic mean with configurable comment weight
        double weightComment = properties.getWeightComment();
        return (10 * weightComment * complexityScore) / (weightComment + complexityScore);
    }

    // ========================================================================
    // Complexity Calculation
    // ========================================================================

    /**
     * Calculate complexity score for a pull request.
     *
     * <p>Complexity tiers:
     * <ul>
     *   <li>1 = Simple (raw &lt; 10)</li>
     *   <li>3 = Medium (raw 10-49)</li>
     *   <li>7 = Large (raw 50-99)</li>
     *   <li>17 = Huge (raw 100-499)</li>
     *   <li>33 = Overly complex (raw &gt;= 500)</li>
     * </ul>
     *
     * @param pullRequest the pull request to analyze
     * @return complexity score (1, 3, 7, 17, or 33)
     */
    public int calculateComplexityScore(PullRequest pullRequest) {
        double rawComplexity =
            ((pullRequest.getChangedFiles() * 3) +
                (pullRequest.getCommits() * 0.5) +
                pullRequest.getAdditions() +
                pullRequest.getDeletions()) /
            10.0;

        if (rawComplexity < 10) {
            return 1; // Simple
        } else if (rawComplexity < 50) {
            return 3; // Medium
        } else if (rawComplexity < 100) {
            return 7; // Large
        } else if (rawComplexity < 500) {
            return 17; // Huge
        }
        return 33; // Overly complex
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    /**
     * Calculate code review bonus based on thoroughness.
     *
     * <p>More inline comments on complex PRs earn higher bonuses (up to 2x).
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
     * Check if the reviewer is the same as the pull request author.
     */
    private boolean isSelfReview(PullRequestReview review) {
        return review.getAuthor().getId().equals(review.getPullRequest().getAuthor().getId());
    }

    /**
     * Check if this review should be excluded because the pull request was authored
     * by a configured bot (e.g., Copilot) and the reviewer is an assignee.
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
}
