package de.tum.cit.aet.hephaestus.practices.review;

import org.jspecify.annotations.NonNull;

public record PracticeReviewCoveragePreviewDTO(
    @NonNull PracticeReviewCoverageSummaryDTO current,
    @NonNull PracticeReviewCoverageSummaryDTO proposed,
    @NonNull Boolean widens
) {}
