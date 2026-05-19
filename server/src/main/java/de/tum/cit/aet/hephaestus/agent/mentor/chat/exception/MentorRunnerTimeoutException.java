package de.tum.cit.aet.hephaestus.agent.mentor.chat.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A JSON-RPC call to the runner did not return within its deadline. */
@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public final class MentorRunnerTimeoutException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MentorRunnerTimeoutException(String message) {
        super(message);
    }
}
