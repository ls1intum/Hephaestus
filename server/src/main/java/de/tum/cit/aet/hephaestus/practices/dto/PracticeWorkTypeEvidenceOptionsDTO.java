package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentMode;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentPolicy;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Evidence choices and recommended requirements for one type of reviewed work")
public record PracticeWorkTypeEvidenceOptionsDTO(
    @NonNull WorkArtifact artifactType,
    @NonNull PracticeAutomatedAssessmentPolicy recommendedRequirements,
    @NonNull List<PracticeAutomatedAssessmentMode> supportedAutomatedAssessmentModes,
    @NonNull List<PracticeEvidenceSourceOptionDTO> allowedSources
) {}
