package de.tum.cit.aet.hephaestus.practices;

/**
 * How a practice relates to one evidence source. <em>How strictly</em> that source must be captured is
 * not said here; it belongs to the source contract.
 *
 * <p>A relation rather than two parallel lists of sources, so a source moves between stances by editing
 * one word and the readiness report can name the stance that produced a refusal.
 */
public enum EvidenceStance {
    /**
     * The claim reads this source. If it could not be captured to the quality its contract demands, the
     * review is refused rather than run — a refusal is telemetry, whereas a review run blind produces a
     * verdict about a developer from evidence we never had.
     */
    REQUIRED,

    /**
     * The claim reads this source and asserts something is <em>not</em> in it. Chosen over
     * {@link #REQUIRED} whenever a verdict rests on an absence, since an absence is the one claim a
     * fragment cannot support: a partial capture of the review threads is equally consistent with
     * "nobody resolved this one" and "the resolution was in the part we did not fetch".
     *
     * <p>Not redundant over a source whose contract already demands a complete capture: the contract
     * says what the capture is good for in general, this says that relaxing it must fail at the
     * practices that depended on it rather than quietly turning their findings into guesses.
     */
    EXHAUSTIVE,

    /** Used when present, noted when absent, never a reason to refuse. */
    CONTEXTUAL;

    /** Whether an absent or inadequate capture of a source held this way refuses the review. */
    public boolean refuses() {
        return this != CONTEXTUAL;
    }

    /** Whether a partial capture refuses the review even where the source contract would accept one. */
    public boolean demandsCompleteCapture() {
        return this == EXHAUSTIVE;
    }
}
