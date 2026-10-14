package de.tum.cit.aet.hephaestus.practices;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PracticeSlugConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PracticeSlugConflictException(String message) {
        super(message);
    }

    public PracticeSlugConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
