package de.tum.cit.aet.hephaestus.core.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class PreconditionFailedException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PreconditionFailedException() {
        this(null);
    }

    public PreconditionFailedException(@Nullable Throwable cause) {
        super(
                HttpStatus.PRECONDITION_FAILED,
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.PRECONDITION_FAILED, "Reload this page, then review your changes and try again."),
                cause);
        getBody().setTitle("This changed after you loaded it");
    }
}
