package de.tum.in.www1.hephaestus.admin;

public class NoAdminConfigFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoAdminConfigFoundException() {
        super("No admin config found");
    }
}
