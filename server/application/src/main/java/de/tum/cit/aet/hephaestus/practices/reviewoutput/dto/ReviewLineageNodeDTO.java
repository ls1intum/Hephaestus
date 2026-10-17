package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewBoundFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.ProblemSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.LineageKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewLineageNodeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LineageKind kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ItemRefDTO item,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewRefDTO review,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewBoundResultDTO result,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ProblemSummaryDTO problem,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewBoundFeedbackDTO feedback) {}
