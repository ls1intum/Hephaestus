package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

public enum SessionCloseReason {
    COMPLETED,
    /** Browser SSE emitter timed out, errored, or closed; the hub forwards this to the worker. */
    USER_DISCONNECTED,
    /** Worker drain: in-flight session ends during graceful shutdown, no recovery. */
    WORKER_DRAINING,
    /** Unexpected failure inside the runner or transport; correlate with logs by {@code frameId}. */
    ERROR,
}
