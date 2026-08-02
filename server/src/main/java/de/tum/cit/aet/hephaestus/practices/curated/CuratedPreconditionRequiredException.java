package de.tum.cit.aet.hephaestus.practices.curated;

public class CuratedPreconditionRequiredException extends RuntimeException {

    public CuratedPreconditionRequiredException() {
        super("If-Match must contain the current catalog entry ETag");
    }
}
