package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Lifecycle states for an {@link AttachedSandbox}.
 *
 * <p>Transitions are forward-only: {@code ATTACHED → CLOSING → CLOSED}. The implementation uses
 * {@link java.util.concurrent.atomic.AtomicReference#compareAndSet} to serialise transitions
 * against concurrent {@code close()} / reaper / EOF / write-error callers.
 */
public enum AttachedSandboxState {
    /** Session is alive — {@code send()} accepted, frames flow to subscribers. */
    ATTACHED,
    /** {@code close()} has been initiated. {@code send()} rejected. Pump may still be draining. */
    CLOSING,
    /** Session fully terminated. {@code subscribe()} returns an already-disposed handle. */
    CLOSED,
}
