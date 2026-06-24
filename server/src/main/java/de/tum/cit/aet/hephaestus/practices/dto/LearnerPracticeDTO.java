package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Learner-facing projection of a {@link Practice}. <b>The detection {@code criteria} is absent BY
 * CONSTRUCTION</b> — this record has no field to carry it — making "criteria never reaches a learner" a
 * physical guarantee, not a UI convention. It carries only what a developer should see: the name, which
 * area it belongs to, why it matters, and what good looks like. NEVER widen this record with
 * {@code criteria}, {@code precomputeScript}, {@code kind}, or a raw observation.
 */
@Schema(description = "Learner-facing view of a practice — criteria absent by construction")
public record LearnerPracticeDTO(
    @NonNull @Schema(description = "URL-safe identifier") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @Nullable @Schema(description = "Slug of the practice area this belongs to, if any") String areaSlug,
    @Nullable @Schema(description = "Why this practice matters, in plain language") String whyItMatters,
    @Nullable @Schema(description = "A concrete picture of doing this well") String whatGoodLooksLike
) {
    public static LearnerPracticeDTO from(Practice practice) {
        return new LearnerPracticeDTO(
            practice.getSlug(),
            practice.getName(),
            practice.getArea() != null ? practice.getArea().getSlug() : null,
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike()
        );
    }
}
