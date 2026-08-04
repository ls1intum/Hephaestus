package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@Schema(description = "Source that may add context but never blocks automated assessment when absent")
public record PracticeOptionalContextSource(
    @NonNull
    @NotNull
    @Schema(description = "Stable source identifier from the selected source contract")
    SourceKind sourceKind
) {
    public PracticeOptionalContextSource {
        Objects.requireNonNull(sourceKind, "sourceKind");
    }
}
