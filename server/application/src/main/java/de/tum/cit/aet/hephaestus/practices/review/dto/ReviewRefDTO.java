package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewRefDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemRefDTO item,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String itemVersion,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewOrigin origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReviewStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant completedAt) {}
