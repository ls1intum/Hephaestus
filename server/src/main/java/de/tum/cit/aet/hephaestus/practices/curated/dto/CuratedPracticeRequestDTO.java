package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.dto.ValidTriggerEvents;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A complete curated practice definition")
public record CuratedPracticeRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 128, message = "Name must be between 3 and 128 characters")
    @NonNull
    String name,

    @NonNull @NotNull(message = "Artifact type is required") WorkArtifact artifactType,

    @NotNull(message = "Trigger events are required")
    @Size(max = 10, message = "Trigger events must contain at most 10 entries")
    @ValidTriggerEvents
    @NonNull
    List<String> triggerEvents,

    @NotBlank(message = "Criteria is required")
    @Size(max = 50000, message = "Criteria must be at most 50000 characters")
    @NonNull
    String criteria,

    @Size(max = 50000, message = "Precompute script must be at most 50000 characters")
    @Nullable
    String precomputeScript,

    @Size(max = 2000, message = "Why it matters must be at most 2000 characters") @Nullable String whyItMatters,

    @Size(max = 2000, message = "What good looks like must be at most 2000 characters")
    @Nullable
    String whatGoodLooksLike,

    @Size(max = 64, message = "Area slug must be at most 64 characters") @Nullable String areaSlug
) {
    public static CuratedPracticeRequestDTO of(PracticeDefinition definition) {
        return new CuratedPracticeRequestDTO(
            definition.name(),
            definition.artifactType(),
            definition.triggerEvents(),
            definition.criteria(),
            definition.precomputeScript(),
            definition.whyItMatters(),
            definition.whatGoodLooksLike(),
            definition.areaSlug()
        );
    }

    public PracticeDefinition definition() {
        return new PracticeDefinition(
            name,
            artifactType,
            triggerEvents,
            criteria,
            precomputeScript,
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
    }
}
