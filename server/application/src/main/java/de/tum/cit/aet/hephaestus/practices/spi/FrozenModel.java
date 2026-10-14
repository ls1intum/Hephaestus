package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record FrozenModel(
        String modelId,
        String providerProtocol,
        String providerModelId,
        @Nullable String reasoningEffort,
        Map<String, JsonNode> options) {
    public FrozenModel {
        options = Map.copyOf(options);
    }
}
