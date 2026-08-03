package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Source and minimum capture quality required by a practice")
public record PracticeEvidenceRequirement(
    @NonNull @NotNull SourceKind sourceKind,
    @NonNull @NotNull EvidenceCompletenessRequirement completeness,
    @NonNull @NotNull EvidenceFreshnessRequirement freshness
) {
    public PracticeEvidenceRequirement {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(freshness, "freshness");
    }
}
