package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Leaderboard entry representing a ranked contributor or team.
 *
 * <p>This DTO supports both individual and team leaderboard modes:
 * <ul>
 *   <li><strong>Individual mode:</strong> {@code user} is populated, {@code team} is null</li>
 *   <li><strong>Team mode:</strong> {@code team} is populated, {@code user} is null</li>
 * </ul>
 *
 * <p>The score and activity counts reflect contributions within the requested timeframe.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    @NonNull @Schema(description = "Count of review and issue comments", example = "10") Integer numberOfComments,
    @NonNull
    @Schema(description = "Count of reviews with unknown/unrecognized state", example = "0")
    Integer numberOfUnknowns,
    @NonNull @Schema(description = "Count of inline code review comments", example = "7") Integer numberOfCodeComments
) {}
