package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewTrigger;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(
        description = "How one record came about: the reviewed work, the review, its observations, the feedback written"
                + " from them, where that feedback went and how the developer answered. Bounded: each list is capped"
                + " and the count beside it says how many more there are")
public record ReviewLineageDTO(
        @Schema(description = "The work the review read; null when the review named no work") @Nullable
        ReviewedWorkRefDTO work,

        @Schema(description = "The review that recorded the observations; null when its record is gone") @Nullable
        ReviewLineageRunDTO review,

        @NonNull @Schema(description = "The observations in the chain, the ones the request names first")
        List<ReviewLineageObservationDTO> observations,

        @Schema(
                description = "Observations of the review, or behind the listed feedback, that the list leaves out",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int moreObservations,

        @NonNull @Schema(description = "The feedback in the chain, newest first")
        List<ReviewLineageFeedbackDTO> feedback,

        @Schema(
                description = "Feedback in the chain that the list leaves out",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int moreFeedback,

        @Schema(
                description = "When the reviewed pull request or merge request was merged; null for other work and for"
                        + " work that is not merged")
        @Nullable
        Instant workMergedAt,

        @Schema(
                description = "What started the review, from the first moment on the work that names it; null when no"
                        + " recorded moment names the review")
        @Nullable
        ReviewTrigger reviewTrigger,

        @NonNull
        @Schema(
                description = "What happened on the work, oldest first: opened, pushed, merged and the like, each with"
                        + " its time and whether it started this review. Empty unless the request asks for the whole"
                        + " review; then the newest 30 at most")
        List<ReviewLineageMomentDTO> workMoments) {}
