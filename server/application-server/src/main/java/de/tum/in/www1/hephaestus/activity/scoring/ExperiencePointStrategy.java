package de.tum.in.www1.hephaestus.activity.scoring;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import java.util.List;

/**
 * Strategy interface for experience point (XP) calculation.
 *
 * <p>This abstraction enables:
 * <ul>
 *   <li>Dependency inversion: services depend on interface, not implementation</li>
 *   <li>Testability: mock implementations for unit tests</li>
 *   <li>Extensibility: different XP formulas for different workspaces</li>
 * </ul>
 *
 * <p>The default implementation is {@link ExperiencePointCalculator}.
 *
 * @see ExperiencePointCalculator
 */
public interface ExperiencePointStrategy {
    /**
     * Calculate XP for a code review.
     *
     * <p>XP is based on:
     * <ul>
     *   <li>Review type (approval, changes requested, comment)</li>
     *   <li>PR complexity (additions, deletions, files changed)</li>
     *   <li>Harmonic mean for multiple reviews on same PR</li>
     * </ul>
     *
     * @param reviews all reviews by the same author on a single PR
     * @return XP for this review activity
     */
    double calculateReviewExperiencePoints(List<PullRequestReview> reviews);

    /**
     * Calculate XP for a single review (simplified API).
     *
     * @param review the review to calculate XP for
     * @return XP for this review
     */
    double calculateReviewExperiencePoints(PullRequestReview review);

    /**
     * Calculate XP for an issue comment.
     *
     * <p>XP is based on comment length (substantive vs trivial).
     *
     * @param comment the issue comment
     * @return XP for this comment
     */
    double calculateIssueCommentExperiencePoints(IssueComment comment);

    /**
     * Calculate XP for creating an issue.
     *
     * @param issue the created issue
     * @return XP for issue creation
     */
    double calculateIssueCreatedExperiencePoints(Issue issue);

    /**
     * Calculate XP for opening a pull request.
     *
     * @param pullRequest the opened pull request
     * @return XP for PR creation
     */
    double calculatePullRequestOpenedExperiencePoints(PullRequest pullRequest);

    /**
     * Calculate XP for merging a pull request.
     *
     * @param pullRequest the merged pull request
     * @return XP for PR merge
     */
    double calculatePullRequestMergedExperiencePoints(PullRequest pullRequest);

    /**
     * Calculate XP for marking a PR ready for review.
     *
     * @param pullRequest the PR marked ready
     * @return XP for readiness transition
     */
    double calculatePullRequestReadyExperiencePoints(PullRequest pullRequest);

    /**
     * Calculate XP for an inline review comment.
     *
     * @param bodyLength length of the comment body
     * @return XP for review comment
     */
    double calculateReviewCommentExperiencePoints(int bodyLength);
}
