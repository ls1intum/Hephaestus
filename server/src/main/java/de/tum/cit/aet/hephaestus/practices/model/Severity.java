package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Severity level for a practice observation — the coaching band of a {@code BAD} observation.
 *
 * <p>{@link Presence} captures whether the signal was PRESENT/ABSENT and {@link Assessment} its GOOD/BAD
 * valence; severity captures <em>how important</em> a BAD observation is. It is set only when
 * {@code assessment = BAD} (null on a GOOD strength or a NOT_APPLICABLE observation): an {@code ABSENT, BAD}
 * gap can be MINOR (style nit) or CRITICAL (security vulnerability).
 */
public enum Severity {
    CRITICAL,
    MAJOR,
    MINOR,
    INFO,
}
