package de.tum.cit.aet.hephaestus.integration.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemRefDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$") @Nullable
        String parentId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        IntegrationKind provider,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String url,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String container,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String path,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        Instant removedAt) {}
