package de.tum.cit.aet.hephaestus.practices.dto;

/**
 * How a workspace's copy stands against the catalog entry it came from. The same four answers apply
 * to a practice and to an area, so one badge renders either.
 */
public enum CatalogLink {
    /** The workspace wrote this one itself; the catalog has no claim on it. */
    LOCAL,
    /** Still exactly what the instance offers. */
    IN_SYNC,
    /** The workspace changed it, so the catalog no longer describes what runs here. */
    LOCALLY_EDITED,
    /** Untouched here, but the instance has moved the catalog entry on since this copy was made. */
    UPDATE_AVAILABLE,
}
