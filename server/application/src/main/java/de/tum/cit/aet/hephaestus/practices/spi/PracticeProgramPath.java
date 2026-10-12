package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * How a practice program reached its candidate, derived from the calls it made: never sent by the runner.
 *
 * @see #of(int, int)
 */
public enum PracticeProgramPath {
    /** Program code alone: no review session and no direct model call. */
    CODE,
    /** Direct model calls only. */
    MODELS,
    /** The review session only. */
    REVIEW,
    /** Both the review session and direct model calls. */
    MIXED;

    /**
     * @param reviewCalls review sessions the program opened
     * @param modelCalls direct model calls of every slot
     */
    public static PracticeProgramPath of(int reviewCalls, int modelCalls) {
        if (reviewCalls > 0) return modelCalls > 0 ? MIXED : REVIEW;
        return modelCalls > 0 ? MODELS : CODE;
    }
}
