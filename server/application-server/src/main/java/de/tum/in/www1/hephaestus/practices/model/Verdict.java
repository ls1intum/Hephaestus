package de.tum.in.www1.hephaestus.practices.model;

/**
 * Verdict for a practice finding — whether the contributor followed or violated the practice.
 *
 * <p>Orthogonal to {@link Severity}: verdict captures the <em>direction</em> (positive vs negative),
 * while severity captures the <em>impact</em> (critical vs informational).
 */
public enum Verdict {
    /** Contributor followed the practice correctly. */
    POSITIVE,
    /** Contributor violated or missed the practice. */
    NEGATIVE,
    /** Practice does not apply to this target (e.g., commit-message quality on a PR with no commits). */
    NOT_APPLICABLE,
    /** Borderline case requiring human judgment — AI confidence is too low for a definitive verdict. */
    NEEDS_REVIEW,
}
