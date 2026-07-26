package de.tum.cit.aet.hephaestus.agent;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionInUseException;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionSlugConflictException;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelInUseException;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelSlugConflictException;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelUpstreamIdConflictException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStateConflictException;
import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.net.URI;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapper for agent-module exceptions: the job lifecycle conflict plus the LLM catalog's
 * conflict types, which the instance- and workspace-scoped connection/model controllers throw alike.
 * Kept in the agent module to avoid a cyclic dependency between agent and workspace, and scoped to
 * this package tree per {@code docs/contributor/api-error-handling.md} — one advice per bounded
 * context, not a second global catch-all.
 *
 * <p><b>Why every conflict needs an explicit handler here.</b> These are plain
 * {@code RuntimeException}s, and a {@code @ResponseStatus} on the exception class is inert in this
 * application: {@code GlobalControllerAdvice} declares {@code @ExceptionHandler(Exception.class)}, and
 * Spring's {@code ExceptionHandlerExceptionResolver} picks the first {@code @ControllerAdvice} bean
 * (by {@code @Order}) with ANY matching handler, so the catch-all wins before
 * {@link org.springframework.web.servlet.mvc.support.ResponseStatusExceptionResolver} ever runs. An
 * exception with no method below therefore returns 500 no matter what it is annotated with.
 * {@code @Order(HIGHEST_PRECEDENCE)} puts this advice ahead of that fallback.
 */
@RestControllerAdvice(basePackageClasses = AgentControllerAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentControllerAdvice {

    @ExceptionHandler(AgentJobStateConflictException.class)
    ProblemDetail handleAgentJobStateConflict(AgentJobStateConflictException exception) {
        return problem(HttpStatus.CONFLICT, "agent-job-state-conflict", "Agent job state conflict", exception);
    }

    @ExceptionHandler(LlmConnectionInUseException.class)
    ProblemDetail handleConnectionInUse(LlmConnectionInUseException exception) {
        return problem(HttpStatus.CONFLICT, "llm-connection-in-use", "LLM connection is in use", exception);
    }

    @ExceptionHandler(LlmModelInUseException.class)
    ProblemDetail handleModelInUse(LlmModelInUseException exception) {
        return problem(HttpStatus.CONFLICT, "llm-model-in-use", "LLM model is in use", exception);
    }

    @ExceptionHandler(LlmConnectionSlugConflictException.class)
    ProblemDetail handleConnectionSlugConflict(LlmConnectionSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "llm-connection-slug-conflict", "LLM connection slug conflict", exception);
    }

    @ExceptionHandler(LlmModelSlugConflictException.class)
    ProblemDetail handleModelSlugConflict(LlmModelSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "llm-model-slug-conflict", "LLM model slug conflict", exception);
    }

    @ExceptionHandler(LlmModelUpstreamIdConflictException.class)
    ProblemDetail handleModelUpstreamIdConflict(LlmModelUpstreamIdConflictException exception) {
        return problem(
            HttpStatus.CONFLICT,
            "llm-model-upstream-id-conflict",
            "LLM model upstream id conflict",
            exception
        );
    }

    /**
     * Every problem carries a stable {@code type} URI. Left unset, RFC 7807 defines the field as
     * {@code about:blank}, which makes all six conflicts above indistinguishable to a client that does
     * not parse English prose out of {@code title}. The {@code /problems/<slug>} shape matches
     * {@code OutlineCollectionControllerAdvice}; the slug is API surface and must not be renamed
     * casually.
     */
    private ProblemDetail problem(HttpStatus status, String typeSlug, String title, Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("/problems/" + typeSlug));
        problem.setTitle(title);
        problem.setDetail(
            Optional.ofNullable(exception.getMessage())
                .map(LoggingUtils::sanitizeForLog)
                .filter(s -> !s.isBlank())
                .orElse("The agent request could not be processed.")
        );
        return problem;
    }
}
