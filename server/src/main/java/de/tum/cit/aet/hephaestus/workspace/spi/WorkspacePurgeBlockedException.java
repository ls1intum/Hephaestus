package de.tum.cit.aet.hephaestus.workspace.spi;

public class WorkspacePurgeBlockedException extends RuntimeException {

    public WorkspacePurgeBlockedException(String message) {
        super(message);
    }

    public WorkspacePurgeBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
