package de.tum.cit.aet.hephaestus.practices.catalog;

import java.io.Serial;

public class StaleCatalogEntryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StaleCatalogEntryException(String subject) {
        super(subject + " changed after you loaded it. Reload and try again.");
    }
}
