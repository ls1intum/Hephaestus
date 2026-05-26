package de.tum.cit.aet.hephaestus.integration.webhook;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable value built by the controllers and consumed by {@link JetStreamPublisher}. The seam
 * between pure verifier/builder logic and the NATS publisher: tests assert on this value, never
 * on jnats directly.
 */
public record PublishRequest(String subject, String dedupId, Map<String, String> headers, byte[] body) {
    public PublishRequest {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(dedupId, "dedupId");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        headers = Map.copyOf(headers);
    }
}
