package de.tum.cit.aet.hephaestus.agent.catalog;

import java.io.Serial;

/** The selected model is disabled, missing, or has no usable price. */
public class LlmModelUnavailableException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    public LlmModelUnavailableException(String message) {
        super(message);
    }
}
