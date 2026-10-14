package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogConflictException;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogPreconditionRequiredException;
import de.tum.cit.aet.hephaestus.practices.catalog.StaleCatalogEntryException;
import de.tum.cit.aet.hephaestus.practices.curated.adoption.CatalogAdoptionPreconditionRequiredException;
import de.tum.cit.aet.hephaestus.practices.curated.adoption.StaleCatalogAdoptionPlanException;
import de.tum.cit.aet.hephaestus.practices.review.InvalidReviewScopeException;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewPreconditionRequiredException;
import de.tum.cit.aet.hephaestus.practices.review.StalePracticeReviewSettingsException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Practices exception handling stays in this module to avoid a dependency cycle with core.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PracticesControllerAdvice {

    @ExceptionHandler(StalePracticeVersionException.class)
    ProblemDetail handleStaleVersion(StalePracticeVersionException exception) {
        var detail = problem(
                HttpStatus.CONFLICT,
                "Practice version changed",
                Objects.toString(exception.getMessage(), "The practice version changed."));
        detail.setProperty("currentVersionId", exception.currentVersionId());
        return detail;
    }

    @ExceptionHandler(PracticeSlugConflictException.class)
    ProblemDetail handleSlugConflict(PracticeSlugConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice slug conflict",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(PracticeGroupSlugConflictException.class)
    ProblemDetail handleGroupSlugConflict(PracticeGroupSlugConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice group slug conflict",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(CatalogConflictException.class)
    ProblemDetail handleCatalogConflict(CatalogConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice catalog conflict",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(StaleCatalogEntryException.class)
    ProblemDetail handleStaleCatalogEntry(StaleCatalogEntryException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice catalog changed",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(CatalogPreconditionRequiredException.class)
    ProblemDetail handleCatalogPreconditionRequired(CatalogPreconditionRequiredException exception) {
        return problem(
                HttpStatus.PRECONDITION_REQUIRED,
                "Practice catalog version required",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(StaleCatalogAdoptionPlanException.class)
    ProblemDetail handleStaleAdoptionPlan(StaleCatalogAdoptionPlanException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice adoption preview changed",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(CatalogAdoptionPreconditionRequiredException.class)
    ProblemDetail handleAdoptionPreconditionRequired(CatalogAdoptionPreconditionRequiredException exception) {
        return problem(
                HttpStatus.PRECONDITION_REQUIRED,
                "Practice adoption preview required",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(StalePracticeReviewSettingsException.class)
    ProblemDetail handleStaleReviewSettings(StalePracticeReviewSettingsException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice review settings changed",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(PracticeReviewPreconditionRequiredException.class)
    ProblemDetail handleReviewPreconditionRequired(PracticeReviewPreconditionRequiredException exception) {
        return problem(
                HttpStatus.PRECONDITION_REQUIRED,
                "Practice review settings version required",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(PracticeReleasePreconditionRequiredException.class)
    ProblemDetail handleReleasePreconditionRequired(PracticeReleasePreconditionRequiredException exception) {
        return problem(
                HttpStatus.PRECONDITION_REQUIRED,
                "Practice release version required",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(StalePracticeReleaseException.class)
    ProblemDetail handleStaleRelease(StalePracticeReleaseException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Practice release changed",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    @ExceptionHandler(InvalidReviewScopeException.class)
    ProblemDetail handleInvalidReviewScope(InvalidReviewScopeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "We could not save the review scope",
                Objects.toString(exception.getMessage(), "Request failed"));
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(Optional.ofNullable(detail)
                .map(LoggingUtils::sanitizeForLog)
                .filter(s -> !s.isBlank())
                .orElse("The practice request could not be processed."));
        return problem;
    }
}
