package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewMode;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Review timing, evidence choices, and recommended settings for one type of reviewed work")
public record PracticeWorkTypeDefinitionOptionsDTO(
    @NonNull ArtifactKind artifactKind,
    @NonNull
    @Schema(description = "Signals a practice on this work type can be reviewed on")
    List<PracticeSignalOptionDTO> signals,
    @NonNull PracticeAutomatedReviewPolicy recommendedPolicy,
    @NonNull
    @Schema(description = "Evidence a new binding on this work type starts with when the author says nothing")
    List<PracticeEvidenceRequirement> recommendedNeeds,
    @NonNull List<PracticeAutomatedReviewMode> supportedAutomatedReviewModes,
    @NonNull List<PracticeEvidenceSourceOptionDTO> allowedSources
) {}
