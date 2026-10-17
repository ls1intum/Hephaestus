package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ResultMarkDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultMarkKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO createdBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        AccountRefDTO removedBy,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant removedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String removalReason) {}
