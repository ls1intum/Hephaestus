package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.observation.AreaTrajectory;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The current developer's derived standing for one practice area — the area's reflection feedback
 * aggregated to a single qualitative status, with the supporting items so the status stays traceable
 * to real findings rather than an opaque grade. Like the reflection surface it is a learner-facing
 * read model, NOT a scoreboard: no raw score, no observation enums, no criteria. The feedback span
 * and trajectory are provenance about the derivation, not scores about the developer.
 */
@Schema(description = "A developer's derived qualitative standing for one practice area")
public record PracticeAreaStatusDTO(
    @NonNull @Schema(description = "Area slug") String areaSlug,
    @NonNull @Schema(description = "Area name") String areaName,
    @NonNull
    @Schema(
        description = "Derived qualitative status across the area's practices",
        allowableValues = { "DEVELOPING", "STRENGTH", "MIXED", "NO_DATA" }
    )
    AreaStatus status,
    @Nullable
    @Schema(
        description = "Learner-facing guidance aggregated from the area's feedback (null for NO_DATA). " +
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
        description = "Weighted direction of the area's per-practice day-to-day standing changes " +
            "(null until at least one practice has two evidence-bearing days)",
        allowableValues = { "IMPROVING", "STEADY", "REGRESSING" }
    )
    AreaTrajectory trajectory,
    @Nullable
    @Schema(
        description = "Days of feedback the status actually rests on — since the oldest in-window observation (null for NO_DATA)"
    )
    Integer feedbackSpanDays,
    @Nullable
    @Schema(description = "When the oldest observation contributing to this status was made (null for NO_DATA)")
    Instant feedbackSince,
    @NonNull
    @Schema(description = "Supporting feedback the status derives from (problems first); empty only for NO_DATA")
    List<ReflectionItemDTO> items,
    @NonNull
    @Schema(
        description = "Distinct work artifacts the feedback comes from, per kind (provenance, not a score); empty for NO_DATA"
    )
    List<FeedbackSourceCountDTO> sources
) {
    /** Coarse, human standing for the area, aggregated from the per-practice reflection cards. */
    public enum AreaStatus {
        /** Only problems surfaced across the area's practices — the focus of attention. */
        DEVELOPING,
        /** Only strengths — confirmed good habits across the area. */
        STRENGTH,
        /** Both problems and strengths across the area's practices. */
        MIXED,
        /** No displayable findings for any practice in this area (also: caller not yet a synced developer). */
        NO_DATA,
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
