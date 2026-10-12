package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPracticeGroupDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewResultCountsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "What practice reviews recorded for one practice in a time range")
public record PracticeReviewCountsDTO(
        @NonNull String practiceSlug,
        @NonNull String practiceName,

        @Schema(description = "Practice group; null when the practice is Unassigned") @Nullable
        ReviewPracticeGroupDTO group,

        @NonNull @Schema(description = "Results of this practice recorded in the range")
        ReviewResultCountsDTO practiceResults,

        @NonNull @Schema(description = "Of those results, the ones an admin has marked incorrect and not restored")
        Long practiceResultsInvalidated,

        @NonNull
        @Schema(
                description = "Feedback created in the range that is bound to a result of this practice,"
                        + " whenever that result was recorded; one piece of feedback bound to several practices"
                        + " counts for each")
        ReviewFeedbackCountsDTO feedback) {}
