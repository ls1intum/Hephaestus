package de.tum.cit.aet.hephaestus.practices;

/**
 * How a practice relates to one evidence source — the whole of what an author still says about
 * evidence, now that <em>how strictly</em> a source is demanded belongs to the source contract.
 *
 * <p>The two arms replace two parallel lists that differed only in which list a source was in. Naming
 * the relation instead of the list means a source can be moved between stances by editing one word,
 * and means the readiness report can say which stance produced a refusal.
 *
 * <p>The third stance, {@code EXHAUSTIVE}, was held back until evidence attached per binding, because
 * whether a review may claim an absence depends on what occasioned it: the same practice reviewed when
 * a change is opened is only reading what is in front of it, while the review that runs when the change
 * merges is the one that says nobody ever resolved the thread.
 */
public enum EvidenceStance {
    /**
     * The claim reads this source. If it could not be captured to the quality its contract demands, the
     * review is refused rather than run — a refusal is telemetry, whereas a review run blind produces a
     * verdict about a developer from evidence we never had.
     */
    REQUIRED,

    /**
     * The claim reads this source and asserts something is <em>not</em> in it.
     *
     * <p>Required, and additionally not satisfiable by a partial capture whatever the source contract's
     * floor happens to be. An absence is the one claim a fragment cannot support: a partial capture of
     * the review threads is consistent both with "nobody resolved this one" and with "the resolution was
     * in the part we did not fetch", and a review that cannot tell those apart still tells a developer
     * they merged past an unresolved thread.
     *
     * <p>Declaring it over a source whose contract already demands {@code COMPLETE} is not redundant.
     * The contract states what the capture is good for in general; this states that <em>this</em>
     * practice's verdict rests on the capture being whole, so relaxing the contract later fails at the
     * practice that depended on it rather than silently converting its findings into guesses.
     */
    EXHAUSTIVE,

    /** Used when present, noted when absent, never a reason to refuse. */
    CONTEXTUAL;

    /** Whether an absent or inadequate capture of a source held this way refuses the review. */
    public boolean refuses() {
        return this != CONTEXTUAL;
    }

    /**
     * Whether a partial capture refuses the review even where the source contract would accept one.
     *
     * <p>The only thing this stance adds to {@link #REQUIRED}, and the reason it is a stance rather than
     * a per-practice capture quality: what is being said is about the claim, not about the source.
     */
    public boolean demandsCompleteCapture() {
        return this == EXHAUSTIVE;
    }
}
