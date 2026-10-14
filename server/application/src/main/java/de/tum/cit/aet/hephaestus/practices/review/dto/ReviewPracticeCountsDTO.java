package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.dto.GroupRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackCountsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewPracticeCountsDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        GroupRefDTO group,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewResultCountsDTO results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int resultsMarkedIncorrect,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewFeedbackCountsDTO feedback) {}
