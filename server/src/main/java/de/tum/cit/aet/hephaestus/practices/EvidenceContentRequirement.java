package de.tum.cit.aet.hephaestus.practices;

/**
 * Whether a practice can be judged from a source that captured successfully but holds nothing.
 *
 * <p>An empty capture is a legitimate result — a pull request really can carry no comments — so
 * emptiness is not a collection failure. It is, however, sometimes fatal to the judgement: a
 * practice about how a change is written cannot be assessed from a diff with no changes in it, and
 * a model handed one will fall back to the title and description. Practices that need substance say
 * so with {@link #NON_EMPTY}.
 */
public enum EvidenceContentRequirement {
    /** The source must hold something; a valid but empty capture is not enough to judge. */
    NON_EMPTY,
    /** An empty capture is a reviewable answer in itself. */
    NO_REQUIREMENT,
}
