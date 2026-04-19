package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Complete user profile including contribution history and activity")
public record ProfileDTO(
    @NonNull @Schema(description = "Basic information about the user") UserInfoDTO userInfo,
    @Schema(description = "Timestamp of the user's first contribution") Instant firstContribution,
    @NonNull
    @Schema(description = "Repositories the user has contributed to")
    List<RepositoryInfoDTO> contributedRepositories,
    @NonNull
    @Schema(description = "Recent scored review activity with XP scores")
    List<ProfileReviewActivityDTO> reviewActivity,
    @NonNull
    @Schema(description = "Currently open pull requests authored by the user")
    List<PullRequestInfoDTO> openPullRequests,
    @NonNull
    @Schema(description = "Aggregated activity stats consistent with leaderboard calculations")
    ProfileActivityStatsDTO activityStats,
    @NonNull
    @Schema(description = "Distinct pull requests reviewed by this user")
    List<PullRequestInfoDTO> reviewedPullRequests,
    @NonNull @Schema(description = "XP progress information for the users' profile") ProfileXpRecordDTO xpRecord
) {}
