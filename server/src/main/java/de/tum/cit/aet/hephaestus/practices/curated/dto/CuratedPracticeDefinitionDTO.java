package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDeclaration;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceValidation;
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
    @NonNull PracticeEvidenceDeclaration evidence,
    @NonNull PracticeEvidenceValidation evidenceValidation,
    @Nullable String whyItMatters,
    @Nullable String whatGoodLooksLike,
    @Nullable String areaSlug
) {
    public static CuratedPracticeDefinitionDTO from(PracticeDefinition definition) {
        return new CuratedPracticeDefinitionDTO(
            definition.name(),
            definition.artifactType(),
            definition.triggerEvents(),
            definition.criteria(),
            definition.precomputeScript(),
            definition.evidence(),
            PracticeEvidenceValidation.authorDeclared(definition.evidence()),
            definition.whyItMatters(),
            definition.whatGoodLooksLike(),
            definition.areaSlug()
        );
    }
}
