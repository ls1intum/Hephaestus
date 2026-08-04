package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(
    name = "PracticeDetectorCapability",
    description = "Hephaestus detection capability for the declared integration evidence"
)
public record PracticeDetectorCapability(
    @NonNull @NotNull PracticeDetectorAssessmentMethod assessmentMethod,
    @NonNull @NotNull PracticeDetectorEvidenceCoverage evidenceCoverage
) {
    public PracticeDetectorCapability {
        Objects.requireNonNull(assessmentMethod, "assessmentMethod");
        Objects.requireNonNull(evidenceCoverage, "evidenceCoverage");
        boolean noDetector = assessmentMethod == PracticeDetectorAssessmentMethod.NONE;
        boolean noCoverage = evidenceCoverage == PracticeDetectorEvidenceCoverage.NONE;
        if (noDetector != noCoverage) {
            throw new IllegalArgumentException(
                "NONE assessment method and evidence coverage must be declared together"
            );
        }
    }

    public boolean supportsAutomatedDetection() {
        return (
            assessmentMethod != PracticeDetectorAssessmentMethod.NONE &&
            evidenceCoverage == PracticeDetectorEvidenceCoverage.DECLARED_REQUIREMENTS_SUFFICIENT
        );
    }
}
