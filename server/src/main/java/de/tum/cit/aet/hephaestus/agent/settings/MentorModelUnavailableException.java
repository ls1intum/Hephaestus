package de.tum.cit.aet.hephaestus.agent.settings;

import java.io.Serial;

/** Thrown when a workspace tries to bind a disabled or currently unavailable mentor model. */
public class MentorModelUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MentorModelUnavailableException() {
        super("The configured mentor model is not available.");
    }
}
