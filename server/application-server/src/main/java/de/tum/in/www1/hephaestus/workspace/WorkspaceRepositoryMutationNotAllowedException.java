package de.tum.in.www1.hephaestus.workspace;

/**
 * Raised when a repository mutation is attempted on a workspace that does not allow manual management
 * (for example, GitHub App backed installations where repository membership is driven by the installation).
 */
public class WorkspaceRepositoryMutationNotAllowedException extends RuntimeException {

    public WorkspaceRepositoryMutationNotAllowedException(String message) {
        super(message);
    }
}
