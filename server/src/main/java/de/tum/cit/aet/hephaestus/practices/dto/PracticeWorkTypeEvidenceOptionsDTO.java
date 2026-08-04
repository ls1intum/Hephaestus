package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewMode;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Evidence choices and recommended requirements for one type of reviewed work")
public record PracticeWorkTypeEvidenceOptionsDTO(
    @NonNull WorkArtifact artifactType,
    @NonNull PracticeAutomatedReviewPolicy recommendedRequirements,
    @NonNull List<PracticeAutomatedReviewMode> supportedAutomatedReviewModes,
    @NonNull List<PracticeEvidenceSourceOptionDTO> allowedSources
) {}
