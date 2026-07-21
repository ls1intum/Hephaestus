package de.tum.cit.aet.hephaestus.agent.catalog;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or updating a model whose (connection, upstream model id) pair already
 * belongs to another model on the same connection ({@code ux_llm_model_connection_upstream} /
 * {@code ux_ws_llm_model_connection_upstream}). Mapped to HTTP 409.
 *
 * <p>Without this guard, two catalog entries could point at the same upstream id (e.g. one FREE, one
 * PRICED) and {@code LlmUsageRecorder} would nondeterministically match either one, letting a FREE
 * sibling silently shadow a PRICED one for billing purposes (#1368).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LlmModelUpstreamIdConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmModelUpstreamIdConflictException(Long connectionId, String upstreamModelId) {
        super(
            "A model with this upstream id already exists on this provider connection: '" +
                upstreamModelId +
                "' on connection " +
                connectionId +
                "."
        );
    }
}
