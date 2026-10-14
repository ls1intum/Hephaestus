package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingResultDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.TrendSupportDTO;
import de.tum.cit.aet.hephaestus.practices.spi.GuidanceSource;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeGroupStandingDTO(
        String groupSlug,
        String groupName,
        PracticeStanding standing,

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

        List<PracticeStandingResultDTO> results,
        List<FeedbackSourceCountDTO> sources) {
    public PracticeGroupStandingDTO {
        results = List.copyOf(results);
        sources = List.copyOf(sources);
    }
}
