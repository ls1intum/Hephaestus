package de.tum.cit.aet.hephaestus.core.exception;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditUnavailableException;
import de.tum.cit.aet.hephaestus.core.tenancy.TenancyViolationException;
import de.tum.cit.aet.hephaestus.integration.core.connection.CredentialUnreadableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler providing RFC 7807 ProblemDetail responses for all controllers.
 *
 * <p>This handler has the lowest precedence, allowing package-specific handlers
 * (like WorkspaceControllerAdvice) to take priority for their domains.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    // STANDARD EXCEPTIONS

    @ExceptionHandler(CredentialUnreadableException.class)
    public ProblemDetail handleCredentialUnreadable(CredentialUnreadableException exception) {
        // A state of the connection, so the answer names it and what clears it; nothing here is a fault
        // of the request or of the server's ability to serve it.
        return problem(HttpStatus.CONFLICT, "Credential unreadable", messageOf(exception));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleNotFound(EntityNotFoundException exception) {
        log.debug("Handled entity not found exception: message={}", messageOf(exception));
        return problem(HttpStatus.NOT_FOUND, "Resource not found", messageOf(exception));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("Handled no resource found exception: message={}", messageOf(exception));
        return problem(HttpStatus.NOT_FOUND, "Resource not found", messageOf(exception));
    }

    @ExceptionHandler(AccessForbiddenException.class)
    ProblemDetail handleForbidden(AccessForbiddenException exception) {
        log.warn("Handled access forbidden exception: message={}", messageOf(exception));
        return problem(HttpStatus.FORBIDDEN, "Access denied", messageOf(exception));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException exception) {
        log.debug("Authorization denied: message={}", messageOf(exception));
        return problem(HttpStatus.FORBIDDEN, "Access denied", "Insufficient permissions for this operation.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        log.debug("Handled bad request exception: message={}", messageOf(exception));
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", messageOf(exception));
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException exception) {
        log.warn("Handled illegal state exception: message={}", messageOf(exception));
        return problem(HttpStatus.CONFLICT, "Invalid state", messageOf(exception));
    }

    @ExceptionHandler(ConfigAuditUnavailableException.class)
    ProblemDetail handleConfigAuditUnavailable(ConfigAuditUnavailableException exception) {
        // 500, not 4xx: the caller did nothing wrong, and a 4xx would keep this out of the error budget
        // that makes a fail-closed audit trail's failure visible. The specific cause stays server-side.
        log.error("Config audit unavailable, change refused: message={}", messageOf(exception));
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Change not recorded",
                "The change was refused because it could not be written to the audit log.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        // Unique/FK constraint races (e.g. concurrent connection installs) → 409, not a 500.
        log.warn(
                "Handled data integrity violation: message={}",
                exception.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with the current resource state.");
    }

    // VALIDATION EXCEPTIONS

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, List<String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField, Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body contains invalid fields");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, List<String>> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> leafProperty(violation).orElse("value"),
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Constraint validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    // EXTERNAL SERVICE EXCEPTIONS

    @ExceptionHandler(WebClientRequestException.class)
    ProblemDetail handleWebClientRequestException(WebClientRequestException exception) {
        // All WebClient request failures (connection refused, DNS, timeout, etc.) are unexpected
        // and warrant WARN level - environment-specific log filtering should be configured externally
        log.warn("External service request failed: uri={}, reason={}", exception.getUri(), messageOf(exception));

        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service unavailable",
                "An upstream service is temporarily unavailable. Please try again later.");
    }

    // FALLBACK HANDLER

    @ExceptionHandler(TenancyViolationException.class)
    ProblemDetail handleTenancyViolation(TenancyViolationException ex) {
        // Server bug — log full SQL context server-side, sanitize client-facing message.
        log.error("Tenancy violation: unguardedTables={}", ex.unguardedTables(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception exception) {
        // Log the full exception for debugging, but don't expose details to client
        log.error(
                "Caught unhandled exception: exceptionType={}",
                exception.getClass().getSimpleName(),
                exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred. Please try again later.");
    }

    // HELPER METHODS

    private static String messageOf(Exception exception) {
        return exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private static Optional<String> leafProperty(ConstraintViolation<?> violation) {
        String lastNode = null;
        for (Path.Node node : violation.getPropertyPath()) {
            lastNode = node.getName();
        }
        return Optional.ofNullable(lastNode);
    }
}
