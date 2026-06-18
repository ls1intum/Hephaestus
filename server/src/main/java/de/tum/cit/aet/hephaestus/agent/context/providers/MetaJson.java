package de.tum.cit.aet.hephaestus.agent.context.providers;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Null-tolerant readers for optional fields on a JSON node, shared by the cross-context providers so the
 * same trivial accessor is not copy-pasted into each one. Returns {@code null} when the field is absent,
 * JSON-null, the wrong type, or blank — the providers treat that uniformly as "signal not present".
 */
final class MetaJson {

    private MetaJson() {}

    static @Nullable Long optLong(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull() && node.get(field).isNumber()) {
            return node.get(field).asLong();
        }
        return null;
    }

    static @Nullable Integer optInteger(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull() && node.get(field).isNumber()) {
            return node.get(field).asInt();
        }
        return null;
    }

    static @Nullable String optString(JsonNode node, String field) {
        // isString(), not just non-null: a numeric/boolean node would coerce to a non-null digit/"true"
        // string and silently pass as a branch/SHA, so the "wrong type → null" contract must guard the type.
        if (node.has(field) && node.get(field).isString()) {
            String value = node.get(field).asString();
            return (value != null && !value.isBlank()) ? value : null;
        }
        return null;
    }
}
