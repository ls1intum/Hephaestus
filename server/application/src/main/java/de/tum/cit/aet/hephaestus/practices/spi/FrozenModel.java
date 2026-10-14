package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public record FrozenModel(
        String modelId,
        String providerProtocol,
        String providerModelId,
        @Nullable String reasoningEffort,
        Map<String, JsonNode> options) {
    public FrozenModel {
        // JSON persistence preserves integral values, but not their node types.
        options = options.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> {
            var value = entry.getValue();
            return value.isIntegralNumber() ? JsonNodeFactory.instance.numberNode(value.bigIntegerValue()) : value;
        }));
    }
}
