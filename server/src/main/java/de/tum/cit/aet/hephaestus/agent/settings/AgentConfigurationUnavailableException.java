package de.tum.cit.aet.hephaestus.agent.settings;

import java.io.Serial;

/** Thrown when a workspace tries to bind a disabled config or one whose catalog model is unavailable. */
public class AgentConfigurationUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigurationUnavailableException() {
        super("The selected agent configuration is disabled or its model is not available.");
    }
}
