package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FrozenModelDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerProtocol,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerModelId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reasoningEffort,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, JsonNode> options) {
    public FrozenModelDTO {
        options = Map.copyOf(options);
    }
}
