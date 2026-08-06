package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewMode;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Review timing, evidence choices, and recommended requirements for one type of reviewed work")
public record PracticeWorkTypeDefinitionOptionsDTO(
    @NonNull ArtifactKind artifactKind,
    @NonNull List<PracticeTriggerEventOptionDTO> triggerEvents,
    @NonNull PracticeAutomatedReviewPolicy recommendedRequirements,
    @NonNull List<PracticeAutomatedReviewMode> supportedAutomatedReviewModes,
    @NonNull List<PracticeEvidenceSourceOptionDTO> allowedSources
) {}
