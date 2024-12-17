package de.tum.in.www1.hephaestus.workspace;

public class NoWorkspaceFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoWorkspaceFoundException() {
        super("No workspace found");
    }
}
