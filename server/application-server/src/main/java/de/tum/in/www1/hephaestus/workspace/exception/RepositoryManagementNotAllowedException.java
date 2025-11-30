package de.tum.in.www1.hephaestus.workspace.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when repository management operations (add/remove) are not allowed.
 * This applies to workspaces managed by GitHub App Installations, where repositories
 * are automatically synced based on the installation's repository selection.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RepositoryManagementNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryManagementNotAllowedException(String workspaceSlug) {
        super(
            "Repository management is not allowed for workspace '" +
            workspaceSlug +
            "'. " +
            "This workspace is managed by a GitHub App Installation. " +
            "Repositories are automatically synced based on the installation's configuration."
        );
    }
}
