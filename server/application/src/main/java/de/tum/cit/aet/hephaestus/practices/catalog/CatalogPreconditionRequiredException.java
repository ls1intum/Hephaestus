package de.tum.cit.aet.hephaestus.practices.catalog;

import java.io.Serial;

public class CatalogPreconditionRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogPreconditionRequiredException() {
        super("The If-Match header must contain the current ETag of the catalog entry.");
    }
}
