package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/** Bidirectional session termination. Whichever side sends first wins; a duplicate inbound close is a no-op. */
public record SessionClose(String sessionId, SessionCloseReason reason) implements WorkerControlFrame {
    public SessionClose {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
    }
}
