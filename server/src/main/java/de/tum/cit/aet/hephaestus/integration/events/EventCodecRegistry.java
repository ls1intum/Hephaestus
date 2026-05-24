package de.tum.cit.aet.hephaestus.integration.events;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Longest-prefix-match registry of {@link EventCodec}s keyed by {@code ce.type} prefix.
 *
 * <p>Built from constructor-injected list of beans; immutable after construction.
 * Duplicate prefixes fail-fast.
 */
@Component
public class EventCodecRegistry {

    private final NavigableMap<String, EventCodec> byPrefix = new TreeMap<>();

    public EventCodecRegistry(List<EventCodec> codecs) {
        for (EventCodec codec : codecs) {
            String prefix = codec.typePrefix();
            if (byPrefix.putIfAbsent(prefix, codec) != null) {
                throw new IllegalStateException(
                    "duplicate EventCodec prefix '" + prefix + "' — " + codec.getClass()
                        + " vs " + byPrefix.get(prefix).getClass()
                );
            }
        }
    }

    /** Returns the codec with the longest prefix matching the event's {@code type}. */
    public Optional<EventCodec> find(String type) {
        if (type == null || type.isBlank()) return Optional.empty();
        var floor = byPrefix.floorEntry(type);
        if (floor != null && type.startsWith(floor.getKey())) {
            return Optional.of(floor.getValue());
        }
        return Optional.empty();
    }
}
