package de.tum.cit.aet.hephaestus.practices.review;

import org.jspecify.annotations.NonNull;

public record PracticeReviewCoverageSummaryDTO(
    @NonNull Integer coveredRepositories,
    @NonNull Integer monitoredRepositories,
    @NonNull Integer coveredPeople,
    @NonNull Integer eligiblePeople,
    @NonNull Integer recentReviewVolume,
    @NonNull Integer estimateWindowDays
) {}
