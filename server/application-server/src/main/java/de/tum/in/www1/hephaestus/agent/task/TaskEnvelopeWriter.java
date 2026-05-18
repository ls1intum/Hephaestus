package de.tum.in.www1.hephaestus.agent.task;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Serializes a {@link TaskEnvelope} to bytes for the workspace input-files map. */
@Component
public class TaskEnvelopeWriter {

    // Sorted keys + pretty printing make the output deterministic across runs/JVMs so
    // WorkspaceContextSnapshotTest can byte-compare against the committed fixture.
    private final ObjectWriter writer;

    public TaskEnvelopeWriter(ObjectMapper baseObjectMapper) {
        this.writer = baseObjectMapper
            .writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .withDefaultPrettyPrinter();
    }

    public byte[] write(TaskEnvelope envelope) {
        try {
            return writer.writeValueAsBytes(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize TaskEnvelope", e);
        }
    }

    public String writeAsString(TaskEnvelope envelope) {
        return new String(write(envelope), StandardCharsets.UTF_8);
    }
}
