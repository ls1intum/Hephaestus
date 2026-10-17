package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class RepositoryAlreadyMonitoredException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryAlreadyMonitoredException(String nameWithOwner) {
        super(HttpStatus.CONFLICT, problem(nameWithOwner), null);
    }

    private static ProblemDetail problem(String nameWithOwner) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                LoggingUtils.sanitizeForLog("The repository “" + nameWithOwner + "” is already monitored."));
        problem.setTitle("Repository already monitored");
        return problem;
    }
}
