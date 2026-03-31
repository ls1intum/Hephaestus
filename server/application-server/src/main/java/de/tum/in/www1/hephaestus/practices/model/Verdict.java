package de.tum.in.www1.hephaestus.practices.model;

/**
 * Verdict for a practice finding — binary assessment of whether the contributor
 * followed or violated the practice.
 *
 * <p>Orthogonal to {@link Severity}: verdict captures the <em>direction</em> (positive vs negative),
 * while severity captures the <em>impact</em> (critical vs informational).
 *
 * <p>Irrelevant practices produce no finding at all — there is no "not applicable" verdict.
 * Uncertainty is expressed via the confidence score, not a separate verdict value.
 */
public enum Verdict {
    /** Contributor demonstrably followed the practice in their changed code. */
    POSITIVE,
    /** Contributor violated or missed the practice in their changed code. */
    NEGATIVE,
}
