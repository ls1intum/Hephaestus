package de.tum.in.www1.hephaestus.practices.model;

/**
 * Verdict for a practice finding — assessment of whether the contributor
 * followed or violated the practice, or whether the practice is irrelevant.
 *
 * <p>Orthogonal to {@link Severity}: verdict captures the <em>direction</em> (positive vs negative),
 * while severity captures the <em>impact</em> (critical vs informational).
 */
public enum Verdict {
    /** Contributor demonstrably followed the practice in their changed code. */
    POSITIVE,
    /** Contributor violated or missed the practice in their changed code. */
    NEGATIVE,
    /** The practice does not apply to the changed code (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
}
