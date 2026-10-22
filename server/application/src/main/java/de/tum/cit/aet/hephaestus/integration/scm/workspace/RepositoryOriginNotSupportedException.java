package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A token connection whose server Hephaestus cannot ask whether the token may read a repository. */
public final class RepositoryOriginNotSupportedException extends ErrorResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryOriginNotSupportedException(String workspaceSlug) {
        this(problem("The workspace “" + workspaceSlug
                + "” is connected to a GitHub server other than github.com. Hephaestus can confirm a "
                + "repository by hand only on github.com, so it adds none here."));
    }

    private RepositoryOriginNotSupportedException(ProblemDetail problem) {
        super(HttpStatus.CONFLICT, problem, null);
    }

    /**
     * A GitLab connection that names no server of its own, or one other than the server its token would be sent to.
     * Its repositories are recorded under one server while GitLab would be asked on another.
     */
    public static RepositoryOriginNotSupportedException forUnstatedGitLabServer() {
        return new RepositoryOriginNotSupportedException(
                problem("This GitLab connection does not state the GitLab server its token belongs to. Reconnect "
                        + "GitLab with the server URL stated, then add the project again."));
    }

    private static ProblemDetail problem(String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, LoggingUtils.sanitizeForLog(detail));
        problem.setTitle("Repository server not supported");
        return problem;
    }
}
