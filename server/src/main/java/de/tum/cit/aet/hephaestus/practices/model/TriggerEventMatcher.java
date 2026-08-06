package de.tum.cit.aet.hephaestus.practices.model;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Decides whether a practice's {@code triggerEvents} array names a given event.
 *
 * <p>One matcher, because two of them drifted: the review gate guarded the element type before
 * coercion while the catalog injector did not, so the same out-of-band JSONB edit that the gate was
 * hardened against would instead throw during job preparation — after the job had already been
 * created. Whether a practice matches an event is one question and deserves one answer.
 */
public final class TriggerEventMatcher {

    private TriggerEventMatcher() {}

    /**
     * True iff {@code triggerEvents} is an array containing {@code eventName} as a string element.
     *
     * <p>Non-string elements are skipped rather than coerced: in Jackson 3 {@code asString()} throws
     * on an object or array, and a stored practice row is not a trusted shape — it can be edited
     * outside the API. A total matcher lets a malformed row cost that one practice, not the job.
     */
    public static boolean matches(@Nullable JsonNode triggerEvents, String eventName) {
        if (triggerEvents == null || !triggerEvents.isArray()) {
            return false;
        }
        for (JsonNode node : triggerEvents) {
            if (node.isString() && eventName.equals(node.asString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The event names a practice subscribes to, in declaration order.
     *
     * <p>Same total-function stance as {@link #matches}: a malformed element is skipped, never coerced.
     * Callers that need to reason about a binding as a whole — "can anything connected here raise any of
     * these?" — need the set rather than a per-event predicate, and asking that question must not be able
     * to fail on a row somebody edited by hand.
     */
    public static Set<String> eventsOf(@Nullable JsonNode triggerEvents) {
        if (triggerEvents == null || !triggerEvents.isArray()) {
            return Set.of();
        }
        Set<String> events = new LinkedHashSet<>();
        for (JsonNode node : triggerEvents) {
            if (node.isString()) {
                events.add(node.asString());
            }
        }
        return events;
    }
}
