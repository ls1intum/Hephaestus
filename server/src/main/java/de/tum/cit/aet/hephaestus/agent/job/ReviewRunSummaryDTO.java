package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunSummaryRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

@Schema(description = "A review run with finding and feedback outcome counts")
public record ReviewRunSummaryDTO(
    @NonNull UUID id,
    @NonNull AgentJobStatus status,
    @NonNull ReviewRunTargetDTO target,
    @NonNull Instant createdAt,
    @NonNull ReviewFindingCountsDTO findings,
    @NonNull ReviewFeedbackCountsDTO feedback
) {
    static ReviewRunSummaryDTO from(
        ReviewRunSummaryRow review,
        ReviewFindingCountsDTO findings,
        ReviewFeedbackCountsDTO feedback
    ) {
        return new ReviewRunSummaryDTO(
            review.getId(),
            review.getStatus(),
            ReviewRunTargetDTO.from(review),
            review.getCreatedAt(),
            findings,
            feedback
        );
    }
}
