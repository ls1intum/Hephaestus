package de.tum.cit.aet.hephaestus.practices.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record PracticeReviewCoverageSummaryDTO(
        @Schema(description = "Monitored repositories admitted by the repository coverage axis") @NonNull
        Integer coveredRepositories,

        @Schema(description = "Repositories currently monitored by the workspace") @NonNull
        Integer monitoredRepositories,

        @Schema(description = "Eligible linked members admitted by the people coverage axis") @NonNull
        Integer coveredPeople,

        @Schema(description = "Eligible linked human members in the workspace") @NonNull
        Integer eligiblePeople,

        @Schema(description = "Workspace-wide practice-review jobs created during the estimate window") @NonNull
        Integer recentReviewVolume,

        @Schema(description = "Number of days included in recentReviewVolume") @NonNull
        Integer estimateWindowDays) {}
