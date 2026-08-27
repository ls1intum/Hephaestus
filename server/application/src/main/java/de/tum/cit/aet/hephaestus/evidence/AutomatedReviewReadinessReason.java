package de.tum.cit.aet.hephaestus.evidence;

public enum AutomatedReviewReadinessReason {
    NO_AUTOMATED_REVIEW,
    DECLARED_EVIDENCE_INSUFFICIENT,
    /**
     * The evidence was there and was read, and the thing this practice judges was not in the work — the
     * one reason here that is about the work rather than about our instrument. Kept apart from the
     * others for exactly that reason: "we could not look" and "we looked, and there was nothing of this
     * kind to look at" are different answers to a developer asking why a practice said nothing, and a
     * surface that renders them the same sends them to the wrong fix.
     */
    SUBJECT_NOT_IN_THE_WORK,
}
