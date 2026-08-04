package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Author-declared, versioned evidence boundary for a practice definition")
public record PracticeEvidenceDeclaration(
    @NonNull @NotNull SourceContractVersion sourceContractVersion,
    @NonNull @NotNull EvidenceProfileId profile,
    @NonNull
    @NotNull
    @Valid
    @Schema(description = "Hephaestus detectability from the declared integration evidence; not human observability")
    PracticeDetectorCapability detectorCapability,
    @NonNull @NotNull @Valid List<PracticeEvidenceRequirement> required,
    @NonNull @NotNull @Valid List<OptionalPracticeEvidenceRequirement> optional,
    @NonNull @NotNull PracticeEvidenceRefusal onUnsatisfied,
    @NonNull @NotNull @Valid List<PracticeEvidenceBlindSpot> blindSpots
) {
    public PracticeEvidenceDeclaration {
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(detectorCapability, "detectorCapability");
        required = sortedRequirements(required, "required");
        optional = Objects.requireNonNull(optional, "optional")
            .stream()
            .sorted(Comparator.comparing(requirement -> requirement.sourceKind().value()))
            .toList();
        boolean detectorAbsent = detectorCapability.assessmentMethod() == PracticeDetectorAssessmentMethod.NONE;
        if (detectorAbsent && (!required.isEmpty() || !optional.isEmpty())) {
            throw new IllegalArgumentException(
                "A practice without a Hephaestus detector cannot declare detector evidence"
            );
        }
        if (!detectorAbsent && required.isEmpty()) {
            throw new IllegalArgumentException("A Hephaestus detector requires at least one evidence source");
        }
        Objects.requireNonNull(onUnsatisfied, "onUnsatisfied");
        blindSpots = Objects.requireNonNull(blindSpots, "blindSpots")
            .stream()
            .sorted(Comparator.comparing(PracticeEvidenceBlindSpot::code))
            .toList();
    }

    private static List<PracticeEvidenceRequirement> sortedRequirements(
        List<PracticeEvidenceRequirement> requirements,
        String field
    ) {
        return Objects.requireNonNull(requirements, field)
            .stream()
            .sorted(Comparator.comparing(requirement -> requirement.sourceKind().value()))
            .toList();
    }
}
