package de.tum.cit.aet.hephaestus.agent.config;

import java.io.Serial;

/**
 * Thrown when an agent config cannot be deleted because it is still bound as a workspace's
 * practice-detection or mentor runtime. The admin must unbind it first. Maps to HTTP 409.
 */
public class AgentConfigBoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AgentConfigBoundException(String message) {
        super(message);
    }
}
