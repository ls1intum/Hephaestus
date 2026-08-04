package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentValidation;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A resolved curated practice definition")
public record CuratedPracticeDefinitionDTO(
    @NonNull String name,
    @NonNull WorkArtifact artifactType,
    @NonNull List<String> triggerEvents,
    @NonNull String criteria,
    @Nullable String precomputeScript,
    @NonNull PracticeAutomatedAssessmentPolicy automatedAssessmentPolicy,
    @NonNull PracticeAutomatedAssessmentValidation automatedAssessmentValidation,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) {
    public static CuratedPracticeDefinitionDTO from(String practiceSlug, PracticeDefinition definition) {
        return new CuratedPracticeDefinitionDTO(
            definition.name(),
            definition.artifactType(),
            definition.triggerEvents(),
            definition.criteria(),
            definition.precomputeScript(),
            definition.automatedAssessmentPolicy(),
            PracticeAutomatedAssessmentValidation.authorDeclared(practiceSlug, definition),
            definition.whyItMatters(),
            definition.whatGoodLooksLike(),
            definition.areaSlug()
        );
    }
}
