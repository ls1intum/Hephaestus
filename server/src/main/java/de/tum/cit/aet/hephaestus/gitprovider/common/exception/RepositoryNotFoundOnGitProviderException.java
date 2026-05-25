package de.tum.cit.aet.hephaestus.gitprovider.common.exception;

/**
 * Thrown when the git provider (GitHub/GitLab) has <em>definitively</em> responded that a
 * repository does not exist — distinct from a transient inability to ask (auth failure,
 * transport error, rate limit, etc.).
 *
 * <p>Only callers that want to take an irreversible action on the result (e.g. removing a
 * {@code RepositoryToMonitor} row) should react to this exception. Callers that want to
 * tolerate transient failure should still receive {@code Optional.empty()} from the sync
 * method and re-attempt on the next cycle.
 *
 * <p>Background: pass-14 of #1198 caught a live bug where a transient GitHub App token-mint
 * failure (no installation credentials configured) caused {@code GitHubRepositorySyncService}
 * to return {@code Optional.empty()}, which the caller then misread as "repository does not
 * exist on GitHub" and deleted the user-configured monitoring row. This exception type
 * exists so the caller can distinguish "I confirmed the repo is gone" from "I never reached
 * the provider".
 */
public class RepositoryNotFoundOnGitProviderException extends RuntimeException {

    private final String nameWithOwner;

    public RepositoryNotFoundOnGitProviderException(String nameWithOwner) {
        super("Repository not found on git provider: " + nameWithOwner);
        this.nameWithOwner = nameWithOwner;
    }

    public String getNameWithOwner() {
        return nameWithOwner;
    }
}
