package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Author-declared automated assessment and evidence sufficiency for one practice")
public record PracticeAutomatedAssessment(
    @NonNull
    @NotNull
    @Schema(description = "Implementation Hephaestus uses for automated assessment")
    PracticeAutomatedAssessmentMode mode,
    @NonNull
    @NotNull
    @Schema(description = "Whether meeting every evidence requirement provides enough context to assess")
    PracticeEvidenceSufficiency evidenceSufficiency
) {
    public PracticeAutomatedAssessment {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(evidenceSufficiency, "evidenceSufficiency");
        boolean noAssessment = mode == PracticeAutomatedAssessmentMode.NONE;
        boolean noEvidence = evidenceSufficiency == PracticeEvidenceSufficiency.NONE;
        if (noAssessment != noEvidence) {
            throw new IllegalArgumentException("NONE assessment mode and evidence support must be declared together");
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean canAttemptAutomatedAssessment() {
        return (
            mode == PracticeAutomatedAssessmentMode.LANGUAGE_MODEL &&
            evidenceSufficiency == PracticeEvidenceSufficiency.SUFFICIENT_WHEN_REQUIREMENTS_MET
        );
    }
}
