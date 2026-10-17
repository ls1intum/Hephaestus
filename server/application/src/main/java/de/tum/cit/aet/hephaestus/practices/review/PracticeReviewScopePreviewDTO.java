package de.tum.cit.aet.hephaestus.practices.review;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record PracticeReviewScopePreviewDTO(
        @Schema(description = "Effective coverage before the proposed change") @NonNull
        PracticeReviewScopeSummaryDTO current,

        @Schema(description = "Effective coverage if the proposed change is applied") @NonNull
        PracticeReviewScopeSummaryDTO proposed,

        @Schema(description = "Whether the proposal admits any repository, branch, or person excluded now") @NonNull
        Boolean widens) {}
