package de.tum.cit.aet.hephaestus.practices;

/**
 * Whether a practice can be reviewed from a source that captured successfully but contains nothing.
 *
 * <p>An empty capture is a legitimate outcome: a pull request may genuinely carry no review
 * comments, so emptiness is not a collection failure. It can nonetheless make a practice
 * unreviewable. A practice concerning how a change is written cannot be assessed from a diff
 * containing no changes, and a model given one falls back to the title and description. Practices
 * that require content declare {@link #NON_EMPTY}.
 */
public enum EvidenceContentRequirement {
    /** The source must contain something; a valid but empty capture cannot support a review. */
    NON_EMPTY,
    /** An empty capture is itself a reviewable result. */
    NO_REQUIREMENT,
}
