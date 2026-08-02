package de.tum.cit.aet.hephaestus.practices;

public interface CatalogDefinition {
    String digest(String slug);

    /** Identity of the fields that can change detection results. */
    String detectionFingerprint(String slug);
}
