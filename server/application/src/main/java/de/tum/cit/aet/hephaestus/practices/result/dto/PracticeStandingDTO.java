package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.StandingBasis;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeStandingDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String whyItMatters,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String whatGoodLooksLike,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PracticeStanding standing,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticeStandingResultDTO> toWorkOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PracticeStandingResultDTO> strengths,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        TrendDirection direction,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        TrendSupportDTO trendSupport,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean doesNotApplyOnly,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What a verdict was read from: current reviews, or only outcomes recorded by earlier"
                        + " reviews, which carry no current proof, also when the criteria they were assessed against"
                        + " are unknown. Absent without a verdict")
        @Nullable
        StandingBasis basis) {
    public PracticeStandingDTO {
        toWorkOn = List.copyOf(toWorkOn);
        strengths = List.copyOf(strengths);
    }
}
