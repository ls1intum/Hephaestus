package de.tum.in.www1.hephaestus.gitprovider.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Centralized service for deserializing NATS messages.
 * This avoids injecting ObjectMapper into every message handler.
 */
@Component
public class NatsMessageDeserializer {

    private final ObjectMapper objectMapper;

    public NatsMessageDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Deserialize a NATS message payload to the specified type.
     *
     * @param message The NATS message
     * @param type    The target class
     * @param <T>     The target type
     * @return The deserialized object
     * @throws IOException If deserialization fails
     */
    public <T> T deserialize(Message message, Class<T> type) throws IOException {
        String payload = new String(message.getData(), StandardCharsets.UTF_8);
        return objectMapper.readValue(payload, type);
    }

    /**
     * Get the raw payload as a string.
     *
     * @param message The NATS message
     * @return The payload as a UTF-8 string
     */
    public String getPayloadAsString(Message message) {
        return new String(message.getData(), StandardCharsets.UTF_8);
    }
}
