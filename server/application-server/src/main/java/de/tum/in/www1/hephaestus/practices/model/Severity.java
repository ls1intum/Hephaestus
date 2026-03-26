package de.tum.in.www1.hephaestus.practices.model;

/**
 * Severity level for a practice finding — orthogonal to {@link Verdict}.
 *
 * <p>Verdict captures <em>what happened</em> (positive/negative); severity captures
 * <em>how important</em> the finding is. A POSITIVE finding can be INFO ("good job")
 * or MAJOR ("correctly handled critical security pattern"). A NEGATIVE finding can be
 * MINOR (style nit) or CRITICAL (security vulnerability).
 */
public enum Severity {
    CRITICAL,
    MAJOR,
    MINOR,
    INFO,
}
