package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.List;

/**
 * Profile-specific DTO representing a user's complete profile view.
 *
 * <p>This DTO lives in the profile module because it composes:
 * <ul>
 *   <li>User info (from gitprovider)</li>
 *   <li>Repository contributions (from gitprovider)</li>
 *   <li>Review activity WITH XP scores (profile-specific composition)</li>
 *   <li>Open pull requests (from gitprovider)</li>
 * </ul>
 *
 * <p>The gitprovider module has NO knowledge of XP or scoring.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Complete user profile including contribution history and activity")
public record ProfileDTO(
    @NonNull @Schema(description = "Basic information about the user") UserInfoDTO userInfo,
    @Schema(description = "Timestamp of the user's first contribution") Instant firstContribution,
    @NonNull
    @Schema(description = "Repositories the user has contributed to")
    List<RepositoryInfoDTO> contributedRepositories,
    @Schema(description = "Recent review activity with XP scores") List<ProfileReviewActivityDTO> reviewActivity,
    @Schema(description = "Currently open pull requests authored by the user")
    List<PullRequestInfoDTO> openPullRequests,
    @Schema(description = "Aggregated activity stats consistent with leaderboard calculations")
    ProfileActivityStatsDTO activityStats,
    @Schema(description = "Distinct pull requests reviewed by this user") List<PullRequestInfoDTO> reviewedPullRequests,
    @NonNull @Schema(description = "XP progress information for the users' profile") ProfileXpRecordDTO xpRecord
) {
}
