package de.tum.in.www1.hephaestus.agent.task;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
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
     * Output JSON writer. Configured with sorted-keys + pretty-print so the byte representation
     * is stable across runs and JVMs — the fixture snapshot in
     * {@code WorkspaceContextSnapshotTest} byte-compares against it.
     */
    private final ObjectWriter writer;

    public TaskEnvelopeWriter(ObjectMapper baseObjectMapper) {
        // Jackson 3: ObjectMapper is immutable. Inherit the central mapper's modules and
        // null-handling, then derive an ObjectWriter with sorted keys + pretty printing.
        // ObjectMapper.writer(SerializationFeature, ...) avoids the (JsonMapper) cast that
        // breaks when tests pass a plain ObjectMapper instead of a JsonMapper subtype.
        this.writer = baseObjectMapper
            .writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .withDefaultPrettyPrinter();
    }

    /** Serialize the envelope to pretty-printed UTF-8 bytes. */
    public byte[] write(TaskEnvelope envelope) {
        try {
            return writer.writeValueAsBytes(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize TaskEnvelope", e);
        }
    }

    /** Convenience: serialize to a UTF-8 string (used by tests / fixtures). */
    public String writeAsString(TaskEnvelope envelope) {
        return new String(write(envelope), StandardCharsets.UTF_8);
    }
}
