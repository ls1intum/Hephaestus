package de.tum.cit.aet.hephaestus.practices.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.OwnFeedbackRefDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.OwnItemTraceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProfileReviewDetailDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewRefDTO review,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResultListDTO> results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OwnFeedbackRefDTO> feedback,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OwnItemTraceDTO trace,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OutcomeVectorDTO practiceOutcomes) {
    public ProfileReviewDetailDTO {
        results = List.copyOf(results);
        feedback = List.copyOf(feedback);
    }
}
