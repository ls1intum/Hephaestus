package de.tum.in.www1.hephaestus.activity.scoring;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for experience point calculation.
 *
 * <p>All XP weights and formulas can be tuned via application properties
 * under {@code hephaestus.activity.*}.
 *
 * @see ExperiencePointCalculator
 */
@Component
@ConfigurationProperties(prefix = "hephaestus.activity")
@Getter
@Setter
public class ExperiencePointProperties {

    /**
     * Logins of bot accounts whose pull requests should be treated as "self-assigned"
     * when the reviewer is also an assignee. Reviews of these PRs by assignees
     * are excluded from XP calculation.
     *
     * <p>Example: "copilot" for Copilot-authored PRs where the human assignee
     * reviews their own AI-assisted work.
     */
    private List<String> selfReviewAuthorLogins = List.of();

    // ========================================================================
    // Review Weights (multipliers for different review outcomes)
    // ========================================================================

    /**
     * Weight multiplier for approval reviews.
     * Higher values reward approvals more.
     */
    private double weightApproval = 2.0;

    /**
     * Weight multiplier for changes-requested reviews.
     * Higher values reward thorough code review feedback.
     */
    private double weightChangesRequested = 2.5;

    /**
     * Weight multiplier for comment-only reviews.
     * Lower than approval/changes since no final decision is made.
     */
    private double weightComment = 1.5;

    // ========================================================================
    // Fixed XP Awards
    // ========================================================================

    /**
     * XP awarded when a pull request is opened.
     */
    private double xpPullRequestOpened = 1.0;

    /**
     * XP awarded when a pull request is merged.
     */
    private double xpPullRequestMerged = 1.0;

    /**
     * XP awarded for each inline review comment.
     */
    private double xpReviewComment = 0.5;

    /**
     * XP awarded when a pull request is marked ready for review (draft→ready).
     * Rewards the effort of preparing work for collaboration.
     */
    private double xpPullRequestReady = 0.5;

    /**
     * XP awarded when an issue is created.
     * Rewards contribution to work identification and planning.
     */
    private double xpIssueCreated = 0.5;

    // ========================================================================
    // Safety Bounds
    // ========================================================================

    /**
     * Maximum XP that can be awarded per event.
     * Events with computed XP above this cap will be clamped.
     * Used as a safety bound against outliers and potential bugs.
     */
    private double maxXpPerEvent = 1000.0;
}
