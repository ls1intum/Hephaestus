package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapper for practices-specific exceptions.
 * Kept in the practices module to avoid a cyclic dependency between practices and core.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PracticesControllerAdvice {

    @ExceptionHandler(PracticeSlugConflictException.class)
    ProblemDetail handleSlugConflict(PracticeSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Practice slug conflict", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(
            Optional.ofNullable(detail)
                .map(LoggingUtils::sanitizeForLog)
                .filter(s -> !s.isBlank())
                .orElse("The practice request could not be processed.")
        );
        return problem;
    }
}
