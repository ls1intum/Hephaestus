package de.tum.in.www1.hephaestus.activity.scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for experience point (XP) calculation in the activity scoring system.
 *
 * <p>All XP weights and formulas can be tuned via application properties under the
 * {@code hephaestus.activity} prefix. This record-based configuration provides:
 *
 * <ul>
 *   <li><strong>Review weights:</strong> Multipliers for different review outcomes (approval,
 *       changes requested, comment-only)
 *   <li><strong>Fixed XP awards:</strong> Points granted for specific activities (PR opened,
 *       merged, issue created, etc.)
 *   <li><strong>Safety bounds:</strong> Maximum XP per event to prevent runaway calculations
 *   <li><strong>Self-review exclusions:</strong> Bot accounts whose PRs are excluded from
 *       self-assigned review XP
 * </ul>
 *
 * <h3>Example Configuration</h3>
 * <pre>{@code
 * hephaestus:
 *   activity:
 *     self-review-author-logins:
 *       - copilot
 *       - dependabot[bot]
 *     review-weights:
 *       approval: 2.5
 *       changes-requested: 3.0
 *     xp-awards:
 *       pull-request-opened: 1.5
 *       pull-request-merged: 2.0
 *     max-xp-per-event: 500.0
 * }</pre>
 *
 * @param selfReviewAuthorLogins logins of bot accounts whose PRs should be treated as
 *                               "self-assigned" when the reviewer is also an assignee
 * @param reviewWeights          multipliers for different code review outcomes
 * @param xpAwards               fixed XP values awarded for specific developer activities
 * @param maxXpPerEvent          safety cap to prevent outliers and potential bugs
 * @see ExperiencePointCalculator
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.activity")
public record ExperiencePointProperties(
    List<String> selfReviewAuthorLogins,

    @Valid ReviewWeights reviewWeights,

    @Valid XpAwards xpAwards,

    @Positive @Max(10_000) @DefaultValue("1000.0") double maxXpPerEvent
) {
    /**
     * Weight multipliers for different code review outcomes.
     *
     * <p>These weights are applied to the base XP calculation to reward different
     * levels of review engagement. Higher weights encourage more thorough reviews.
     *
     * @param approval         multiplier for approval reviews (default: 2.0)
     * @param changesRequested multiplier for changes-requested reviews (default: 2.5)
     * @param comment          multiplier for comment-only reviews (default: 1.5)
     */
    @Validated
    public record ReviewWeights(
        @PositiveOrZero @Max(100) @DefaultValue("2.0") double approval,
        @PositiveOrZero @Max(100) @DefaultValue("2.5") double changesRequested,
        @PositiveOrZero @Max(100) @DefaultValue("1.5") double comment
    ) {}

    /**
     * Fixed XP values awarded for specific developer activities.
     *
     * <p>These are base awards that may be further modified by complexity scores
     * or other factors in the XP calculation pipeline.
     *
     * <p><strong>Note:</strong> PR and issue activities are set to 0 by default.
     * Currently, only code reviews and review comments earn XP. PR/issue XP
     * will be introduced in a future release.
     *
     * @param pullRequestOpened         XP for opening a pull request (default: 0.0)
     * @param pullRequestMerged         XP for merging a pull request (default: 0.0)
     * @param pullRequestReady          XP for marking a PR ready for review (default: 0.0)
     * @param reviewComment             XP for each inline review comment (default: 0.5)
     * @param issueCreated              XP for creating an issue (default: 0.0)
     * @param projectCreated            XP for creating a project (default: 0.0)
     * @param projectItemCreated        XP for adding an item to a project (default: 0.0)
     * @param projectStatusUpdateCreated XP for creating a project status update (default: 0.0)
     * @param commitCreated             XP for creating a commit (pushed to default branch) (default: 0.0)
     * @param discussionCreated         XP for creating a discussion (default: 0.0)
     * @param discussionAnswered        XP for answering a discussion (default: 0.0)
     * @param discussionCommentCreated  XP for creating a discussion comment (default: 0.0)
     */
    @Validated
    public record XpAwards(
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double pullRequestOpened,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double pullRequestMerged,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double pullRequestReady,
        @PositiveOrZero @Max(1000) @DefaultValue("0.5") double reviewComment,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double issueCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double projectCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double projectItemCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double projectStatusUpdateCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double commitCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double discussionCreated,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double discussionAnswered,
        @PositiveOrZero @Max(1000) @DefaultValue("0.0") double discussionCommentCreated
    ) {}

    /** Compact constructor ensuring nested records are never null. */
    public ExperiencePointProperties {
        if (selfReviewAuthorLogins == null) {
            selfReviewAuthorLogins = List.of();
        }
        if (reviewWeights == null) {
            reviewWeights = new ReviewWeights(2.0, 2.5, 1.5);
        }
        if (xpAwards == null) {
            // PR/issue/project/commit/discussion activities are 0 by default; only review comments earn XP
            xpAwards = new XpAwards(0.0, 0.0, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
