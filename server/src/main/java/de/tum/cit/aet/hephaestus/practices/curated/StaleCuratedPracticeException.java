package de.tum.cit.aet.hephaestus.practices.curated;

public class StaleCuratedPracticeException extends RuntimeException {

    public StaleCuratedPracticeException(String slug) {
        super("Curated practice '" + slug + "' changed since it was loaded.");
    }
}
