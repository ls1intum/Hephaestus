package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/** Forward-only lifecycle for an {@link AttachedSandbox}: {@code ATTACHED → CLOSING → CLOSED}. */
public enum AttachedSandboxState {
    ATTACHED,
    CLOSING,
    CLOSED,
}
