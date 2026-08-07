package de.tum.cit.aet.hephaestus.practices;

/**
 * How a practice relates to one evidence source — the whole of what an author still says about
 * evidence, now that <em>how strictly</em> a source is demanded belongs to the source contract.
 *
 * <p>The two arms replace two parallel lists that differed only in which list a source was in. Naming
 * the relation instead of the list means a source can be moved between stances by editing one word,
 * and means the readiness report can say which stance produced a refusal.
 *
 * <p>A third stance, {@code EXHAUSTIVE} — the licence for a claim that something is <em>absent</em>,
 * and the only one that would consult signal coverage — is not here yet on purpose. It has no producer:
 * deciding which shipped practices assert absence is a reading of each practice's criteria, and the
 * stance only becomes expressible once evidence attaches per binding, because whether a review may
 * claim absence depends on what occasioned it. Adding an arm nothing selects is the shape of the dead
 * vocabulary this work has spent several slices deleting.
 */
public enum EvidenceStance {
    /**
     * The claim reads this source. If it could not be captured to the quality its contract demands, the
     * review is refused rather than run — a refusal is telemetry, whereas a review run blind produces a
     * verdict about a developer from evidence we never had.
     */
    REQUIRED,

    /** Used when present, noted when absent, never a reason to refuse. */
    CONTEXTUAL;

    /** Whether an absent or inadequate capture of a source held this way refuses the review. */
    public boolean refuses() {
        return this == REQUIRED;
    }
}
