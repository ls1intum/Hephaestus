package de.tum.cit.aet.hephaestus.practices;

/**
 * How a practice relates to one evidence source. <em>How strictly</em> that source must be captured is
 * not said here; it belongs to the source contract.
 */
public enum EvidenceStance {
    /**
     * The claim reads this source. If it could not be captured to the quality its contract demands, the
     * review is refused rather than run — a refusal is telemetry, whereas a review run blind produces a
     * verdict from evidence we never had.
     */
    REQUIRED,

    /**
     * The claim reads this source and asserts something is <em>not</em> in it. Chosen over
     * {@link #REQUIRED} whenever a verdict rests on an absence: a partial capture is equally consistent
     * with "nobody resolved this one" and "the resolution was in the part we did not fetch".
     */
    EXHAUSTIVE,

    /** Used when present, noted when absent, never a reason to refuse. */
    CONTEXTUAL;

    public boolean refuses() {
        return this != CONTEXTUAL;
    }

    /** Whether a partial capture refuses the review even where the source contract would accept one. */
    public boolean demandsCompleteCapture() {
        return this == EXHAUSTIVE;
    }
}
