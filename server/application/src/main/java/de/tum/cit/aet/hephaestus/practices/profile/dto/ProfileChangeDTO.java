package de.tum.cit.aet.hephaestus.practices.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeDirection;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeResolvedBy;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProfileChangeDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProfileChangeType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String practiceSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String practiceName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String groupName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String from,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String to,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ProfileChangeDirection direction,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID feedbackId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ProfileChangeResolvedBy resolvedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Integer cleanNeeded,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ItemRefDTO> evidence) {
    public ProfileChangeDTO {
        evidence = List.copyOf(evidence);
    }
}
