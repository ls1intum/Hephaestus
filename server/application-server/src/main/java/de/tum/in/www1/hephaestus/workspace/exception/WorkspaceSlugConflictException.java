package de.tum.in.www1.hephaestus.workspace.exception;

public class WorkspaceSlugConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WorkspaceSlugConflictException(String slug) {
        super("Workspace slug already exists: " + slug);
    }

    public WorkspaceSlugConflictException(String slug, Throwable cause) {
        super("Workspace slug already exists: " + slug, cause);
    }
}
