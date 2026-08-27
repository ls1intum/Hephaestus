package de.tum.cit.aet.hephaestus.practices.curated.adoption;

public class CatalogAdoptionPreconditionRequiredException extends RuntimeException {

    public CatalogAdoptionPreconditionRequiredException() {
        super("If-Match must contain the adoption preview ETag.");
    }
}
