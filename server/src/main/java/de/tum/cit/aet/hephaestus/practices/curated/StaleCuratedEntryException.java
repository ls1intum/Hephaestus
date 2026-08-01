package de.tum.cit.aet.hephaestus.practices.curated;

/** A catalog entry moved on between the read the client edited and the write it sent back. */
public class StaleCuratedEntryException extends RuntimeException {

    public StaleCuratedEntryException(String entry, String slug) {
        super(describe(entry) + " '" + slug + "' changed since it was loaded.");
    }

    private static String describe(String entry) {
        return entry.equals("CuratedPracticeArea") ? "Curated area" : "Curated practice";
    }
}
