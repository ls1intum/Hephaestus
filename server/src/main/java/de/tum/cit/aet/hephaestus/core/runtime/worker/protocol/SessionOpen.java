package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

import tools.jackson.databind.JsonNode;

/** Hub → worker request to start an interactive session. {@code context} is opaque to the substrate; the runner for {@link SessionKind} deserializes it. */
public record SessionOpen(String sessionId, SessionKind kind, JsonNode context) implements WorkerControlFrame {
    public SessionOpen {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }
}
