package de.tum.cit.aet.hephaestus.practices;

public interface CatalogDefinition {
    String digest(String slug);

    String provenanceFingerprint(String slug);
}
