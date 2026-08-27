package de.tum.cit.aet.hephaestus.workspace.exception;

public class InvalidWorkspaceSlugException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidWorkspaceSlugException(String slug) {
        super("Invalid workspace slug: " + slug);
    }
}
