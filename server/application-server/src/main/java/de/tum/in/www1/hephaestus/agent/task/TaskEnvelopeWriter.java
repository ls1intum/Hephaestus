package de.tum.in.www1.hephaestus.agent.task;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * Serializes a {@link TaskEnvelope} to bytes for the workspace input-files map.
 *
 * <p>The result is dropped into {@code Map<String, byte[]>} keyed by {@code "task.json"} —
 * the sandbox materializes the map atomically into the container, so file-system-level
 * atomic writes are not needed here.
 */
@Component
public class TaskEnvelopeWriter {

    /**
     * Output JSON key order. Locked because the byte representation participates in fixture
     * snapshots — see {@code WorkspaceContextSnapshotTest}.
     */
    private final ObjectMapper writer;

    public TaskEnvelopeWriter(ObjectMapper baseObjectMapper) {
        // Copy the central mapper to inherit JavaTimeModule + null-handling, then enable
        // sorted keys for deterministic byte output across runs and JVMs.
        this.writer = baseObjectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** Serialize the envelope to pretty-printed UTF-8 bytes. */
    public byte[] write(TaskEnvelope envelope) {
        try {
            return writer.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize TaskEnvelope", e);
        }
    }

    /** Convenience: serialize to a UTF-8 string (used by tests / fixtures). */
    public String writeAsString(TaskEnvelope envelope) {
        return new String(write(envelope), StandardCharsets.UTF_8);
    }
}
