package de.tum.cit.aet.hephaestus.agent.catalog;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when creating an LLM connection with a slug that already exists. Mapped to HTTP 409. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LlmConnectionSlugConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmConnectionSlugConflictException(String slug) {
        super("An LLM connection with slug '" + slug + "' already exists.");
    }
}
