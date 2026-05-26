package de.tum.cit.aet.hephaestus.integration.scm.common.exception;

/**
 * The git provider <em>definitively</em> responded that a repository does not exist —
 * distinct from a transient failure ({@code Optional.empty()}). Only callers taking
 * irreversible action (e.g. removing a {@code RepositoryToMonitor} row) should react
 * to this; transient-tolerant callers stay on the Optional path.
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
