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
 * <p><b>Every value here is a measurement about the world.</b> "We could not look" is a fact about the
 * instrument, not about the developer, and it is deliberately not representable: a source that was
 * missing, errored, or governance-blocked produces a readiness refusal recorded on the review
 * ({@code AutomatedReviewReadinessDecision}) and no {@link Observation} at all. That boundary is what
 * lets the whole table be read as behaviour.
 */
public enum Presence {
    /** The target signal is present in the developer's changed work. */
    PRESENT,
    /** The target signal is absent where it was expected in the developer's changed work. */
    ABSENT,
    /** The practice does not apply to the changed work (e.g., no network calls → error-state-handling is irrelevant). */
    NOT_APPLICABLE,
    /**
     * The evidence the practice needs was present and was read, and it does not settle the question either
     * way.
     *
     * <p>Distinct from {@link #NOT_APPLICABLE} on purpose, and the distinction is a research-integrity
     * line rather than a nicety. NOT_APPLICABLE is a claim about the work — this practice has no subject
     * here — and a detector that says it when it merely could not decide has laundered its own uncertainty
     * into a statement about the developer, which then enters the behaviour series as "nothing to see".
     * INDETERMINATE says the honest thing: we looked and could not tell.
     *
     * <p>This value is also what makes an {@code exhaustive} evidence stance safe to author. A practice
     * that asserts <em>absence</em> ("merged with no decision recorded") may only warrant ABSENT when the
     * corpus it searched was complete; when it was not, the answer is INDETERMINATE, never a quiet
     * NOT_APPLICABLE.
     *
     * <p>Carries no valence, so {@link Assessment} is null — same coupling as NOT_APPLICABLE.
     */
    INDETERMINATE;

    /**
     * Whether an observation with this presence carries a good/bad direction — i.e. whether
     * {@link Assessment} is required rather than forbidden.
     *
     * <p>One method rather than a repeated {@code != NOT_APPLICABLE} test, because that test was written
     * out in six places and every one of them would have silently accepted an INDETERMINATE row with an
     * assessment attached. The DB CHECK {@code chk_observation_presence_assessment} is the same predicate.
     */
    public boolean carriesValence() {
        return this == PRESENT || this == ABSENT;
    }
}
