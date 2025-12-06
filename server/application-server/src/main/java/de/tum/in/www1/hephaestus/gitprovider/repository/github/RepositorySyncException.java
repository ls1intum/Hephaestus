package de.tum.in.www1.hephaestus.gitprovider.repository.github;

import java.io.Serial;

/**
 * Signals that a repository could not be synchronized from GitHub because it no longer exists or is inaccessible.
 */
public class RepositorySyncException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        FORBIDDEN,
        UNKNOWN,
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private final String repositoryNameWithOwner;
    private final Reason reason;

    public RepositorySyncException(String repositoryNameWithOwner, Reason reason, Throwable cause) {
        super("Unable to sync repository '" + repositoryNameWithOwner + "': " + reason, cause);
        this.repositoryNameWithOwner = repositoryNameWithOwner;
        this.reason = reason;
    }

    public String getRepositoryNameWithOwner() {
        return repositoryNameWithOwner;
    }

    public Reason getReason() {
        return reason;
    }
}
