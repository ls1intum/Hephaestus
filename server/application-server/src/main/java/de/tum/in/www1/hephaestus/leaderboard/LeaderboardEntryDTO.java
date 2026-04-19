package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

/** Ranked leaderboard entry. Exactly one of {@code user} / {@code team} is populated depending on leaderboard mode. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A ranked entry in the leaderboard (individual or team)")
public record LeaderboardEntryDTO(
    @NonNull @Schema(description = "Position in the leaderboard (1-based)", example = "1") Integer rank,
    @NonNull @Schema(description = "Total XP score for the timeframe", example = "150") Integer score,
    @Schema(description = "User info (populated in INDIVIDUAL mode, null in TEAM mode)") UserInfoDTO user,
    @Schema(description = "Team info (populated in TEAM mode, null in INDIVIDUAL mode)") TeamInfoDTO team,
    @NonNull @Schema(description = "Sample of reviewed PRs for display") List<PullRequestInfoDTO> reviewedPullRequests,
    @NonNull @Schema(description = "Count of distinct PRs reviewed", example = "5") Integer numberOfReviewedPRs,
    @NonNull @Schema(description = "Count of review approvals", example = "3") Integer numberOfApprovals,
    @NonNull @Schema(description = "Count of change requests submitted", example = "1") Integer numberOfChangeRequests,
    @NonNull @Schema(description = "Count of comment-only review submissions", example = "10") Integer numberOfComments,
    @NonNull
    @Schema(description = "Count of reviews with unknown/unrecognized state", example = "0")
    Integer numberOfUnknowns,
    @NonNull
    @Schema(
        description = "Count of scored inline feedback comments on pull requests authored by someone else",
        example = "7"
    )
    Integer numberOfCodeComments,
    @NonNull
    @Schema(
        description = "Count of visible-only discussion replies and inline thread replies on the contributor's own pull requests",
        example = "4"
    )
    Integer numberOfOwnReplies,
    @NonNull
    @Schema(description = "Count of authored pull requests opened in the timeframe that are still open", example = "2")
    Integer numberOfOpenPullRequests,
    @NonNull
    @Schema(description = "Count of authored pull requests merged in the timeframe", example = "6")
    Integer numberOfMergedPullRequests,
    @NonNull
    @Schema(description = "Count of authored pull requests closed without merge in the timeframe", example = "1")
    Integer numberOfClosedPullRequests,
    @NonNull
    @Schema(description = "Count of issues opened in the timeframe", example = "3")
    Integer numberOfOpenedIssues,
    @NonNull
    @Schema(description = "Count of issues closed in the timeframe", example = "2")
    Integer numberOfClosedIssues
) {}
