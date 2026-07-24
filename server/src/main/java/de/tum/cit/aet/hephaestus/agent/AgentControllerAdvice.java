package de.tum.cit.aet.hephaestus.agent;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobStateConflictException;
import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapper for agent-specific exceptions.
 * Kept in the agent module to avoid a cyclic dependency between agent and workspace.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentControllerAdvice {

    @ExceptionHandler(AgentJobStateConflictException.class)
    ProblemDetail handleAgentJobStateConflict(AgentJobStateConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Agent job state conflict", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(
            Optional.ofNullable(detail)
                .map(LoggingUtils::sanitizeForLog)
                .filter(s -> !s.isBlank())
                .orElse("The agent request could not be processed.")
        );
        return problem;
    }
}
