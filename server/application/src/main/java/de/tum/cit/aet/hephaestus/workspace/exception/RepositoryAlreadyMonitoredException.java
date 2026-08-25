package de.tum.cit.aet.hephaestus.workspace.exception;

public class RepositoryAlreadyMonitoredException extends RuntimeException {

    public RepositoryAlreadyMonitoredException(String nameWithOwner) {
        super("Repository with name '" + nameWithOwner + "' is already monitored");
    }
}
