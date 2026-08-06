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
    EvidenceContentRequirement content
) {
    public PracticeEvidenceRequirement {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(completeness, "completeness");
        // Policies written before a practice could demand substance omit the field entirely; they
        // meant "any capture will do", which is what NO_REQUIREMENT says.
        content = content == null ? EvidenceContentRequirement.NO_REQUIREMENT : content;
    }
}
