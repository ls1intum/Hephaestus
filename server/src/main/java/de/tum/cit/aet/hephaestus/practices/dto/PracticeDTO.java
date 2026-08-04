package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentValidation;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Practice definition for evaluating developer contributions")
public record PracticeDTO(
    @NonNull @Schema(description = "Practice ID") Long id,
    @NonNull @Schema(description = "URL-safe identifier unique within workspace") String slug,
    @NonNull @Schema(description = "Human-readable name") String name,
    @NonNull @Schema(description = "Domain events that start a practice review") List<String> triggerEvents,
    @NonNull @Schema(description = "Practice evaluation criteria") String criteria,
    @Nullable
    @Schema(description = "TypeScript/Bun precompute script for static analysis before AI review")
    String precomputeScript,
    @NonNull PracticeAutomatedAssessmentPolicy automatedAssessmentPolicy,
    @NonNull PracticeAutomatedAssessmentValidation automatedAssessmentValidation,
    @NonNull @Schema(description = "Type of work this practice assesses") WorkArtifact artifactType,
    @Nullable @Schema(description = "Slug of the practice area this practice is bound to, if any") String areaSlug,
    @NonNull @Schema(description = "Position within its area (lowest first); ties broken by name") Integer displayOrder,
    @Nullable @Schema(description = "Developer-facing rationale (learner layer)") String whyItMatters,
    @Nullable @Schema(description = "Developer-facing exemplar (learner layer)") String whatGoodLooksLike,
    @NonNull @Schema(description = "Whether new reviews include this practice") Boolean usedInNewReviews,
    @NonNull @Schema(description = "Timestamp when the practice was created") Instant createdAt,
    @NonNull @Schema(description = "Timestamp when the practice was last updated") Instant updatedAt,
    @Nullable CatalogOriginDTO catalogOrigin
) {
    public static PracticeDTO from(Practice practice, @Nullable CatalogOriginDTO catalogOrigin) {
        return new PracticeDTO(
            practice.getId(),
            practice.getSlug(),
            practice.getName(),
            TriggerEventsConverter.toList(practice.getTriggerEvents()),
            practice.getCriteria(),
            practice.getPrecomputeScript(),
            practice.getAutomatedAssessmentPolicy(),
            PracticeAutomatedAssessmentValidation.authorDeclared(practice.getSlug(), PracticeDefinition.from(practice)),
            practice.getArtifactType(),
            practice.getArea() != null ? practice.getArea().getSlug() : null,
            practice.getDisplayOrder(),
            practice.getWhyItMatters(),
            practice.getWhatGoodLooksLike(),
            practice.isUsedInNewReviews(),
            practice.getCreatedAt(),
            practice.getUpdatedAt(),
            catalogOrigin
        );
    }
}
