package de.tum.in.www1.hephaestus.contributors;

import java.io.Serial;

/**
 * Signals that fetching contributor information from GitHub failed.
 *
 * <p>This exception should be thrown when the GitHub API call fails, allowing callers
 * to distinguish between "no contributors" and "API failure".
 */
public class ContributorFetchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ContributorFetchException(String message) {
        super(message);
    }

    public ContributorFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
