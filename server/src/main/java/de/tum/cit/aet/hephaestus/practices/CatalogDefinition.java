package de.tum.cit.aet.hephaestus.practices;

/**
 * What every catalog definition can answer about itself: is this the same thing, and is this the same
 * thing as far as a detection run is concerned. Implemented by {@link PracticeDefinition} and
 * {@link AreaDefinition}, which is what lets one catalog entry type serve both.
 */
public interface CatalogDefinition {
    /** Identity of the whole definition, copy included. */
    String digest(String slug);

    /**
     * Identity of the part a detection run reads. Two definitions with the same fingerprint detect
     * identically, so a difference outside it is a difference only people notice.
     */
    String detectionFingerprint(String slug);
}
