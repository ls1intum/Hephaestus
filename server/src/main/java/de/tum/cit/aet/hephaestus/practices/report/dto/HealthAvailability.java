package de.tum.cit.aet.hephaestus.practices.report.dto;

/**
 * Availability of the k-anonymised counts on an {@link AreaHealthDTO} card. A mutually-exclusive 3-state
 * modeled as one discriminant rather than two booleans (which would have permitted an invalid 4th state).
 */
public enum HealthAvailability {
    /** Counts are exposed: the k-anonymity threshold is met. */
    AVAILABLE,
    /**
     * Suppressed for k-anonymity: 1..4 active developers in the window (or a non-zero status bucket smaller
     * than the threshold).
     */
    SUPPRESSED,
    /** No developer had activity on this area in the window — not a privacy risk, nobody to re-identify. */
    NO_DATA,
}
