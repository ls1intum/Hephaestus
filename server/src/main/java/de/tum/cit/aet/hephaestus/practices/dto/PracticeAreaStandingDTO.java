package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendDirection;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A developer's derived qualitative standing for one Area including 1<=n<many practices")
public record PracticeAreaStandingDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull @Schema(description = "Derived qualitative standing across the area's practices") Standing standing,
    @Nullable
    @Schema(
        description = "Developer guidance aggregated from the area's feedback (null unless the standing is a verdict). " +
            "The deterministic summary combines standing, next focus, and developer-facing catalog guidance."
    )
    String guidance,
    @Nullable
    @Schema(description = "How the guidance text was produced (null when there is no guidance)")
    GuidanceSource guidanceSource,
    @Nullable
    @Schema(description = "Evidence-weighted, opportunity-indexed direction across the area's practices")
    TrendDirection direction,
    @Nullable @Schema(description = "Evidence support and provenance for the direction") TrendSupportDTO trendSupport,
    @Nullable
    @Schema(description = "Calendar span covered by the feedback, for provenance only; never a trend-analysis unit")
    Integer feedbackSpanDays,
    @Nullable
    @Schema(description = "Oldest contributing observation, for provenance only (null without a verdict)")
    Instant feedbackSince,
    @NonNull
    @Schema(description = "Supporting feedback the status derives from (problems first); empty without a verdict")
    List<ReflectionItemDTO> items,
    @NonNull
    @Schema(
        description = "Distinct work artifacts the feedback comes from, per kind (provenance, not a score); empty without a verdict"
    )
    List<FeedbackSourceCountDTO> sources
) {
    public enum Standing {
        DEVELOPING,
        STRENGTH,
        MIXED,
        NOT_OBSERVED,
        NO_OPPORTUNITY,
    }

    public static boolean isVerdict(Standing standing) {
        return standing == Standing.DEVELOPING || standing == Standing.MIXED || standing == Standing.STRENGTH;
    }

    public enum GuidanceSource {
        RULE_BASED,
    }
}
