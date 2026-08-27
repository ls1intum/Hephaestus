package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendDirection;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.TrendSupportDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The current developer's derived standing for one area. The area's reflection feedback
 * aggregated to a single qualitative status, with the supporting items so the status stays traceable
 * to real observations rather than an opaque grade. Like the reflection surface it is a developer-facing
 * read model, NOT a scoreboard: no raw score, no observation enums, no criteria. The feedback span
 * and direction are provenance about the derivation, not scores about the developer.
 */
@Schema(description = "A developer's derived qualitative standing for one Area including 1<=n<many practices")
public record PracticeAreaStatusDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull @Schema(description = "Derived qualitative status across the area's practices") AreaStatus status,
    @Nullable
    @Schema(
        description = "Developer guidance aggregated from the area's feedback (null unless the status is a verdict). " +
            "The deterministic summary combines standing, next focus, and developer-facing catalog guidance; " +
            "the same field carries AI-aggregated guidance when a provider supplies it."
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
    /**
     * Coarse, human standing for the area, aggregated from the per-practice reflection cards.
     *
     * <p>The area is the layer ABOVE the practices and reads nothing but their standings: it averages how
     * positive each practice's recent evidence was and puts the result on the same scale the practices use.
     * Deriving it from the practice standings rather than from their underlying observations is what keeps the two
     * surfaces from contradicting each other — a practice the reflection page shows as a strength can never be
     * counted against its own area.
     *
     * <p>The first three are verdicts. The last two are the reasons there is NO verdict, and they are
     * deliberately separate: a developer cannot act on an empty state that means "nothing was reviewed" and
     * "your work offered no opportunity" interchangeably. This mirrors the trend surface's split of
     * {@code UNCERTAIN} from {@code INSUFFICIENT_EVIDENCE} — absence of a claim is not the same fact as a
     * claim of absence.
     */
    public enum AreaStatus {
        /** Fewer than half the area's practices stand as a strength — the focus of attention. */
        DEVELOPING,
        /** Nearly every practice in the area stands as a strength — confirmed good habits. */
        STRENGTH,
        /** Real strengths across the area, and enough still to work on. */
        MIXED,
        /** No observation reached any practice in this area (also: caller not yet a synced developer). */
        NOT_OBSERVED,
        /**
         * Practices were evaluated but produced nothing to report: the reviewed work offered no relevant
         * opportunity, or a defect-detector practice ran clean.
         */
        NO_OPPORTUNITY,
    }

    /** Whether this status is a verdict about the developer's work, rather than a reason none could be formed. */
    public static boolean isVerdict(AreaStatus status) {
        return status == AreaStatus.DEVELOPING || status == AreaStatus.MIXED || status == AreaStatus.STRENGTH;
    }

    public enum GuidanceSource {
        RULE_BASED,
    }
}
