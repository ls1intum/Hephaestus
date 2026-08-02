package de.tum.cit.aet.hephaestus.practices.curated;

public class CuratedCatalogConflictException extends RuntimeException {

    public CuratedCatalogConflictException(String message) {
        super(message);
    }

    public CuratedCatalogConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
