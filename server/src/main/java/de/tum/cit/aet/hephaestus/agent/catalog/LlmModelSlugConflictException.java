package de.tum.cit.aet.hephaestus.agent.catalog;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating a model with a slug that already exists on the same connection
 * ({@code ux_llm_model_connection_slug}). Mapped to HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LlmModelSlugConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmModelSlugConflictException(Long connectionId, String slug) {
        super("A model with slug '" + slug + "' already exists on connection " + connectionId + ".");
    }
}
