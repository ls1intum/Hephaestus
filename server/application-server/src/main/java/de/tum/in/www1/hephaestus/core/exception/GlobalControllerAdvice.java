package de.tum.in.www1.hephaestus.core.exception;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    // ========================================================================
    // STANDARD EXCEPTIONS
    // ========================================================================

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleNotFound(EntityNotFoundException exception) {
        log.debug("Handled entity not found exception: message={}", exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("Handled no resource found exception: message={}", exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    ProblemDetail handleForbidden(AccessForbiddenException exception) {
        log.warn("Handled access forbidden exception: message={}", exception.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        log.debug("Handled bad request exception: message={}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException exception) {
        log.warn("Handled illegal state exception: message={}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, "Invalid state", exception.getMessage());
    }

    // ========================================================================
    // VALIDATION EXCEPTIONS
    // ========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, List<String>> errors = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(
                Collectors.groupingBy(
                    FieldError::getField,
                    Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                )
            );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Request body contains invalid fields"
        );
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, List<String>> errors = exception
            .getConstraintViolations()
            .stream()
            .collect(
                Collectors.groupingBy(
                    violation -> leafProperty(violation).orElse("value"),
                    Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                )
            );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Constraint validation failed"
        );
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    // ========================================================================
    // FALLBACK HANDLER
    // ========================================================================

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception exception) {
        // Log the full exception for debugging, but don't expose details to client
        log.error("Caught unhandled exception: exceptionType={}", exception.getClass().getSimpleName(), exception);
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "An unexpected error occurred. Please try again later."
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

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
