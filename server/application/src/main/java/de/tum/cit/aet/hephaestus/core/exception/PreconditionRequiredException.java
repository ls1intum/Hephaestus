package de.tum.cit.aet.hephaestus.core.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class PreconditionRequiredException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PreconditionRequiredException() {
        super(
                HttpStatus.PRECONDITION_REQUIRED,
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.PRECONDITION_REQUIRED, "Reload this page before saving changes."),
                null);
        getBody().setTitle("Current version required");
    }
}
