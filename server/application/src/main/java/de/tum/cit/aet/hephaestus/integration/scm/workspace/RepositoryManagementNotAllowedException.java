package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class RepositoryManagementNotAllowedException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryManagementNotAllowedException(String workspaceSlug) {
        super(HttpStatus.BAD_REQUEST, problem(workspaceSlug), null);
    }

    private static ProblemDetail problem(String workspaceSlug) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                LoggingUtils.sanitizeForLog("Repository management is not allowed for workspace “" + workspaceSlug
                        + "”. A GitHub App installation manages this workspace. "
                        + "The server syncs repositories automatically from the installation configuration."));
        problem.setTitle("Repository management not allowed");
        return problem;
    }
}
