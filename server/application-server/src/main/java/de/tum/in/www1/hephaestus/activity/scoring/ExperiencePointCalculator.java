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
 *
 * @see ExperiencePointStrategy
 */
@Component
public class ExperiencePointCalculator implements ExperiencePointStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExperiencePointCalculator.class);

    private final PullRequestRepository pullRequestRepository;
    private final Set<String> selfReviewAuthorLogins;
    private final ExperiencePointProperties properties;

    // ========================================================================
    // Default Experience Point Constants (overridable via properties)
    // ========================================================================

    /**
     * Default XP awarded when a pull request is opened.
     * <p>
     * Currently disabled (0.0). PR XP will be introduced in a future release.
     */
    public static final double XP_PULL_REQUEST_OPENED = 0.0;

    /**
     * Default XP awarded when a pull request is merged.
     * <p>
     * Currently disabled (0.0). PR XP will be introduced in a future release.
     */
    public static final double XP_PULL_REQUEST_MERGED = 0.0;

    /** Default XP awarded for each inline review comment */
    public static final double XP_REVIEW_COMMENT = 0.5;

    public ExperiencePointCalculator(
        PullRequestRepository pullRequestRepository,
        ExperiencePointProperties properties
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.properties = properties;
        this.selfReviewAuthorLogins = properties
            .selfReviewAuthorLogins()
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
        return properties.xpAwards().pullRequestOpened();
    }

    /**
     * Get XP for pull request merged (configurable).
     */
    public double getXpPullRequestMerged() {
        return properties.xpAwards().pullRequestMerged();
    }

    /**
     * Get XP for review comment (configurable).
     */
    public double getXpReviewComment() {
        return properties.xpAwards().reviewComment();
    }

    /**
     * Get XP for pull request marked ready for review (configurable).
     */
    public double getXpPullRequestReady() {
        return properties.xpAwards().pullRequestReady();
    }

    /**
     * Get XP for issue created (configurable).
     */
    public double getXpIssueCreated() {
        return properties.xpAwards().issueCreated();
    }

    /**
     * Get XP for project created (configurable).
     */
    public double getXpProjectCreated() {
        return properties.xpAwards().projectCreated();
    }

    /**
     * Get XP for project item created (configurable).
     */
    public double getXpProjectItemCreated() {
        return properties.xpAwards().projectItemCreated();
    }

    /**
     * Get XP for project status update created (configurable).
     */
    public double getXpProjectStatusUpdateCreated() {
        return properties.xpAwards().projectStatusUpdateCreated();
    }

    /**
     * Get XP for commit created (configurable).
     */
    public double getXpCommitCreated() {
        return properties.xpAwards().commitCreated();
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
    @Override
    public double calculateReviewExperiencePoints(List<PullRequestReview> reviews) {
        return calculateReviewExperiencePoints(reviews, 0);
    }

    /**
     * Calculate experience points for reviews with additional issue comments.
     *
     * <p><strong>Dismissed reviews are included in XP calculation.</strong>
     * The effort of providing feedback is valuable regardless of whether the review
     * was later dismissed (e.g., due to new commits making it stale).
     *
     * @param reviews list of reviews to score
     * @param issueCommentCount number of issue comments
     * @return total XP for all reviews and comments
     */
    public double calculateReviewExperiencePoints(List<PullRequestReview> reviews, int issueCommentCount) {
        List<PullRequestReview> eligibleReviews = reviews
            .stream()
            .filter(review -> !isSelfAssignedReview(review))
            .toList();

        if (eligibleReviews.isEmpty()) {
            return 0.0;
        }

        // All reviews are for the same pull request
        int complexityScore = calculateComplexityScore(reviews.get(0).getPullRequest());

        // Get configurable weights
        double weightApproval = properties.reviewWeights().approval();
        double weightChangesRequested = properties.reviewWeights().changesRequested();
        double weightComment = properties.reviewWeights().comment();

        double approvalExperiencePoints = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.APPROVED)
            .filter(review -> !isSelfReview(review))
            .mapToDouble(
                review -> weightApproval * calculateCodeReviewBonus(review.getComments().size(), complexityScore)
            )
            .sum();

        double changesRequestedExperiencePoints = eligibleReviews
            .stream()
            .filter(review -> review.getState() == PullRequestReview.State.CHANGES_REQUESTED)
            .filter(review -> !isSelfReview(review))
            .mapToDouble(
                review ->
                    weightChangesRequested * calculateCodeReviewBonus(review.getComments().size(), complexityScore)
            )
            .sum();

        double commentExperiencePoints = eligibleReviews
            .stream()
            .filter(
                review ->
                    review.getState() == PullRequestReview.State.COMMENTED ||
                    review.getState() == PullRequestReview.State.UNKNOWN
            )
            .filter(review -> !isSelfReview(review))
            .mapToDouble(
                review -> weightComment * calculateCodeReviewBonus(review.getComments().size(), complexityScore)
            )
            .sum();

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
     * Calculate XP for a single review (simplified API).
     *
     * @param review the review to calculate XP for
     * @return XP for this review
     */
    @Override
    public double calculateReviewExperiencePoints(PullRequestReview review) {
        return calculateReviewExperiencePoints(List.of(review));
    }

    /**
     * Calculate XP for creating an issue.
     *
     * @param issue the created issue
     * @return XP for issue creation
     */
    @Override
    public double calculateIssueCreatedExperiencePoints(Issue issue) {
        return properties.xpAwards().issueCreated();
    }

    /**
     * Calculate XP for opening a pull request.
     *
     * @param pullRequest the opened pull request
     * @return XP for PR creation
     */
    @Override
    public double calculatePullRequestOpenedExperiencePoints(PullRequest pullRequest) {
        return properties.xpAwards().pullRequestOpened();
    }

    /**
     * Calculate XP for merging a pull request.
     *
     * @param pullRequest the merged pull request
     * @return XP for PR merge
     */
    @Override
    public double calculatePullRequestMergedExperiencePoints(PullRequest pullRequest) {
        return properties.xpAwards().pullRequestMerged();
    }

    /**
     * Calculate XP for marking a PR ready for review.
     *
     * @param pullRequest the PR marked ready
     * @return XP for readiness transition
     */
    @Override
    public double calculatePullRequestReadyExperiencePoints(PullRequest pullRequest) {
        return properties.xpAwards().pullRequestReady();
    }

    /**
     * Calculate XP for an inline review comment.
     *
     * @param bodyLength length of the comment body
     * @return XP for review comment
     */
    @Override
    public double calculateReviewCommentExperiencePoints(int bodyLength) {
        // Substantive comments (>50 chars) earn full XP, trivial ones earn half
        double base = properties.xpAwards().reviewComment();
        return bodyLength > 50 ? base : base * 0.5;
    }

    /**
     * Calculate experience points for an issue comment on a pull request.
     *
     * @param issueComment the comment to score
     * @return XP for the comment
     */
    @Override
    public double calculateIssueCommentExperiencePoints(IssueComment issueComment) {
        Issue issue = issueComment.getIssue();
        if (issue == null) {
            log.warn(
                "Skipped XP calculation, issue comment has no associated issue: commentId={}",
                issueComment.getId()
            );
            return 0;
        }

        PullRequest pullRequest;

        if (issue.isPullRequest()) {
            pullRequest = (PullRequest) issue;
        } else {
            if (issue.getRepository() == null) {
                log.warn("Skipped XP calculation, issue has no repository: issueId={}", issue.getId());
                return 0;
            }
            var optionalPullRequest = pullRequestRepository.findByRepositoryIdAndNumber(
                issue.getRepository().getId(),
                issue.getNumber()
            );
            if (optionalPullRequest.isEmpty()) {
                // Expected case: comment is on a regular issue, not a PR - no XP awarded
                log.debug(
                    "Skipped XP for non-PR issue comment: commentId={}, issueNumber={}",
                    issueComment.getId(),
                    issue.getNumber()
                );
                return 0;
            }
            pullRequest = optionalPullRequest.get();
        }

        // No XP for commenting on your own pull request (with null checks)
        User prAuthor = pullRequest.getAuthor();
        User commentAuthor = issueComment.getAuthor();
        if (
            prAuthor != null &&
            commentAuthor != null &&
            prAuthor.getId() != null &&
            commentAuthor.getId() != null &&
            prAuthor.getId().equals(commentAuthor.getId())
        ) {
            return 0;
        }

        int complexityScore = calculateComplexityScore(pullRequest);

        // Harmonic mean with configurable comment weight
        double weightComment = properties.reviewWeights().comment();
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
     * Returns false if any required data is null (defensive check).
     */
    private boolean isSelfReview(PullRequestReview review) {
        User reviewer = review.getAuthor();
        PullRequest pr = review.getPullRequest();
        if (reviewer == null || reviewer.getId() == null || pr == null) {
            return false;
        }
        User prAuthor = pr.getAuthor();
        if (prAuthor == null || prAuthor.getId() == null) {
            return false;
        }
        return reviewer.getId().equals(prAuthor.getId());
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
