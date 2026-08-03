package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Author-declared, versioned evidence boundary for a practice definition")
public record PracticeEvidenceDeclaration(
    @NonNull @NotNull SourceContractVersion sourceContractVersion,
    @NonNull @NotNull EvidenceProfileId profile,
    @NonNull @NotNull PracticeObservability observability,
    @NonNull
    @NotNull
    @Size(min = 1, message = "At least one required evidence source is required")
    @Valid
    List<PracticeEvidenceRequirement> required,
    @NonNull @NotNull @Valid List<OptionalPracticeEvidenceRequirement> optional,
    @NonNull @NotNull PracticeEvidenceRefusal onUnsatisfied,
    @NonNull @NotNull @Valid List<PracticeEvidenceBlindSpot> blindSpots
) {
    public PracticeEvidenceDeclaration {
        Objects.requireNonNull(sourceContractVersion, "sourceContractVersion");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(observability, "observability");
        required = sortedRequirements(required, "required");
        optional = Objects.requireNonNull(optional, "optional")
            .stream()
            .sorted(Comparator.comparing(requirement -> requirement.sourceKind().value()))
            .toList();
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
