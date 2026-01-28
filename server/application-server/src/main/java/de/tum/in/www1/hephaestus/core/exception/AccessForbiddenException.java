package de.tum.in.www1.hephaestus.core.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Generic unchecked exception for access forbidden (i.e. 403) errors.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessForbiddenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccessForbiddenException(String message) {
        super(message);
    }
}
