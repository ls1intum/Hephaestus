package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import java.io.Serial;

/**
 * Signals that a GitLab GraphQL sync operation received an invalid or unexpected response.
 */
public class GitLabSyncException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GitLabSyncException(String message) {
        super(message);
    }
}
