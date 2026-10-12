package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.time.TimeBucketSize;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewResultCountsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Practice reviews, results and feedback in a time range, in total, over time and by practice")
public record PracticeReviewOverviewDTO(
        @NonNull @Schema(description = "Inclusive lower bound of the range counted, with its default filled in")
        Instant from,

        @NonNull @Schema(description = "Exclusive upper bound of the range counted, with its default filled in")
        Instant to,

        @NonNull @Schema(description = "How long each bucket is")
        TimeBucketSize bucket,

        @NonNull @Schema(description = "Practice reviews created in the range")
        ReviewRunCountsDTO reviews,

        @NonNull @Schema(description = "Results recorded in the range")
        ReviewResultCountsDTO practiceResults,

        @NonNull @Schema(description = "Of those results, the ones an admin has marked incorrect and not restored")
        Long practiceResultsInvalidated,

        @NonNull @Schema(description = "Feedback created in the range")
        ReviewFeedbackCountsDTO feedback,

        @NonNull @Schema(description = "Every bucket the range touches, oldest first, including empty ones")
        List<PracticeReviewBucketDTO> buckets,

        @NonNull
        @Schema(
                description = "Each practice with a result recorded in the range or feedback created in the range"
                        + " on one of its results, the most observed first, then by name")
        List<PracticeReviewCountsDTO> practices) {}
