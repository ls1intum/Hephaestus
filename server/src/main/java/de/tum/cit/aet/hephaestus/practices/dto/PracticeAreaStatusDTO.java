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
 * to real findings rather than an opaque grade. Like the reflection surface it is a learner-facing
 * read model, NOT a scoreboard: no raw score, no observation enums, no criteria. The feedback span
 * and trajectory are provenance about the derivation, not scores about the developer.
 */
@Schema(description = "A developer's derived qualitative standing for one Area including 1<=n<many practices")
public record PracticeAreaStatusDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(
        description = "Derived qualitative status across the area's practices",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NOT_OBSERVED", "NO_OPPORTUNITY" }
    )
    AreaStatus status,
    @Nullable
    @Schema(
        description = "Learner-facing guidance aggregated from the area's feedback (null unless the status is a verdict). " +
            "The deterministic summary combines standing, next focus, and learner-facing catalog guidance; " +
            "the same field carries AI-aggregated guidance when a provider supplies it."
    )
    String guidance,
    @Nullable
    @Schema(
        description = "How the guidance text was produced (null when there is no guidance)",
        allowableValues = { "RULE_BASED", "AI_AGGREGATED" }
    )
    GuidanceSource guidanceSource,
    @Nullable
    @Schema(
        description = "Evidence-weighted, opportunity-indexed direction across the area's practices",
        allowableValues = { "IMPROVING", "STABLE", "DECLINING", "UNCERTAIN", "INSUFFICIENT_EVIDENCE" }
    )
    TrendDirection trajectory,
    @Nullable @Schema(description = "Evidence support and provenance for the trajectory") TrendSupportDTO trendSupport,
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
     * <p>The first three are verdicts. The last two are the reasons there is NO verdict, and they are
     * deliberately separate: a learner cannot act on an empty state that means "nothing was reviewed" and
     * "your work offered no opportunity" interchangeably. This mirrors the trend surface's split of
     * {@code STABLE} from {@code INSUFFICIENT_EVIDENCE} — absence of a claim is not the same fact as a
     * claim of absence.
     */
    public enum AreaStatus {
        /** Only problems surfaced across the area's practices — the focus of attention. */
        DEVELOPING,
        /** Only strengths — confirmed good habits across the area. */
        STRENGTH,
        /** Both problems and strengths across the area's practices. */
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

    /**
     * Provenance of the guidance text. The UI labels AI-produced guidance, so a reader always knows
     * whether a sentence was assembled by a rule or written by a model.
     */
    public enum GuidanceSource {
        /** Deterministic sentence assembled from the area's practice names and standings. */
        RULE_BASED,
        /** Aggregated by an AI model over the area's feedback (e.g. a persisted nightly summary). */
        AI_AGGREGATED,
    }
}
