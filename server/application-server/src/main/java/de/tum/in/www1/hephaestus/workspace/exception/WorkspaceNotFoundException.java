package de.tum.in.www1.hephaestus.workspace.exception;

public class WorkspaceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WorkspaceNotFoundException(String slug) {
        super("Workspace not found: " + slug);
    }
}
