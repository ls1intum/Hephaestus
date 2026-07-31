package de.tum.cit.aet.hephaestus.practices.curated;

public class CuratedPracticePreconditionRequiredException extends RuntimeException {

    public CuratedPracticePreconditionRequiredException() {
        super("If-Match must contain the current curated-practice ETag");
    }
}
