package de.tum.cit.aet.hephaestus.practices.trace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewResultCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeTraceEntryDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FeedbackMode feedbackMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewOn reviewOn,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AboutRole about,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ReviewRefDTO review,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewResultCountsDTO results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasonCountDTO> withheldReasons) {
    public PracticeTraceEntryDTO {
        withheldReasons = List.copyOf(withheldReasons);
    }
}
