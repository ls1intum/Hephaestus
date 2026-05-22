package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/** Worker → hub stdout chunk. {@code terminal=true} marks the last chunk — the hub completes the SSE emitter then. */
public record SessionOutput(String sessionId, String payload, boolean terminal) implements WorkerControlFrame {
    public SessionOutput {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }
}
