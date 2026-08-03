package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Optional source that may be used at any available quality")
public record OptionalPracticeEvidenceRequirement(
    @NonNull @NotNull SourceKind sourceKind,
    @NonNull @NotNull @Schema(allowableValues = "ANY") EvidenceCompletenessRequirement completeness,
    @NonNull @NotNull @Schema(allowableValues = "ANY") EvidenceFreshnessRequirement freshness
) {
    public OptionalPracticeEvidenceRequirement {
        Objects.requireNonNull(sourceKind, "sourceKind");
        if (completeness != EvidenceCompletenessRequirement.ANY || freshness != EvidenceFreshnessRequirement.ANY) {
            throw new IllegalArgumentException("Optional evidence must use ANY completeness and freshness");
        }
    }
}
