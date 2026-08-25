package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

/** Forward-only lifecycle for an {@link AttachedSandbox}: {@code ATTACHED → CLOSING → CLOSED}. */
public enum AttachedSandboxState {
    ATTACHED,
    CLOSING,
    CLOSED,
}
