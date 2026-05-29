package de.tum.cit.aet.hephaestus.profile.dto;

import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

@Schema(description = "Configurable activity monitor data for a contributor profile")
public record ProfileActivityMonitorDTO(
    @NonNull
    @Schema(description = "Aggregated activity stats after applying monitor filters")
    ProfileActivityStatsDTO activityStats,
    @NonNull
    @Schema(description = "Review activity entries after applying monitor filters and limit")
    List<ProfileReviewActivityDTO> reviewActivity,
    @NonNull
    @Schema(description = "Open pull requests authored in the selected timeframe, after repository filters and limit")
    List<PullRequestInfoDTO> authoredPullRequests,
    @NonNull
    @Schema(description = "Repositories with monitor-relevant activity in the selected timeframe")
    List<RepositoryInfoDTO> repositories,
    @NonNull
    @Schema(description = "Total review activity entries after filters, before limit")
    Integer totalReviewActivityCount,
    @NonNull
    @Schema(description = "Total open authored pull requests after filters, before limit")
    Integer totalAuthoredPullRequestCount
) {}
