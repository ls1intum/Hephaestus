package de.tum.cit.aet.hephaestus.practices.result.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GoingWellPracticeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceSlug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String goingWell,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant since,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int workCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GoingWellEvidenceDTO> evidence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore) {
    public GoingWellPracticeDTO {
        evidence = List.copyOf(evidence);
    }
}
