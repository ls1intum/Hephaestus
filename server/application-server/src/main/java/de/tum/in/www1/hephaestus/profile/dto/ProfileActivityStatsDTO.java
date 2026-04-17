package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

/**
 * Profile-specific DTO for aggregated activity statistics with XP scores.
 *
 * <p>This DTO represents the same statistics as leaderboard entries but is
 * scoped to a single user's profile view. It contains:
 * <ul>
 *   <li>Total XP score</li>
 *   <li>Review activity breakdown (approvals, change requests, comment reviews)</li>
 *   <li>Visible pull request and issue activity summaries</li>
 *   <li>Distinct PR review count</li>
 * </ul>
 *
 * @param score total XP score for the timeframe
 * @param numberOfReviewedPRs distinct count of pull requests reviewed
 * @param numberOfApprovals count of APPROVED review states
 * @param numberOfChangeRequests count of CHANGES_REQUESTED review states
 * @param numberOfComments count of COMMENTED review states
 * @param numberOfCodeComments count of scored inline feedback comments on pull requests authored by someone else
 * @param numberOfUnknowns count of unknown/other review states
 * @param numberOfOwnReplies count of visible-only discussion replies and inline thread replies on the user's own pull requests
 * @param numberOfOpenPullRequests count of authored pull requests opened in timeframe that are still open
 * @param numberOfMergedPullRequests count of authored pull requests merged in timeframe
 * @param numberOfClosedPullRequests count of authored pull requests closed without merge in timeframe
 * @param numberOfOpenedIssues count of issues opened in timeframe
 * @param numberOfClosedIssues count of issues closed in timeframe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aggregated activity statistics with XP scores for a user profile")
public record ProfileActivityStatsDTO(
    @NonNull @Schema(description = "Total XP score", example = "1250") Integer score,
    @NonNull
    @Schema(description = "Number of distinct pull requests reviewed", example = "42")
    Integer numberOfReviewedPRs,
    @NonNull @Schema(description = "Number of approvals given", example = "15") Integer numberOfApprovals,
    @NonNull @Schema(description = "Number of change requests submitted", example = "8") Integer numberOfChangeRequests,
    @NonNull
    @Schema(description = "Number of comment-only review submissions", example = "12")
    Integer numberOfComments,
    @NonNull
    @Schema(
        description = "Number of scored inline feedback comments on pull requests authored by someone else",
        example = "30"
    )
    Integer numberOfCodeComments,
    @NonNull @Schema(description = "Number of reviews with unknown state", example = "0") Integer numberOfUnknowns,
    @NonNull
    @Schema(
        description = "Number of visible-only discussion replies and inline thread replies on the user's own pull requests",
        example = "4"
    )
    Integer numberOfOwnReplies,
    @NonNull
    @Schema(description = "Number of authored pull requests opened in the timeframe that are still open", example = "2")
    Integer numberOfOpenPullRequests,
    @NonNull
    @Schema(description = "Number of authored pull requests merged in the timeframe", example = "6")
    Integer numberOfMergedPullRequests,
    @NonNull
    @Schema(description = "Number of authored pull requests closed without merge in the timeframe", example = "1")
    Integer numberOfClosedPullRequests,
    @NonNull
    @Schema(description = "Number of issues opened in the timeframe", example = "3")
    Integer numberOfOpenedIssues,
    @NonNull
    @Schema(description = "Number of issues closed in the timeframe", example = "2")
    Integer numberOfClosedIssues
) {}
