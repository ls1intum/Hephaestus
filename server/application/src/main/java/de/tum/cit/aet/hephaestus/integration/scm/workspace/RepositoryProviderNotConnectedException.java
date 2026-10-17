package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class RepositoryProviderNotConnectedException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryProviderNotConnectedException(String workspaceSlug) {
        super(HttpStatus.CONFLICT, problem(workspaceSlug), null);
    }

    private static ProblemDetail problem(String workspaceSlug) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                LoggingUtils.sanitizeForLog(
                        "The workspace “" + workspaceSlug + "” has no active GitHub or GitLab connection. "
                                + "Connect one before adding repositories to monitor."));
        problem.setTitle("Repository provider not connected");
        return problem;
    }
}
