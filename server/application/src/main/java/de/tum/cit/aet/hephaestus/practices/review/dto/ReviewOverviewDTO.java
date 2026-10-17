package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.spi.TimeBucketSize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewOverviewDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TimeBucketSize bucket,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewStatusCountsDTO reviews,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewResultCountsDTO results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int resultsMarkedIncorrect,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewFeedbackCountsDTO feedback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewBucketDTO> buckets,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReviewPracticeCountsDTO> practices) {
    public ReviewOverviewDTO {
        buckets = List.copyOf(buckets);
        practices = List.copyOf(practices);
    }
}
