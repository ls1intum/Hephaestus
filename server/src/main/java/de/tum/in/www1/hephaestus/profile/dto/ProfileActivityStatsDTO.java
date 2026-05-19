package de.tum.in.www1.hephaestus.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

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
