package de.tum.in.www1.hephaestus.agent.mentor.chat.exception;

import java.io.Serial;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when the in-flight unique partial index fires for a thread (cross-replica double-submit). */
@ResponseStatus(HttpStatus.CONFLICT)
public final class TurnAlreadyInFlightException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TurnAlreadyInFlightException(UUID threadId, Throwable cause) {
        super("Another turn is already in flight for thread " + threadId, cause);
    }
}
