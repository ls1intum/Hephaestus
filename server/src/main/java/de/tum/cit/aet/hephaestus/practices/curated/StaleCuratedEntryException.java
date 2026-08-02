package de.tum.cit.aet.hephaestus.practices.curated;

public class StaleCuratedEntryException extends RuntimeException {

    public StaleCuratedEntryException(String subject) {
        super(subject + " changed since it was loaded.");
    }
}
