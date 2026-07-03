package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error mapper for the Slack admin controllers. Scoped to the {@code integration.slack} package so every Slack
 * REST route returns RFC-7807 {@code ProblemDetail} for its domain violations, following the
 * {@code docs/contributor/api-error-handling.md} convention (mirrors {@code WorkspaceControllerAdvice}). Only the
 * Slack-specific consent violation is mapped here; not-found / validation / auth flow through the global advice.
 */
@RestControllerAdvice(basePackages = "de.tum.cit.aet.hephaestus.integration.slack")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlackChannelControllerAdvice {

    @ExceptionHandler(SlackChannelConsentViolationException.class)
    ProblemDetail handleConsentViolation(SlackChannelConsentViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Slack channel consent violation");
        problem.setDetail(
            Optional.ofNullable(exception.getMessage())
                .map(LoggingUtils::sanitizeForLog)
                .filter(s -> !s.isBlank())
                .orElse("The requested consent transition is not allowed from the channel's current state.")
        );
        return problem;
    }
}
