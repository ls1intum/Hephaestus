package de.tum.cit.aet.hephaestus.core.settings;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InstanceSettingsAdminController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class InstanceSettingsControllerAdvice {

    @ExceptionHandler(StaleInstanceSettingsException.class)
    ProblemDetail handleStaleSettings(StaleInstanceSettingsException exception) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Instance settings changed", exception.getMessage());
    }

    @ExceptionHandler(InstanceSettingsPreconditionRequiredException.class)
    ProblemDetail handleMissingPrecondition(InstanceSettingsPreconditionRequiredException exception) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "Instance settings version required", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
