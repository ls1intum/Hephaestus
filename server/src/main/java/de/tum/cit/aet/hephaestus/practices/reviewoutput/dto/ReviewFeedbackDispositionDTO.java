package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ObservationFeedbackDisposition;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Counts of feedback by delivery state")
public record ReviewFeedbackDispositionDTO(
    @NonNull @Schema(description = "Linked messages awaiting delivery") Long prepared,
    @NonNull @Schema(description = "Linked messages delivered") Long delivered,
    @NonNull @Schema(description = "Linked messages delivered and later replaced") Long superseded,
    @NonNull @Schema(description = "Linked messages withheld by policy") Long suppressed,
    @NonNull @Schema(description = "Linked messages whose delivery failed") Long failed
) {
    public static ReviewFeedbackDispositionDTO empty() {
        return new ReviewFeedbackDispositionDTO(0L, 0L, 0L, 0L, 0L);
    }

    public static ReviewFeedbackDispositionDTO from(ObservationFeedbackDisposition row) {
        return new ReviewFeedbackDispositionDTO(
            row.getPrepared(),
            row.getDelivered(),
            row.getSuperseded(),
            row.getSuppressed(),
            row.getFailed()
        );
    }
}
