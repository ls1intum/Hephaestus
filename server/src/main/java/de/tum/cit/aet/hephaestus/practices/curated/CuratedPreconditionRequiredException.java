package de.tum.cit.aet.hephaestus.practices.curated;

/** A catalog write arrived without the {@code If-Match} that proves which revision it edits. */
public class CuratedPreconditionRequiredException extends RuntimeException {

    public CuratedPreconditionRequiredException() {
        super("If-Match must contain the current catalog entry ETag");
    }
}
