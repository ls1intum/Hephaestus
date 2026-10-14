package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemEventDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String itemId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FoundBy foundBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemEventState state,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ItemEventReason reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant decidedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        UUID reviewId) {}
