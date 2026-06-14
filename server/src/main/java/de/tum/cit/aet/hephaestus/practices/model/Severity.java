package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Severity level for a practice finding — orthogonal to {@link Verdict}.
 *
 * <p>Verdict captures <em>presence</em> (observed/not observed); severity captures
 * <em>how important</em> the finding is. An OBSERVED finding can be INFO ("good job")
 * or MAJOR ("correctly handled critical security pattern"). A NOT_OBSERVED finding can be
 * MINOR (style nit) or CRITICAL (security vulnerability).
 */
public enum Severity {
    CRITICAL,
    MAJOR,
    MINOR,
    INFO,
}
