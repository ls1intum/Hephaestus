package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Developer-facing practice metadata. Review criteria are deliberately excluded. */
@Schema(description = "Developer-facing view of a practice — criteria absent by construction")
public record ReviewedPracticeDTO(
        @NonNull @Schema(description = "URL-safe identifier")
        String slug,

        @NonNull @Schema(description = "Human-readable name")
        String name,

        @Nullable @Schema(description = "Slug of the practice group this belongs to, if any")
        String groupSlug,

        @Nullable @Schema(description = "Why this practice matters, in plain language")
        String whyItMatters,

        @Nullable @Schema(description = "A concrete picture of doing this well")
        String whatGoodLooksLike) {
    public static ReviewedPracticeDTO from(Practice practice) {
        return new ReviewedPracticeDTO(
                practice.getSlug(),
                practice.getName(),
                practice.getGroup() != null ? practice.getGroup().getSlug() : null,
                practice.getWhyItMatters(),
                practice.getWhatGoodLooksLike());
    }
}
