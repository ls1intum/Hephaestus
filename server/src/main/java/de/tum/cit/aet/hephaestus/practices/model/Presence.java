package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whether the target signal a practice looks for was seen in the developer's work, was expected but
 * absent, does not apply at all, or could not be settled from evidence that was in fact present
 * (ADR 0022).
 *
 * <p><b>Measurement, not evaluation.</b> Presence states only what the detector <em>saw</em>; it does
 * NOT encode "good" or "bad". The good/bad direction is a second, orthogonal axis carried by
 * {@link Assessment} and resolved per observation by the detector reading the practice criteria and
 * {@code what_good_looks_like}. The 2×2 of {@code (presence, assessment)} reads directly: a present
 * good behaviour is a strength, a present bad behaviour is a problem (commission), and an absent good
 * behaviour is a gap (omission).
 *
 * <p>Orthogonal to {@link Severity}: presence captures whether the signal was seen, severity captures
 * impact (critical vs informational) and is meaningful only for a {@link Assessment#BAD} observation.
 *
 * <p><b>Every value here is a measurement about the world</b>, which is what lets the whole table be
 * read as behaviour. "We could not look" is a fact about the instrument and is deliberately not
 * representable: a source that was missing, errored, or governance-blocked produces an
 * {@code AutomatedReviewReadinessDecision} on the review and no {@link Observation} at all.
 */
public enum Presence {
    /** The target signal is present in the developer's changed work. */
    PRESENT,
    /** The target signal is absent where it was expected in the developer's changed work. */
    ABSENT,
    /** The practice does not apply to the changed work (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
    /**
     * The evidence the practice needs was present and was read, and does not settle the question either
     * way. Carries no valence, so {@link Assessment} is null — the same coupling as NOT_APPLICABLE.
     *
     * <p>Chosen over {@link #NOT_APPLICABLE} whenever the detector could not decide. NOT_APPLICABLE is a
     * claim about the work — this practice has no subject here — so saying it under uncertainty enters
     * the behaviour series as "nothing to see". It is also the only correct answer for a practice with an
     * {@code exhaustive} stance whose corpus turned out incomplete: such a practice may warrant ABSENT
     * only when the corpus it searched was whole.
     */
    INDETERMINATE;

    /**
     * Whether an observation with this presence carries a good/bad direction — i.e. whether
     * {@link Assessment} is required rather than forbidden.
     *
     * <p>The same predicate as the DB CHECK {@code chk_observation_presence_assessment}. Asked here
     * rather than by an open-coded {@code != NOT_APPLICABLE}, which silently accepts an INDETERMINATE
     * row with an assessment attached.
     */
    public boolean carriesValence() {
        return this == PRESENT || this == ABSENT;
    }
}
