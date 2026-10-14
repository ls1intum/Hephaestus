package de.tum.cit.aet.hephaestus.practices.catalog;

import java.io.Serial;

public class CatalogConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogConflictException(String message) {
        super(message);
    }

    public CatalogConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
