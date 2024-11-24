package de.tum.in.www1.hephaestus.workspace;

public class RepositoryAlreadyMonitoredException extends RuntimeException {

    public RepositoryAlreadyMonitoredException(String nameWithOwner) {
        super("Repository with name '" + nameWithOwner + "' is already monitored");
    }
}
