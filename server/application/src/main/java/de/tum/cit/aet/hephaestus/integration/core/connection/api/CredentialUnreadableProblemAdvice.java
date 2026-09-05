package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionModeConflictException;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialUnreadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * An unreadable credential, or a write the connection's mode cannot take, is a state of the
 * connection, so the answer names it and what clears it; nothing here is a fault of the request or of
 * the server's ability to serve it. Lives beside the connection API rather than in the core advice,
 * which must not depend on integration types. Unordered: {@code GlobalControllerAdvice} sits at
 * {@code LOWEST_PRECEDENCE}, so these handlers already win over its catch-all without outranking a
 * future advice more specific than they are.
 */
@RestControllerAdvice
public class CredentialUnreadableProblemAdvice {

    @ExceptionHandler(CredentialUnreadableException.class)
    public ProblemDetail handle(CredentialUnreadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Credential unreadable");
        return problem;
    }

    /** A write the connection's mode cannot take is a state conflict, and says which state. */
    @ExceptionHandler(ConnectionModeConflictException.class)
    public ProblemDetail handle(ConnectionModeConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Connection mode conflict");
        return problem;
    }
}
