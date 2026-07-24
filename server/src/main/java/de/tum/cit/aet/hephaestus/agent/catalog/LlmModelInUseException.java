package de.tum.cit.aet.hephaestus.agent.catalog;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when deleting an LLM model that is still bound to an agent configuration. Mapped to HTTP 409. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LlmModelInUseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmModelInUseException(Long modelId) {
        super("Cannot delete LLM model " + modelId + ": one or more agent configurations still use it.");
    }
}
