package de.tum.cit.aet.hephaestus.practices.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Converts between {@link JsonNode} (entity storage) and {@code List<String>} (DTO representation)
 * for practice trigger events.
 */
public final class TriggerEventsConverter {

    private TriggerEventsConverter() {}

    /**
     * Converts a JSONB array node to a list of event name strings.
     */
    public static List<String> toList(JsonNode triggerEvents) {
        if (triggerEvents == null || !triggerEvents.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(triggerEvents.size());
        for (JsonNode node : triggerEvents) {
            result.add(node.asString());
        }
        return List.copyOf(result);
    }

    /**
     * Converts a list of event name strings to a JSONB array node.
     */
    public static JsonNode toJsonNode(List<String> events) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        if (events != null) {
            events.forEach(array::add);
        }
        return array;
    }
}
