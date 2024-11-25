package de.tum.in.www1.hephaestus.workspace;

public class RepositoryNotFoundException extends RuntimeException {

    public RepositoryNotFoundException(String nameWithOwner) {
        super("Repository with name '" + nameWithOwner + "' not found");
    }
}
