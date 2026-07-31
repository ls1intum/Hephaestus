package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeConflictException;
import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticePreconditionRequiredException;
import de.tum.cit.aet.hephaestus.practices.curated.StaleCuratedPracticeException;
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

    @ExceptionHandler(PracticeAreaSlugConflictException.class)
    ProblemDetail handleAreaSlugConflict(PracticeAreaSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Practice area slug conflict", exception.getMessage());
    }

    @ExceptionHandler(CuratedPracticeConflictException.class)
    ProblemDetail handleCuratedConflict(CuratedPracticeConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Curated practice conflict", exception.getMessage());
    }

    @ExceptionHandler(StaleCuratedPracticeException.class)
    ProblemDetail handleStaleCuratedPractice(StaleCuratedPracticeException exception) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Curated practice changed", exception.getMessage());
    }

    @ExceptionHandler(CuratedPracticePreconditionRequiredException.class)
    ProblemDetail handleCuratedPreconditionRequired(CuratedPracticePreconditionRequiredException exception) {
        return problem(
            HttpStatus.PRECONDITION_REQUIRED,
            "Curated practice precondition required",
            exception.getMessage()
        );
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
