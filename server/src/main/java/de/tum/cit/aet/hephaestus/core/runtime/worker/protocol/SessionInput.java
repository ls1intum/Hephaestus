package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/**
 * Hub → worker stdin chunk. At-most-once: lost frames close the session via {@link SessionClose}
 * with {@link SessionCloseReason#ERROR} rather than replay — mentor turns are not idempotent and
 * replay would double-charge LLM cost.
 */
public record SessionInput(String sessionId, String payload) implements WorkerControlFrame {
    public SessionInput {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }
}
