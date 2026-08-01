package de.tum.cit.aet.hephaestus.practices.curated;

/** A catalog write cannot be applied as asked — a taken slug, or an entry with nothing to restore. */
public class CuratedCatalogConflictException extends RuntimeException {

    public CuratedCatalogConflictException(String message) {
        super(message);
    }

    public CuratedCatalogConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
