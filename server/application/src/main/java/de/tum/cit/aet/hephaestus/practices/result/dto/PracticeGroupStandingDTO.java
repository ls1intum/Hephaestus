package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.dto.FeedbackSourceCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.GuidanceSource;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeGroupStandingDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String groupSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String groupName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupDescription,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupIcon,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupColor,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PracticeStanding standing,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String guidance,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        GuidanceSource guidanceSource,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        TrendDirection direction,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        TrendSupportDTO trendSupport,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer feedbackSpanDays,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant feedbackSince,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticeStandingResultDTO> results,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FeedbackSourceCountDTO> sources) {
    public PracticeGroupStandingDTO {
        results = List.copyOf(results);
        sources = List.copyOf(sources);
    }
}
