package de.tum.cit.aet.hephaestus.practices.curated.dto;

import de.tum.cit.aet.hephaestus.practices.dto.ValidTriggerEvents;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record UpdateCuratedPracticeRequestDTO(
    @NonNull @NotBlank @Size(min = 3, max = 128) String name,
    @NonNull @NotNull @Size(max = 10) @ValidTriggerEvents List<String> triggerEvents,
    @NonNull @NotBlank @Size(max = 50000) String criteria,
    @Nullable @Size(max = 100000) String precomputeScript,
    @NonNull @NotNull WorkArtifact artifactType,
    @Nullable @Size(max = 2000) String whyItMatters,
    @Nullable @Size(max = 2000) String whatGoodLooksLike,
    @Nullable @Size(max = 64) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String areaSlug
) {}
