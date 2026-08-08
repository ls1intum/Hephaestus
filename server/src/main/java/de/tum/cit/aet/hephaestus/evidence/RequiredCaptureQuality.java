package de.tum.cit.aet.hephaestus.evidence;

/**
 * What a practice is asking for when it requires this source — a fact about the source, not about the
 * practice that names it.
 *
 * <p>Stated once per source rather than once per practice that names it. Every shipped practice agrees
 * on the answer for a given source, and {@code EvidencePolicyRedundancyTest} measures that uniformity
 * rather than assuming it — which is what makes one axis here a simplification and not a loss of
 * expressiveness. If a practice ever genuinely needs a stricter capture than its neighbours, that is a
 * per-practice axis being rediscovered, and it must be reintroduced deliberately.
 *
 * <p>One ordered enum rather than two independent booleans: the three values are a ladder, and the
 * fourth cell of the cross-product — "non-empty but possibly partial" — asserts something no source can
 * support. A partial capture that happens to contain something cannot rule out that what it omitted was
 * the part that mattered.
 */
public enum RequiredCaptureQuality {
    /** The capture must have succeeded. Whatever it holds, including nothing, is reviewable. */
    ANY_CAPTURE,

    /**
     * The capture must cover the whole selected scope. A partial one cannot support a judgement about
     * the thing as a whole, and the model cannot see that it is reading a fragment.
     */
    COMPLETE,

    /**
     * Complete, and containing something. An empty capture is a legitimate outcome that nonetheless
     * cannot ground a judgement — a diff with no changes in it makes the model fall back to the title
     * and description and grade those instead.
     */
    COMPLETE_AND_NON_EMPTY;

    public boolean demandsComplete() {
        return this != ANY_CAPTURE;
    }

    public boolean demandsContent() {
        return this == COMPLETE_AND_NON_EMPTY;
    }
}
