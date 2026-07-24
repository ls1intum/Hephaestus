package de.tum.cit.aet.hephaestus.agent.catalog;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapper for the LLM catalog's domain conflict exceptions (#1368) — instance and workspace-scoped
 * connection/model controllers alike, since both layers throw the same {@code *ConflictException}/
 * {@code *InUseException} types. Without an explicit handler here these are plain
 * {@code RuntimeException}s whose {@code @ResponseStatus(CONFLICT)} would otherwise be shadowed by
 * {@code GlobalControllerAdvice}'s {@code @ExceptionHandler(Exception.class)} fallback — Spring's
 * {@code ExceptionHandlerExceptionResolver} picks the first {@code @ControllerAdvice} bean (by
 * {@code @Order}) that has ANY matching handler, so an un-ordered catch-all elsewhere in the chain
 * would win before {@link org.springframework.web.servlet.mvc.support.ResponseStatusExceptionResolver}
 * ever runs. {@code @Order(HIGHEST_PRECEDENCE)} mirrors {@code AgentControllerAdvice}'s fix for the same
 * class of bug.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LlmCatalogControllerAdvice {

    @ExceptionHandler(LlmConnectionInUseException.class)
    ProblemDetail handleConnectionInUse(LlmConnectionInUseException exception) {
        return problem(HttpStatus.CONFLICT, "LLM connection is in use", exception.getMessage());
    }

    @ExceptionHandler(LlmModelInUseException.class)
    ProblemDetail handleModelInUse(LlmModelInUseException exception) {
        return problem(HttpStatus.CONFLICT, "LLM model is in use", exception.getMessage());
    }

    @ExceptionHandler(LlmConnectionSlugConflictException.class)
    ProblemDetail handleConnectionSlugConflict(LlmConnectionSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "LLM connection slug conflict", exception.getMessage());
    }

    @ExceptionHandler(LlmModelSlugConflictException.class)
    ProblemDetail handleModelSlugConflict(LlmModelSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "LLM model slug conflict", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
